package dev.tiertagger.core;
import java.net.*;import java.net.http.*;import java.io.*;import java.nio.charset.StandardCharsets;import java.time.Duration;import java.util.*;import java.util.concurrent.*;
/** Bounded per-provider worker, request spacing, TTL caches and shared snapshots. */
public final class AdditionalProvider implements RankingProvider {
 private final Map<UUID,String> names=new ConcurrentHashMap<>();
 public void identify(UUID id,String name){if(name!=null&&name.matches("[A-Za-z0-9_]{1,16}"))names.put(id,name);}
 private record Entry(Map<String,String> ranks,long expires){}
 private final String id;private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();private final Map<UUID,Entry> cache=new ConcurrentHashMap<>();private final Set<UUID> pending=ConcurrentHashMap.newKeySet();private volatile Map<UUID,Map<String,String>> snapshot=Map.of();private volatile long snapshotExpires,retryAt;private volatile String state="Not checked";private volatile int generation;private final ExecutorService worker=new ThreadPoolExecutor(1,1,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(128),r->{Thread t=new Thread(r,"tier-provider");t.setDaemon(true);return t;});private boolean snapshotPending;
 private final URI endpointOverride;private final java.util.function.LongSupplier now;
 public AdditionalProvider(String id){this(id,null,System::currentTimeMillis);}
 AdditionalProvider(String id,URI endpoint,java.util.function.LongSupplier clock){this.id=id;endpointOverride=endpoint;now=clock;}
 private volatile long lastSuccess;
 private volatile UUID failedPlayer;
 public void requestRetry(UUID preferred){if(now.getAsLong()>=retryAtMillis())request(failedPlayer==null?preferred:failedPlayer);}
 public long retryAtMillis(){return retryAt;}
 public long lastSuccessMillis(){return lastSuccess;}
 public String id(){return id;}public String status(){return state;}
 public boolean resolved(UUID uuid){if(id.equals("nova")||id.equals("crack"))return snapshotExpires>now.getAsLong();var e=cache.get(uuid);return e!=null&&e.expires>now.getAsLong();}
 public Optional<String> tier(UUID uuid,String mode){if(id.equals("nova")||id.equals("crack"))return snapshotExpires>now.getAsLong()?Optional.ofNullable(snapshot.getOrDefault(uuid,Map.of()).get(mode)):Optional.empty();Entry e=cache.get(uuid);return e==null||e.expires<now.getAsLong()?Optional.empty():Optional.ofNullable(e.ranks.get(mode));}
 public synchronized void request(UUID uuid){if(now.getAsLong()<retryAt)return;boolean bulk=id.equals("nova")||id.equals("crack");if(bulk&&(snapshotPending||snapshotExpires>now.getAsLong()))return;Entry old=cache.get(uuid);if(!bulk&&old!=null&&old.expires>now.getAsLong())return;if(!pending.add(uuid))return;if(bulk)snapshotPending=true;int epoch=generation;
  try{worker.execute(()->{try{if(epoch!=generation||now.getAsLong()<retryAt)return;state="Checking";if(bulk){String json=get(id.equals("nova")?"https://novatiers.com/users":"https://cracktiers.me/api/players");var parsed=ProviderParsers.snapshot(id,json);if(epoch==generation){snapshot=parsed;snapshotExpires=now.getAsLong()+(id.equals("nova")?3600000:900000);}}
   else {Map<String,String> ranks=new HashMap<>();if(id.equals("nexus")){String[] prefixes={"","mace/","spearmace/","smp/"},modes={"crystal","mace","spear-mace","smp"};for(int i=0;i<prefixes.length;i++){try{String json=get("https://nexustiers.com/"+prefixes[i]+"tier/"+uuid);var parsed=ProviderParsers.parse("nexus",json,uuid);String tier=parsed.get("crystal");if(tier!=null)ranks.put(modes[i],tier);}catch(NotFound ignored){}Thread.sleep(1000);}}
    else try{ranks.putAll(ProviderParsers.parse(id,get(url(uuid)),uuid));}catch(NotFound ignored){}
    if(epoch==generation){if(cache.size()>2048)cache.clear();cache.put(uuid,new Entry(Map.copyOf(ranks),now.getAsLong()+300000));}}
   if(epoch==generation){lastSuccess=now.getAsLong();state="Connected";}Thread.sleep(1000);
  }catch(InterruptedException ex){Thread.currentThread().interrupt();}catch(Exception ex){if(epoch==generation){failedPlayer=uuid;retryAt=Math.max(retryAt,now.getAsLong()+60000);state=ProviderStatus.failure(ex).label();}}finally{synchronized(this){if(epoch==generation){pending.remove(uuid);snapshotPending=false;}}}});}catch(RejectedExecutionException e){pending.remove(uuid);snapshotPending=false;state="Queue busy";}
 }
 private String url(UUID uuid){if(endpointOverride!=null)return endpointOverride.resolve(uuid.toString()).toString();String plain=uuid.toString().replace("-","");return switch(id){case "pvptiers"->"https://pvptiers.com/api/profile/"+plain;case "pvphq"->"https://pvphq.com/api/ranked/"+uuid;case "lucnoxity"->"https://apiv2.luctierlist.com/api/players/"+uuid;case "jptiers"->"https://api.mcpvp.jp/api/v2/profile/"+uuid;case "tiermc"->"https://tiermc.com/api/v2/profile/"+uuid;case "prestige"->"https://yiaeivzhqvgnmsiufzcy.supabase.co/functions/v1/tier-api/players/"+uuid;case "mcpvp"->"https://www.mcpvp.com/tiers/search?version=beta&kit=overall&include_retired=1&q="+java.net.URLEncoder.encode(java.util.Objects.requireNonNull(names.get(uuid),"Player name not yet available"),java.nio.charset.StandardCharsets.UTF_8);default->throw new IllegalArgumentException();};}
 private static final class NotFound extends IOException{}
 private String get(String url)throws Exception {if(now.getAsLong()<retryAt)throw new IOException("Backoff");var response=http.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(12)).header("Accept","application/json").header("User-Agent","GlobalTierTagger/0.5.0").GET().build(),HttpResponse.BodyHandlers.ofInputStream());try(var input=response.body()){if(response.statusCode()==404)throw new NotFound();if(response.statusCode()!=200){if(response.statusCode()==429)retryAt=now.getAsLong()+RetryAfter.seconds(response.headers().firstValue("Retry-After").orElse("60"),60)*1000;throw new IOException("HTTP "+response.statusCode());}byte[] data=input.readNBytes(8*1024*1024+1);if(data.length>8*1024*1024)throw new IOException("Response too large");return new String(data,StandardCharsets.UTF_8);}}
 public synchronized void clear(){lastSuccess=0;failedPlayer=null;generation++;cache.clear();snapshot=Map.of();snapshotExpires=0;pending.clear();snapshotPending=false;((ThreadPoolExecutor)worker).getQueue().clear();state="Not checked";}
 public void close(){clear();worker.shutdownNow();http.close();}
}
