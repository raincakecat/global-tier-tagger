package dev.tiertagger.core;

import com.google.gson.*;
import java.io.InputStream;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

/** Batched, read-only Lunar rank lookups, isolated from Minecraft's render thread. */
public final class LunarClient implements RankingProvider {
 private static final String ENDPOINT="https://lunar-ranks.cattiers.com/v1/ranks/lookup";
 private static final long REFRESH_MS=60_000, MAX_STALE_MS=300_000;
 private static final Set<String> MODES=Set.of("crystal","sword","axe","smp","pot","mace","uhc","nethpot","spear-mace");
 private static final Set<String> TIERS=Set.of("HT1","LT1","HT2","LT2","HT3","LT3","HT4","LT4","HT5","LT5");
 private record Entry(Map<String,String> ranks,long updated){}
 private final URI endpoint;
 private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();
 private final ScheduledExecutorService worker=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"lunar-tier-lookup");t.setDaemon(true);return t;});
 private final Map<UUID,Entry> cache=new ConcurrentHashMap<>();
 private final LinkedHashSet<UUID> pending=new LinkedHashSet<>();
 private final Set<UUID> inFlight=new HashSet<>();
 private volatile String status="Waiting for players";
 private volatile long retryAt;private int generation;private boolean scheduled,closed;
 public LunarClient(){this(URI.create(ENDPOINT));}
 LunarClient(URI endpoint){this.endpoint=endpoint;}
 private volatile long lastSuccess;
 private volatile UUID failedPlayer;
 public void requestRetry(UUID preferred){if(System.currentTimeMillis()>=retryAtMillis())request(failedPlayer==null?preferred:failedPlayer);}
 public long retryAtMillis(){return retryAt;}
 public long lastSuccessMillis(){return lastSuccess;}
 public String id(){return "lunar";}
 public String status(){return status;}
 public boolean resolved(UUID id){var e=cache.get(id);return e!=null&&System.currentTimeMillis()-e.updated<300000;}
 public Optional<String> tier(UUID id,String mode){Entry e=cache.get(id);return e==null||System.currentTimeMillis()-e.updated()>MAX_STALE_MS?Optional.empty():Optional.ofNullable(e.ranks().get(mode));}
 public synchronized void request(UUID id){
  if(closed)return;long now=System.currentTimeMillis();Entry old=cache.get(id);
  if(old!=null && now-old.updated()<REFRESH_MS)return;
  if(now<retryAt || pending.size()>=4096 || inFlight.contains(id))return;
  pending.add(id);schedule();
 }
 private synchronized void schedule(){if(!closed&&!scheduled&&!pending.isEmpty()){scheduled=true;worker.schedule(this::runBatch,150,TimeUnit.MILLISECONDS);}}
 private void runBatch(){
  List<UUID> batch;int epoch;
  synchronized(this){if(closed){scheduled=false;return;}epoch=generation;batch=pending.stream().limit(100).toList();pending.removeAll(batch);inFlight.addAll(batch);}
  if(batch.isEmpty()){synchronized(this){scheduled=false;schedule();}return;}
  try{
   String body=new Gson().toJson(Map.of("uuids",batch.stream().map(LunarClient::key).toList()));
   status="Checking";HttpRequest request=HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(10)).header("Content-Type","application/json").header("Accept","application/json").header("User-Agent","MultiTierTagger/0.1").POST(HttpRequest.BodyPublishers.ofString(body)).build();
   var response=http.send(request,HttpResponse.BodyHandlers.ofInputStream());
   try(InputStream input=response.body()){
    if(response.statusCode()!=200){long seconds=RetryAfter.seconds(response.headers().firstValue("Retry-After").orElse("5"),5);throw new RetryException(seconds*1000,response.statusCode());}
    byte[] bytes=input.readNBytes(262145);if(bytes.length>262144)throw new IllegalArgumentException("Response too large");
    Map<UUID,Map<String,String>> values=decode(new String(bytes,StandardCharsets.UTF_8),batch);
    synchronized(this){if(epoch==generation&&!closed){long now=System.currentTimeMillis();for(var v:values.entrySet())cache.put(v.getKey(),new Entry(v.getValue(),now));lastSuccess=System.currentTimeMillis();status="Lunar Tiers connected";retryAt=0;}}
   }
  }catch(Exception error){synchronized(this){if(epoch==generation&&!closed){failedPlayer=batch.getFirst();retryAt=System.currentTimeMillis()+(error instanceof RetryException r?r.delay:5000);status=error instanceof RetryException r?ProviderStatus.http(r.code).label():ProviderStatus.failure(error).label();pending.clear();}}if(error instanceof InterruptedException)Thread.currentThread().interrupt();}
  finally{synchronized(this){if(epoch==generation)inFlight.removeAll(batch);scheduled=false;schedule();}}
 }
 private static String key(UUID id){return id.toString().replace("-","");}
 static Map<UUID,Map<String,String>> decode(String json,List<UUID> expected){
  JsonObject root=JsonParser.parseString(json).getAsJsonObject();if(!root.has("schemaVersion")||root.get("schemaVersion").getAsInt()!=1||!root.has("cacheSeconds")||root.get("cacheSeconds").getAsInt()!=60)throw new IllegalArgumentException("Invalid response");
  JsonObject players=root.getAsJsonObject("players");Set<String> keys=new HashSet<>();expected.forEach(id->keys.add(key(id)));if(!players.keySet().equals(keys))throw new IllegalArgumentException("Incomplete response");
  Map<UUID,Map<String,String>> result=new HashMap<>();for(UUID id:expected){JsonObject obj=players.getAsJsonObject(key(id));Map<String,String> ranks=new HashMap<>();for(var entry:obj.entrySet()){
   if(!MODES.contains(entry.getKey())||!entry.getValue().isJsonPrimitive()||!entry.getValue().getAsJsonPrimitive().isString()||!TIERS.contains(entry.getValue().getAsString()))throw new IllegalArgumentException("Invalid tier");ranks.put(entry.getKey(),entry.getValue().getAsString());
  }result.put(id,Map.copyOf(ranks));}return Map.copyOf(result);
 }
 public synchronized void clear(){lastSuccess=0;failedPlayer=null;generation++;pending.clear();inFlight.clear();cache.clear();status="Waiting for players";}
 public synchronized void close(){closed=true;generation++;pending.clear();inFlight.clear();cache.clear();worker.shutdownNow();http.close();}
 private static final class RetryException extends Exception{final long delay;final int code;RetryException(long delay,int code){this.delay=delay;this.code=code;}}
}
