package dev.tiertagger.core;
import com.google.gson.*;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

/** Bounded UUID batches, with independent outcomes and paced, cancellable requests. */
abstract class BatchedRankingProvider implements RankingProvider {
 private final String providerId;
 protected final URI endpoint;
 protected record BatchResult(Map<UUID,Map<String,String>> values,Map<UUID,PlayerLookup> errors,long refreshMillis) {}
 private static final long REFRESH_MS=60_000,MAX_STALE_MS=300_000;
 private static final Set<String> MODES=Set.of("crystal","sword","axe","smp","pot","mace","uhc","nethpot","spear-mace");
 private static final Set<String> TIERS=Set.of("HT1","LT1","HT2","LT2","HT3","LT3","HT4","LT4","HT5","LT5");
 private record Entry(Map<String,String> ranks,long updated,long refreshMillis){}
 private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();
 private final ScheduledExecutorService worker=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"lunar-tier-lookup");t.setDaemon(true);return t;});
 private final LinkedHashMap<UUID,Entry> cache=new LinkedHashMap<>(16,.75f,true);
 private final LinkedHashMap<UUID,PlayerLookup> failures=new LinkedHashMap<>();
 private final LinkedHashSet<UUID> pending=new LinkedHashSet<>();
 private final Set<UUID> inFlight=new HashSet<>();
 private String status="Not checked";
 private long retryAt,lastSuccess,nextStartAt;
 private int generation,activeGeneration;
 private boolean scheduled,closed;
 private UUID failedPlayer;
 private ScheduledFuture<?> scheduledTask;
 private CompletableFuture<?> activeHttp;
 protected BatchedRankingProvider(String id,URI endpoint){providerId=id;this.endpoint=endpoint;}
 protected synchronized void checkActive(){if(closed||generation!=activeGeneration)throw new CancellationException();}
 protected int batchSize(){return 100;}
 protected boolean eligible(UUID id){return true;}
 protected void onClear(){}
 public synchronized void requestRetry(UUID preferred){if(System.currentTimeMillis()>=retryAt)requestForeground(failedPlayer==null?preferred:failedPlayer);}
 public synchronized long retryAtMillis(){return retryAt;}
 public synchronized long lastSuccessMillis(){return lastSuccess;}
 public final String id(){return providerId;}
 public synchronized String status(){return status;}
 public synchronized boolean resolved(UUID id){var e=cache.get(id);return PlayerLookup.supported(id)&&e!=null&&System.currentTimeMillis()-e.updated<Math.max(MAX_STALE_MS,e.refreshMillis);}
 public synchronized Optional<String> tier(UUID id,String mode){var e=cache.get(id);return PlayerLookup.supported(id)&&e!=null&&System.currentTimeMillis()-e.updated<Math.max(MAX_STALE_MS,e.refreshMillis)?Optional.ofNullable(e.ranks.get(mode)):Optional.empty();}
 public synchronized PlayerLookup lookup(UUID id){
  if(!PlayerLookup.supported(id))return new PlayerLookup(PlayerLookup.Phase.SKIPPED,"Unsupported Minecraft UUID",0);
  if(resolved(id))return PlayerLookup.of(PlayerLookup.Phase.READY);
  var failure=failures.get(id);if(failure!=null)return failure;
  if(System.currentTimeMillis()<retryAt)return new PlayerLookup(PlayerLookup.Phase.RETRY_WAIT,"Provider cooldown",retryAt);
  return PlayerLookup.of(inFlight.contains(id)?PlayerLookup.Phase.FETCHING:pending.contains(id)?PlayerLookup.Phase.QUEUED:PlayerLookup.Phase.NOT_REQUESTED);
 }
 public synchronized void request(UUID id){enqueue(id,false);}
 public synchronized void requestForeground(UUID id){enqueue(id,true);}
 private void enqueue(UUID id,boolean foreground){
  if(closed||!PlayerLookup.supported(id)||!eligible(id))return;long now=System.currentTimeMillis();var old=cache.get(id);if(old!=null&&now-old.updated<old.refreshMillis)return;
  var failure=failures.get(id);if(failure!=null&&failure.retryAtMillis()>now)return;
  if(inFlight.contains(id))return;
  if(pending.size()>=4096&&!pending.contains(id)){failures.put(id,new PlayerLookup(PlayerLookup.Phase.FAILED,"Request queue busy",now+1000));trim();return;}
  failures.remove(id);
  if(foreground){var rest=new LinkedHashSet<>(pending);pending.clear();pending.add(id);pending.addAll(rest);}else pending.add(id);
  schedule();
 }
 private void schedule(){if(!closed&&!scheduled&&!pending.isEmpty()){scheduled=true;long delay=Math.max(150,Math.max(retryAt,nextStartAt)-System.currentTimeMillis());scheduledTask=worker.schedule(this::runBatch,delay,TimeUnit.MILLISECONDS);}}
 private void runBatch(){
  List<UUID> batch;int epoch;
  synchronized(this){if(closed)return;epoch=generation;activeGeneration=epoch;batch=pending.stream().limit(batchSize()).toList();pending.removeAll(batch);inFlight.addAll(batch);nextStartAt=System.currentTimeMillis()+1000;}
  try{
   if(batch.isEmpty())return;
   HttpRequest request=buildRequest(batch);
   CompletableFuture<HttpResponse<byte[]>> future;
   synchronized(this){if(epoch!=generation||closed)return;status="Checking";future=http.sendAsync(request,HttpBodies.limited(262144));activeHttp=future;}
   HttpResponse<byte[]> response;
   try{response=future.get(11,TimeUnit.SECONDS);}catch(ExecutionException e){if(e.getCause() instanceof Exception cause)throw cause;throw e;}catch(TimeoutException e){future.cancel(true);throw new HttpTimeoutException("Request timed out");}finally{synchronized(this){if(activeHttp==future)activeHttp=null;}}
   if(response.statusCode()!=200)throw new RetryException(Math.max(5,RetryAfter.seconds(response.headers().firstValue("Retry-After").orElse("5"),5))*1000,response.statusCode());
   long spacing=1000;
   try{long limit=Long.parseLong(response.headers().firstValue("X-RateLimit-Limit").orElse("60"));if(limit>0&&limit<60)spacing=Math.max(spacing,(60000+limit-1)/limit);}catch(NumberFormatException ignored){}
   synchronized(this){nextStartAt=Math.max(nextStartAt,System.currentTimeMillis()+spacing);}
   BatchResult result=parseBatch(new String(response.body(),StandardCharsets.UTF_8),batch);
   synchronized(this){if(epoch==generation&&!closed){long now=System.currentTimeMillis();for(var v:result.values().entrySet()){cache.put(v.getKey(),new Entry(v.getValue(),now,result.refreshMillis()));failures.remove(v.getKey());}failures.putAll(result.errors());lastSuccess=now;status=ProviderCatalog.name(providerId)+" connected";failedPlayer=result.errors().keySet().stream().findFirst().orElse(null);trim();}}
  }catch(Exception error){synchronized(this){if(epoch==generation&&!closed&&!(error instanceof CancellationException)){long delay=error instanceof RetryException r?r.delay:5000;retryAt=System.currentTimeMillis()+delay;failedPlayer=batch.isEmpty()?null:batch.getFirst();status=error instanceof RetryException r?ProviderStatus.http(r.code).label():ProviderStatus.failure(error).label();for(UUID id:batch)failures.put(id,new PlayerLookup(PlayerLookup.Phase.FAILED,status,retryAt));trim();}}if(error instanceof InterruptedException)Thread.currentThread().interrupt();}
  finally{synchronized(this){if(epoch==generation){inFlight.removeAll(batch);scheduled=false;schedule();}}}
 }
 protected HttpRequest buildRequest(List<UUID> batch)throws Exception {
  String body=new Gson().toJson(Map.of("uuids",batch.stream().map(BatchedRankingProvider::key).toList()));
  return HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(10)).header("Content-Type","application/json").header("Accept","application/json").header("User-Agent","GlobalTierTagger/1.0.2").POST(HttpRequest.BodyPublishers.ofString(body)).build();
 }
 protected BatchResult parseBatch(String json,List<UUID> batch)throws Exception {
  JsonObject root=JsonParser.parseString(json).getAsJsonObject();
  if(!root.has("schemaVersion")||root.get("schemaVersion").getAsInt()!=1||!root.has("cacheSeconds")||!validCacheSeconds(root)||!root.has("players"))throw new IllegalArgumentException("Invalid response");
  JsonObject players=root.getAsJsonObject("players");Set<String> expected=new HashSet<>();batch.forEach(id->expected.add(key(id)));if(!expected.containsAll(players.keySet()))throw new IllegalArgumentException("Unexpected player identity");
  Map<UUID,Map<String,String>> values=new HashMap<>();Map<UUID,PlayerLookup> errors=new HashMap<>();
  for(UUID id:batch){try{JsonObject single=root.deepCopy();JsonObject player=new JsonObject();if(!players.has(key(id)))throw new IllegalArgumentException("Incomplete player response");player.add(key(id),players.get(key(id)));single.add("players",player);values.putAll(decode(single.toString(),List.of(id)));}catch(RuntimeException e){errors.put(id,new PlayerLookup(PlayerLookup.Phase.FAILED,"Invalid or incomplete player response",System.currentTimeMillis()+60_000));}}
  return new BatchResult(Map.copyOf(values),Map.copyOf(errors),Math.max(REFRESH_MS,root.get("cacheSeconds").getAsLong()*1000));
 }
 private void trim(){cache.entrySet().removeIf(e->System.currentTimeMillis()-e.getValue().updated>=Math.max(MAX_STALE_MS,e.getValue().refreshMillis));while(cache.size()>2048)cache.remove(cache.keySet().iterator().next());while(failures.size()>4096)failures.remove(failures.keySet().iterator().next());}
 protected static boolean validCacheSeconds(JsonObject root){long seconds=root.get("cacheSeconds").getAsLong();return seconds>0&&seconds<=86400;}
 protected static String key(UUID id){return id.toString().replace("-","");}
 static Map<UUID,Map<String,String>> decode(String json,List<UUID> expected){
  JsonObject root=JsonParser.parseString(json).getAsJsonObject();if(!root.has("schemaVersion")||root.get("schemaVersion").getAsInt()!=1||!root.has("cacheSeconds")||!validCacheSeconds(root))throw new IllegalArgumentException("Invalid response");
  JsonObject players=root.getAsJsonObject("players");Set<String> keys=new HashSet<>();expected.forEach(id->keys.add(key(id)));if(!players.keySet().equals(keys))throw new IllegalArgumentException("Incomplete response");
  Map<UUID,Map<String,String>> result=new HashMap<>();for(UUID id:expected){JsonObject obj=players.getAsJsonObject(key(id));Map<String,String> ranks=new HashMap<>();for(var entry:obj.entrySet()){
   if(!MODES.contains(entry.getKey())||!entry.getValue().isJsonPrimitive()||!entry.getValue().getAsJsonPrimitive().isString()||!TIERS.contains(entry.getValue().getAsString()))throw new IllegalArgumentException("Invalid tier");ranks.put(entry.getKey(),entry.getValue().getAsString());
  }result.put(id,Map.copyOf(ranks));}return Map.copyOf(result);
 }
 public synchronized void clear(){generation++;if(scheduledTask!=null)scheduledTask.cancel(false);if(activeHttp!=null)activeHttp.cancel(true);activeHttp=null;scheduled=false;lastSuccess=0;failedPlayer=null;pending.clear();inFlight.clear();cache.clear();failures.clear();status="Not checked";onClear();}
 public void close(){synchronized(this){if(closed)return;clear();closed=true;worker.shutdownNow();}http.close();}
 private static final class RetryException extends Exception{final long delay;final int code;RetryException(long delay,int code){this.delay=delay;this.code=code;}}
}
