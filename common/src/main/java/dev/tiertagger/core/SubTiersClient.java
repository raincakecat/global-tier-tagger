package dev.tiertagger.core;

import com.google.gson.*;
import java.io.InputStream;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

/** Read-only UUID lookups; all I/O happens on one bounded background worker. */
public final class SubTiersClient implements RankingProvider {
 private record Entry(Map<String,String> tiers,long expires) {}
 private static final Map<String,String> MODES=Map.ofEntries(
  Map.entry("minecart","minecart"),Map.entry("dia_crystal","diamond-vanilla"),Map.entry("debuff","debuff"),
  Map.entry("elytra","elytra"),Map.entry("speed","speed"),Map.entry("creeper","creeper"),
  Map.entry("manhunt","manhunt"),Map.entry("dia_smp","diamond-smp"),Map.entry("bow","bow"),
  Map.entry("bed","bed"),Map.entry("og_vanilla","og-vanilla"),Map.entry("trident","trident"));
 private static final long CACHE_MS=300_000;
 private final URI base;
 private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();
 private final Map<UUID,Entry> cache=new ConcurrentHashMap<>();
 private final Set<UUID> pending=ConcurrentHashMap.newKeySet();
 private final ThreadPoolExecutor worker=new ThreadPoolExecutor(1,1,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(128),r->{Thread t=new Thread(r,"subtiers-lookup");t.setDaemon(true);return t;});
 private volatile long retryAfter;private volatile int generation;private volatile String status="Waiting for players";
 public SubTiersClient(){this(URI.create("https://subtiers.net/api/profile/"));}
 SubTiersClient(URI base){this.base=base;}
 private volatile long lastSuccess;
 private volatile UUID failedPlayer;
 public void requestRetry(UUID preferred){if(System.currentTimeMillis()>=retryAtMillis())request(failedPlayer==null?preferred:failedPlayer);}
 public long retryAtMillis(){return retryAfter;}
 public long lastSuccessMillis(){return lastSuccess;}
 public String id(){return "subtiers";}
 public String status(){return status;}
 public boolean resolved(UUID id){var entry=cache.get(id);return entry!=null&&entry.expires>System.currentTimeMillis();}
 public Optional<String> tier(UUID id,String mode){Entry e=cache.get(id);return e==null||e.expires<System.currentTimeMillis()?Optional.empty():Optional.ofNullable(e.tiers.get(mode));}
 static Map<String,String> parse(String json,UUID expected){
  JsonObject root=JsonParser.parseString(json).getAsJsonObject();
  if(!root.has("uuid")||!root.get("uuid").getAsString().replace("-","").equalsIgnoreCase(expected.toString().replace("-",""))||!root.has("rankings")||!root.get("rankings").isJsonObject())throw new IllegalArgumentException("Invalid profile");
  Map<String,String> result=new HashMap<>();JsonObject rankings=root.getAsJsonObject("rankings");
  for(var mapping:MODES.entrySet()){
   JsonElement raw=rankings.get(mapping.getKey());if(raw==null||raw.isJsonNull())continue;if(!raw.isJsonObject())throw new IllegalArgumentException("Invalid ranking");
   JsonObject rank=raw.getAsJsonObject();if(!rank.has("tier")||!rank.has("pos"))throw new IllegalArgumentException("Incomplete ranking");
   int tier=rank.get("tier").getAsInt(),pos=rank.get("pos").getAsInt();if(tier<1||tier>5||(pos!=0&&pos!=1))throw new IllegalArgumentException("Invalid tier");
   result.put(mapping.getValue(),(pos==0?"HT":"LT")+tier);
  }
  return Map.copyOf(result);
 }
 public void request(UUID id){
  Entry old=cache.get(id);if((old!=null&&old.expires>System.currentTimeMillis())||System.currentTimeMillis()<retryAfter||!pending.add(id))return;
  int epoch=generation;
  try{worker.execute(()->{try{
   if(epoch!=generation||System.currentTimeMillis()<retryAfter)return;
   status="Checking";var request=HttpRequest.newBuilder(base.resolve(id.toString())).timeout(Duration.ofSeconds(8)).header("Accept","application/json").header("User-Agent","MultiTierTagger/0.1").GET().build();
   var response=http.send(request,HttpResponse.BodyHandlers.ofInputStream());
   try(InputStream input=response.body()){
    if(epoch!=generation)return;
    if(response.statusCode()==200){byte[] body=input.readNBytes(262145);if(body.length>262144)throw new IllegalArgumentException("Response too large");Map<String,String> tiers=parse(new String(body,StandardCharsets.UTF_8),id);synchronized(this){if(epoch==generation){cache.put(id,new Entry(tiers,System.currentTimeMillis()+CACHE_MS));lastSuccess=System.currentTimeMillis();status="SubTiers connected";}}}
    else if(response.statusCode()==404){synchronized(this){if(epoch==generation){cache.put(id,new Entry(Map.of(),System.currentTimeMillis()+CACHE_MS));lastSuccess=System.currentTimeMillis();status="SubTiers connected";}}}
    else{failedPlayer=id;long seconds=RetryAfter.seconds(response.headers().firstValue("Retry-After").orElse("60"),60);synchronized(this){if(epoch==generation){failedPlayer=id;retryAfter=System.currentTimeMillis()+seconds*1000;status=ProviderStatus.http(response.statusCode()).label();}}}
   }
   Thread.sleep(1000);
  }catch(InterruptedException e){Thread.currentThread().interrupt();}catch(Exception e){synchronized(this){if(epoch==generation){failedPlayer=id;retryAfter=System.currentTimeMillis()+60_000;status=ProviderStatus.failure(e).label();}}}finally{if(epoch==generation)pending.remove(id);}});}catch(RejectedExecutionException e){pending.remove(id);}
 }
 public synchronized void clear(){lastSuccess=0;failedPlayer=null;generation++;cache.clear();worker.getQueue().clear();pending.clear();status="Waiting for players";}
 public void close(){clear();worker.shutdownNow();http.close();}
}
