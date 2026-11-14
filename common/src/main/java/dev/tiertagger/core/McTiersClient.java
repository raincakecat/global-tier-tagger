package dev.tiertagger.core;
import java.net.*; import java.net.http.*; import java.time.Duration; import java.util.*; import java.util.concurrent.*;
/** Single bounded worker; no network calls on the render thread. */
public final class McTiersClient implements RankingProvider {
 private record Entry(Map<String,String> ranks,long expires) {}
 private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).build();
 private final ConcurrentHashMap<UUID,Entry> cache=new ConcurrentHashMap<>();
 private final Set<UUID> pending=ConcurrentHashMap.newKeySet();
 private final ThreadPoolExecutor worker=new ThreadPoolExecutor(1,1,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(128),r->{Thread t=new Thread(r,"tier-lookup");t.setDaemon(true);return t;});
 private volatile long retryAfter=0; private volatile String status="Waiting for players"; private volatile int generation=0;
 private volatile long lastSuccess;
 private volatile UUID failedPlayer;
 public void requestRetry(UUID preferred){if(System.currentTimeMillis()>=retryAtMillis())request(failedPlayer==null?preferred:failedPlayer);}
 public long retryAtMillis(){return retryAfter;}
 public long lastSuccessMillis(){return lastSuccess;}
 public String id(){return "mctiers";}
 public boolean resolved(UUID id){var entry=cache.get(id);return entry!=null&&entry.expires>System.currentTimeMillis();}
 public Optional<String> tier(UUID id,String mode) { Entry e=cache.get(id); if(e==null || e.expires<System.currentTimeMillis()) return Optional.empty(); return Optional.ofNullable(e.ranks.get(mode)); }
 public String status(){return status;}
 public void request(UUID id) {
  Entry e=cache.get(id); if((e!=null && e.expires>System.currentTimeMillis()) || System.currentTimeMillis()<retryAfter || !pending.add(id)) return;
  int epoch=generation;
  try { worker.execute(()->{ try {
   if(epoch!=generation || System.currentTimeMillis()<retryAfter) return;
   status="Checking";HttpRequest req=HttpRequest.newBuilder(URI.create("https://mctiers.net/api/v2/profile/"+id+"/rankings")).timeout(Duration.ofSeconds(8)).header("User-Agent","TierTagger/0.1 (Minecraft client)").GET().build();
   HttpResponse<String> res=http.send(req,HttpResponse.BodyHandlers.ofString());
   if(epoch!=generation)return;
   if(res.statusCode()==200) {cache.put(id,new Entry(McTiersParser.parse(res.body()),System.currentTimeMillis()+300_000)); lastSuccess=System.currentTimeMillis();status="MCTiers connected";}
   else if(res.statusCode()==404) {cache.put(id,new Entry(Map.of(),System.currentTimeMillis()+300_000)); lastSuccess=System.currentTimeMillis();status="MCTiers connected";}
   else {failedPlayer=id;retryAfter=System.currentTimeMillis()+Math.max(60,RetryAfter.seconds(res.headers().firstValue("Retry-After").orElse("60"),60))*1000;status=ProviderStatus.http(res.statusCode()).label();}
   Thread.sleep(1000);
  } catch(InterruptedException ex){Thread.currentThread().interrupt();} catch(Exception ex){failedPlayer=id;retryAfter=System.currentTimeMillis()+60_000;status=ProviderStatus.failure(ex).label();} finally {pending.remove(id);} }); }
  catch(RejectedExecutionException ex){pending.remove(id);}
 }
 public void clear(){lastSuccess=0;failedPlayer=null;status="Not checked";generation++;cache.clear();worker.getQueue().clear();pending.clear();}
 public void close(){clear();worker.shutdownNow();}
}
