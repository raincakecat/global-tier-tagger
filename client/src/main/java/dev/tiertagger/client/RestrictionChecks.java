package dev.tiertagger.client;

import dev.tiertagger.core.*;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** UI state and commands only; never sends a server chat packet. */
public final class RestrictionChecks {
 static MinecraftIdentityResolver identities=new MinecraftIdentityResolver();
 static RestrictionClient api=new RestrictionClient();
 public static final class Entry {
  public final String name; public final UUID expected; public RestrictionClient.Result result; public String message="Queued"; public boolean complete;
  Entry(String name,UUID expected){this.name=name;this.expected=expected;}
 }
 public static final List<Entry> entries=new ArrayList<>();
 private static final List<CompletableFuture<?>> scanJobs=new ArrayList<>();
 private static final Map<UUID,RestrictionClient.Result> details=new HashMap<>();
 private static int epoch,scanEpoch,nextScanIndex;private static boolean scanReady;public static boolean scanning;public static String notice="Enter a username or check online players";
 private RestrictionChecks() {}
 public static MutableComponent link(String label,String url){return Component.literal(label).withStyle(style->style.withColor(0xe7a0a0).withUnderlined(true).withClickEvent(new ClickEvent.OpenUrl(java.net.URI.create(url))));}
 public static String error(Throwable thrown){Throwable e=thrown;while(e instanceof CompletionException&&e.getCause()!=null)e=e.getCause();if(e instanceof CancellationException)return "Cancelled";if(e instanceof RestrictionClient.Failure f)return f.getMessage()+(f.requestId.isBlank()?"":" (request "+f.requestId+")");return e instanceof IllegalArgumentException?e.getMessage():"Lookup unavailable; try again";}
 public static void command(String name,Consumer<Component> chat){
  int current=epoch;Object connection=Minecraft.getInstance().getConnection();chat.accept(Component.literal("[Restrictions] Resolving "+name+"..."));
  identities.resolve(name).thenCompose(identity->api.lookup(identity.uuid(),1)).whenComplete((result,failure)->Minecraft.getInstance().execute(()->{
   if(current!=epoch||connection!=Minecraft.getInstance().getConnection())return;
   if(failure!=null){chat.accept(Component.literal("[Restrictions] "+error(failure)).withColor(0xff9292));return;}
   details.put(result.uuid(),result);summary(result,chat);
  }));
 }
 static void summary(RestrictionClient.Result result,Consumer<Component> chat){
  String name=result.username()==null?result.uuid().toString():result.username();
  chat.accept(Component.literal("[Restrictions] "+name+" | "+result.activeCount()+" active | "+result.pastCount()+" past"));
  if(result.activeCount()==0)chat.accept(Component.literal("No active records returned"));
  for(var record:result.active().stream().limit(5).toList())chat.accept(Component.literal(record.community()+": "+record.reason()+" | "+review(record.verification())));
  if(result.sample())chat.accept(Component.literal("SAMPLE DATA"));
  if(result.activeTruncated()||result.pastTruncated()||result.hasMorePast()||result.beyondPageLimit())chat.accept(Component.literal("Results/history are incomplete; open full details."));
  if(!result.notice().isBlank())chat.accept(Component.literal(result.notice()));
  chat.accept(Component.literal("[Open full details]").withStyle(s->s.withColor(0xefaaaa).withUnderlined(true).withClickEvent(new ClickEvent.RunCommand("/tiertagger restrictions "+result.uuid()))));
  chat.accept(link("RestrictTiers profile",result.profileUrl()).append(Component.literal(" | ")).append(link("Request correction","https://restricttiers.raincakecat.com/contact")));
 }
 public static String review(String flag){return flag.equals("needs_review")?"Needs verification":"No review flag recorded";}
 public static void openDetails(String raw){try{UUID id=MinecraftIdentityResolver.uuid(raw);RestrictionClient.Result result=details.get(id);if(result!=null)Minecraft.getInstance().setScreenAndShow(new RestrictionScreen(Platform.screen(Minecraft.getInstance()),result));}catch(IllegalArgumentException ignored){}}
 public static void check(String username){cancelScan();entries.clear();Entry entry=new Entry(username,null);entries.add(entry);int current=epoch;notice="Resolving premium account...";lookup(entry,current,-1);}
 public static void scan(){var mc=Minecraft.getInstance();if(mc.getConnection()==null){cancelScan();entries.clear();notice="Join a server to check online players";return;}List<Entry> snapshot=new ArrayList<>();Set<UUID> seen=new HashSet<>();for(var player:mc.getConnection().getOnlinePlayers())if(seen.add(player.getProfile().id()))snapshot.add(new Entry(player.getProfile().name(),player.getProfile().id()));scanSnapshot(snapshot);}
 static void scanSnapshot(List<Entry> snapshot){cancelScan();entries.clear();Set<UUID> seen=new HashSet<>();for(Entry entry:snapshot)if(entry.expected==null||seen.add(entry.expected))entries.add(entry);scanning=!entries.isEmpty();notice=entries.isEmpty()?"No online players":"Checking snapshot of "+entries.size()+" players";nextScanIndex=0;advanceScan(epoch,scanEpoch);}

 private static void advanceScan(int current,int scan){if(!scanning||current!=epoch||scan!=scanEpoch)return;if(nextScanIndex>=entries.size()){scanning=false;scanJobs.clear();notice="Online check finished";return;}Entry entry=entries.get(nextScanIndex++);entry.message="Resolving premium account";notice="Checking "+nextScanIndex+" / "+entries.size()+": "+entry.name;lookup(entry,current,scan);}
 private static void lookup(Entry entry,int current,int scan){
  var identityJob=identities.resolve(entry.name);if(scan>=0)scanJobs.add(identityJob);identityJob.whenComplete((identity,failure)->Minecraft.getInstance().execute(()->{
   if(current!=epoch||(scan>=0&&scan!=scanEpoch))return;
   if(failure!=null){finish(entry,null,failure);return;}
   if(entry.expected!=null&&!entry.expected.equals(identity.uuid())){finish(entry,null,new IllegalArgumentException("Online UUID does not match premium account"));return;}
   entry.message="Checking restrictions";var job=scan>=0?api.lookupScan(identity.uuid()):api.lookup(identity.uuid(),1);if(scan>=0)scanJobs.add(job);
   job.whenComplete((result,error)->Minecraft.getInstance().execute(()->{if(current==epoch&&(scan<0||scan==scanEpoch))finish(entry,result,error);}));
  }));
 }
 private static void finish(Entry entry,RestrictionClient.Result result,Throwable failure){entry.complete=true;entry.result=result;entry.message=failure==null?(result.activeCount()+" active | "+result.pastCount()+" past"):error(failure);if(result!=null)details.put(result.uuid(),result);if(scanning&&entries.contains(entry)){scanJobs.removeIf(CompletableFuture::isDone);scanReady=true;}}
 public static void tick(){if(scanning&&scanReady){scanReady=false;advanceScan(epoch,scanEpoch);}}
 public static int completed(){return (int)entries.stream().filter(e->e.complete).count();}
 public static void cancelScan(){scanEpoch++;scanReady=false;for(var job:scanJobs)job.cancel(false);scanJobs.clear();if(scanning){scanning=false;notice="Online check cancelled";for(Entry e:entries)if(!e.complete){e.message="Cancelled";e.complete=true;}}}
 public static void disconnect(){epoch++;cancelScan();api.cancelPending();entries.clear();details.clear();notice="Disconnected";}
 public static void close(){disconnect();api.close();identities.close();}
}
