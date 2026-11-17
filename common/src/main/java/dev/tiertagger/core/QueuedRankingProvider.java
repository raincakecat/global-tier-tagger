package dev.tiertagger.core;

import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.LongSupplier;

/** Shared bounded transport. Player rejection never pauses unrelated profiles. */
abstract class QueuedRankingProvider implements RankingProvider {
 protected static final class HttpFailure extends IOException {
  final int code;final long retrySeconds;
  HttpFailure(int code,long retrySeconds){super("HTTP "+code);this.code=code;this.retrySeconds=retrySeconds;}
 }
 protected static final class PartialFailure extends Exception {
  final Map<String,String> ranks;final Exception cause;
  PartialFailure(Map<String,String> ranks,Exception cause){super(cause);this.ranks=Map.copyOf(ranks);this.cause=cause;}
 }
 private record Entry(Map<String,String> ranks,long expires,boolean complete){}
 private final String providerId;
 private final long ttl;
 private final boolean bulk;
 protected final LongSupplier now;
 private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NORMAL).build();
 private final ThreadPoolExecutor worker;
 private final LinkedHashMap<UUID,Entry> cache=new LinkedHashMap<>(16,.75f,true);
 private final LinkedHashMap<UUID,PlayerLookup> outcomes=new LinkedHashMap<>();
 private final Map<UUID,Job> pending=new HashMap<>();
 private Map<UUID,Map<String,String>> snapshot=Map.of();
 private long snapshotExpires,pausedUntil,lastSuccess,nextStartNanos,spacingNanos=1_000_000_000L;
 private int generation;
 private boolean closed;
 private String service="Not checked";
 private UUID failedPlayer;
 private CompletableFuture<?> activeHttp;
 private final ThreadLocal<Integer> requestGeneration=new ThreadLocal<>();

 protected QueuedRankingProvider(String id,long ttl,boolean bulk,LongSupplier clock){
  providerId=id;this.ttl=ttl;this.bulk=bulk;now=clock;
  worker=new ThreadPoolExecutor(1,1,0,TimeUnit.SECONDS,new LinkedBlockingDeque<>(128),r->{Thread t=new Thread(r,"tier-"+id);t.setDaemon(true);return t;});
 }
 protected abstract Map<String,String> fetch(UUID id)throws Exception;
 protected Map<UUID,Map<String,String>> fetchSnapshot()throws Exception{throw new UnsupportedOperationException();}
 protected String connectedLabel(){return "Connected";}
 protected void onClear(){}
 public final String id(){return providerId;}
 public synchronized String status(){return service;}
 public synchronized long retryAtMillis(){return pausedUntil;}
 public synchronized long lastSuccessMillis(){return lastSuccess;}
 private UUID jobKey(UUID id){return bulk?new UUID(0,0):id;}
 public synchronized boolean resolved(UUID id){if(!PlayerLookup.supported(id))return false;if(bulk)return snapshotExpires>now.getAsLong();var e=cache.get(id);return e!=null&&e.complete&&e.expires>now.getAsLong();}
 public synchronized Optional<String> tier(UUID id,String mode){if(!PlayerLookup.supported(id))return Optional.empty();if(bulk)return snapshotExpires>now.getAsLong()?Optional.ofNullable(snapshot.getOrDefault(id,Map.of()).get(mode)):Optional.empty();var e=cache.get(id);return e!=null&&e.expires>now.getAsLong()?Optional.ofNullable(e.ranks.get(mode)):Optional.empty();}
 public synchronized PlayerLookup lookup(UUID id){
  if(!PlayerLookup.supported(id))return new PlayerLookup(PlayerLookup.Phase.SKIPPED,"Unsupported Minecraft UUID",0);
  if(resolved(id))return PlayerLookup.of(PlayerLookup.Phase.READY);
  var outcome=outcomes.get(jobKey(id));
  if(outcome!=null&&outcome.failed())return outcome;
  var job=pending.get(jobKey(id));
  if(pausedUntil>now.getAsLong())return new PlayerLookup(PlayerLookup.Phase.RETRY_WAIT,"Provider cooldown",pausedUntil);
  if(job!=null)return PlayerLookup.of(job.running?PlayerLookup.Phase.FETCHING:PlayerLookup.Phase.QUEUED);
  return outcome==null?PlayerLookup.of(PlayerLookup.Phase.NOT_REQUESTED):outcome;
 }
 public void request(UUID id){enqueue(id,false);}
 public void requestForeground(UUID id){enqueue(id,true);}
 public synchronized void requestRetry(UUID preferred){
  UUID id=failedPlayer==null?preferred:failedPlayer;
  if(id==null||pausedUntil>now.getAsLong())return;
  var prior=outcomes.get(jobKey(id));if(prior!=null&&prior.retryAtMillis()>now.getAsLong())return;
  enqueue(id,true);
 }
 private synchronized void enqueue(UUID id,boolean foreground){
  if(closed||!PlayerLookup.supported(id)||resolved(id))return;
  UUID key=jobKey(id);var prior=outcomes.get(key);
  if(prior!=null&&prior.failed()&&prior.retryAtMillis()>now.getAsLong())return;
  Job existing=pending.get(key);
  if(existing!=null){if(foreground){existing.foreground=true;if(!existing.running&&worker.getQueue().remove(existing))((LinkedBlockingDeque<Runnable>)worker.getQueue()).offerFirst(existing);}return;}
  if(foreground&&worker.getQueue().remainingCapacity()==0){var iterator=((LinkedBlockingDeque<Runnable>)worker.getQueue()).descendingIterator();while(iterator.hasNext()){var candidate=(Job)iterator.next();if(!candidate.foreground&&worker.getQueue().remove(candidate)){pending.remove(candidate.key,candidate);break;}}}
  Job job=new Job(id,key,generation);job.foreground=foreground;pending.put(key,job);outcomes.remove(key);
  try{worker.execute(job);if(foreground&&!job.running&&worker.getQueue().remove(job))((LinkedBlockingDeque<Runnable>)worker.getQueue()).offerFirst(job);}
  catch(RejectedExecutionException e){pending.remove(key,job);outcomes.put(key,new PlayerLookup(PlayerLookup.Phase.FAILED,"Request queue busy",now.getAsLong()+1000));trim();}
 }
 private final class Job implements Runnable {
  final UUID id,key;final int epoch;boolean running,foreground;
  Job(UUID id,UUID key,int epoch){this.id=id;this.key=key;this.epoch=epoch;}
  public void run(){
   synchronized(QueuedRankingProvider.this){if(closed||epoch!=generation)return;running=true;}
   requestGeneration.set(epoch);
   try{
    if(bulk){var values=fetchSnapshot();synchronized(QueuedRankingProvider.this){if(!valid(epoch))return;snapshot=Map.copyOf(values);snapshotExpires=now.getAsLong()+ttl;}}
    else{var ranks=fetch(id);synchronized(QueuedRankingProvider.this){if(!valid(epoch))return;cache.put(id,new Entry(Map.copyOf(ranks),now.getAsLong()+ttl,true));}}
    synchronized(QueuedRankingProvider.this){if(valid(epoch)){outcomes.remove(key);lastSuccess=now.getAsLong();failedPlayer=null;service=connectedLabel();trim();}}
   }catch(PartialFailure e){failure(this,e.cause,e.ranks);}
   catch(Exception e){failure(this,e,null);if(e instanceof InterruptedException)Thread.currentThread().interrupt();}
   finally{synchronized(QueuedRankingProvider.this){pending.remove(key,this);}requestGeneration.remove();}
  }
 }
 private synchronized boolean valid(int epoch){return !closed&&epoch==generation;}
 private synchronized void failure(Job job,Exception error,Map<String,String> partial){
  if(!valid(job.epoch)||error instanceof CancellationException||error instanceof InterruptedException)return;
  boolean serviceFailure=error instanceof HttpFailure h?(h.code==429||h.code>=500||h.code==401||h.code==403||bulk):error instanceof IOException;
  long delay=error instanceof HttpFailure h?Math.max(60,h.retrySeconds)*1000:60_000;
  if(error instanceof HttpFailure h&&(h.code==400||h.code==422)&&!bulk)delay=300_000;
  long retry=now.getAsLong()+delay;
  if(serviceFailure){pausedUntil=Math.max(pausedUntil,retry);service=ProviderStatus.failure(error).label();failedPlayer=job.id;}
  String reason=error instanceof HttpFailure h?((h.code==400||h.code==422)?"Player request rejected (HTTP "+h.code+")":ProviderStatus.http(h.code).reason()):ProviderStatus.failure(error).reason();
  if(partial!=null&&!partial.isEmpty())cache.put(job.id,new Entry(partial,now.getAsLong()+ttl,false));
  outcomes.put(job.key,new PlayerLookup(partial!=null&&!partial.isEmpty()?PlayerLookup.Phase.PARTIAL:PlayerLookup.Phase.FAILED,reason,retry));trim();
 }
 private void trim(){cache.entrySet().removeIf(e->e.getValue().expires<=now.getAsLong());while(cache.size()>2048)cache.remove(cache.keySet().iterator().next());while(outcomes.size()>4096)outcomes.remove(outcomes.keySet().iterator().next());}
 private void awaitBudget()throws InterruptedException {
  for(;;){long wait; synchronized(this){Integer epoch=requestGeneration.get();if(epoch==null||!valid(epoch))throw new CancellationException();wait=Math.max(pausedUntil-now.getAsLong(),TimeUnit.NANOSECONDS.toMillis(nextStartNanos-System.nanoTime()));if(wait<=0){nextStartNanos=System.nanoTime()+spacingNanos;service="Checking";return;}}Thread.sleep(Math.min(250,Math.max(1,wait)));}
 }
 protected final String get(String url)throws Exception{return exchange(URI.create(url));}
 protected final String getProfile(String url)throws Exception{try{return get(url);}catch(HttpFailure e){if(e.code==404)return null;throw e;}}
 private String exchange(URI uri)throws Exception{
  awaitBudget();int epoch=requestGeneration.get();
  CompletableFuture<HttpResponse<byte[]>> future;
  synchronized(this){if(!valid(epoch))throw new CancellationException();future=http.sendAsync(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(12)).header("Accept","application/json").header("User-Agent","GlobalTierTagger/1.0.2").GET().build(),HttpBodies.limited(bulk?8*1024*1024:1024*1024));activeHttp=future;}
  HttpResponse<byte[]> response;
  try{response=future.get(13,TimeUnit.SECONDS);}catch(ExecutionException e){if(e.getCause() instanceof Exception cause)throw cause;throw e;}catch(TimeoutException e){future.cancel(true);throw new HttpTimeoutException("Request timed out");}
  finally{synchronized(this){if(activeHttp==future)activeHttp=null;}}
  synchronized(this){if(!valid(epoch))throw new CancellationException();
   response.headers().firstValue("X-RateLimit-Limit").ifPresent(v->{try{long limit=Long.parseLong(v);if(limit>0&&limit<60){spacingNanos=Math.max(spacingNanos,TimeUnit.MILLISECONDS.toNanos((60000+limit-1)/limit));nextStartNanos=Math.max(nextStartNanos,System.nanoTime()+spacingNanos);}}catch(NumberFormatException ignoredValue){}});
   long retry=RetryAfter.seconds(response.headers().firstValue("Retry-After").orElse("60"),60);
   if(response.statusCode()==429||response.statusCode()>=500){pausedUntil=Math.max(pausedUntil,now.getAsLong()+Math.max(60,retry)*1000);throw new HttpFailure(response.statusCode(),retry);}
   service=connectedLabel();
   if(response.statusCode()!=200)throw new HttpFailure(response.statusCode(),retry);
  }
  return new String(response.body(),StandardCharsets.UTF_8);
 }
 public synchronized void clear(){generation++;if(activeHttp!=null)activeHttp.cancel(true);activeHttp=null;worker.getQueue().clear();pending.clear();cache.clear();outcomes.clear();snapshot=Map.of();snapshotExpires=0;lastSuccess=0;failedPlayer=null;service="Not checked";onClear();}
 public void close(){synchronized(this){if(closed)return;clear();closed=true;worker.shutdownNow();}http.close();}
}
