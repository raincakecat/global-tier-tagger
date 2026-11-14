package dev.tiertagger.core;

import com.google.gson.*;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

/** TierTests public API. Requests are made only for selected, nearby players. */
public final class TierTestsClient implements RankingProvider {
 private record Entry(Map<String,String> ranks,long expires) {}
 private static final long CACHE_MS=4*60*60*1000L;
 private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).build();
 private final ConcurrentHashMap<UUID,Entry> cache=new ConcurrentHashMap<>();
 private final Set<UUID> pending=ConcurrentHashMap.newKeySet();
 private final ThreadPoolExecutor worker=new ThreadPoolExecutor(1,1,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(128),r->{Thread t=new Thread(r,"tiertests-lookup");t.setDaemon(true);return t;});
 private volatile long retryAfter;private volatile int generation;private volatile String status="Waiting for players";
 private volatile long lastSuccess;
 private volatile UUID failedPlayer;
 public void requestRetry(UUID preferred){if(System.currentTimeMillis()>=retryAtMillis())request(failedPlayer==null?preferred:failedPlayer);}
 public long retryAtMillis(){return retryAfter;}
 public long lastSuccessMillis(){return lastSuccess;}
 public String id(){return "tiertests";} public String status(){return status;}
 public boolean resolved(UUID id){var entry=cache.get(id);return entry!=null&&entry.expires>System.currentTimeMillis();}
 public Optional<String> tier(UUID id,String mode){Entry e=cache.get(id);return e==null||e.expires<System.currentTimeMillis()?Optional.empty():Optional.ofNullable(e.ranks.get(mode));}
 static Map<String,String> parse(String response){
  JsonObject root=JsonParser.parseString(response).getAsJsonObject();if(!root.has("success")||!root.get("success").getAsBoolean()||!root.has("data")||!root.get("data").isJsonArray())throw new IllegalArgumentException("Invalid TierTests response");
  Map<String,String> result=new HashMap<>();
  for(JsonElement e:root.getAsJsonArray("data")){if(!e.isJsonObject())continue;JsonObject t=e.getAsJsonObject();
   if(!t.has("gamemode")||!t.get("gamemode").isJsonObject()||!t.has("tier")||t.get("tier").isJsonNull())continue;
   JsonObject g=t.getAsJsonObject("gamemode");if(!g.has("name")||g.get("name").isJsonNull())continue;
   String name=g.get("name").getAsString();String mode=switch(name){case "CRYSTAL"->"crystal";case "SWORD"->"sword";case "SMP"->"smp";case "NETH"->"nethpot";case "MACE"->"mace";case "UHC"->"uhc";default->null;};
   String raw=t.get("tier").getAsString();String rank=switch(raw){case "S","F"->raw;case "A_PLUS","B_PLUS","C_PLUS","D_PLUS"->raw.substring(0,1)+"+";case "A_MINUS","B_MINUS","C_MINUS","D_MINUS"->raw.substring(0,1)+"-";default->null;};
   if(mode!=null&&rank!=null)result.put(mode,rank);
  }return Map.copyOf(result);
 }
 public void request(UUID id){
  Entry e=cache.get(id);if((e!=null&&e.expires>System.currentTimeMillis())||System.currentTimeMillis()<retryAfter||!pending.add(id))return;
  int epoch=generation;
  try{worker.execute(()->{try{
   if(epoch!=generation||System.currentTimeMillis()<retryAfter)return;
   status="Checking";var req=HttpRequest.newBuilder(URI.create("https://api.tiertests.com/v1/tiers/current/all?version=MODERN&minecraftUuid="+id)).timeout(Duration.ofSeconds(8)).header("User-Agent","MultiTierTagger/0.1").GET().build();
   var res=http.send(req,HttpResponse.BodyHandlers.ofString());if(epoch!=generation)return;
   if(res.statusCode()==200){cache.put(id,new Entry(parse(res.body()),System.currentTimeMillis()+CACHE_MS));lastSuccess=System.currentTimeMillis();status="TierTests connected";}
   else if(res.statusCode()==404){cache.put(id,new Entry(Map.of(),System.currentTimeMillis()+CACHE_MS));lastSuccess=System.currentTimeMillis();status="TierTests connected";}
   else {failedPlayer=id;long seconds=RetryAfter.seconds(res.headers().firstValue("Retry-After").orElse("60"),60);retryAfter=System.currentTimeMillis()+seconds*1000;status=ProviderStatus.http(res.statusCode()).label();}
   Thread.sleep(1000);
  }catch(InterruptedException ex){Thread.currentThread().interrupt();}catch(Exception ex){failedPlayer=id;retryAfter=System.currentTimeMillis()+60_000;status=ProviderStatus.failure(ex).label();}finally{pending.remove(id);}});}catch(RejectedExecutionException ex){pending.remove(id);}
 }
 public void clear(){lastSuccess=0;failedPlayer=null;status="Not checked";generation++;cache.clear();worker.getQueue().clear();pending.clear();}
 public void close(){clear();worker.shutdownNow();}
}
