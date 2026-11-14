package dev.tiertagger.core;

import com.google.gson.*;
import java.io.InputStream;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;

/** Public, read-only client. One bounded queue for every restriction request. */
public final class RestrictionClient implements AutoCloseable {
 public record Record(String community,String reason,List<String> categories,Integer durationDays,String restrictedAt,String expiresAt,String archivedAt,String verification,String sourceUrl) {}
 public record Result(UUID uuid,String username,boolean nameUnavailable,String profileUrl,int activeCount,int pastCount,List<Record> active,List<Record> past,int page,Integer nextPage,boolean activeTruncated,boolean pastTruncated,boolean hasMorePast,boolean beyondPageLimit,boolean sample,String notice,String requestId,String respondedAt) {}
 public record Status(String checkedAt,String scope) {}
 public static final class Failure extends RuntimeException {
  public final String code,requestId;public final long retrySeconds;
  Failure(String code,String message,String requestId,long retrySeconds){super(message);this.code=code;this.requestId=requestId;this.retrySeconds=retrySeconds;}
 }
 private record Cached(Object value,long expires) {}
 private static final java.util.concurrent.atomic.AtomicLong SEQUENCE=new java.util.concurrent.atomic.AtomicLong();
 private record Job(int priority,long sequence,Runnable action) implements Runnable,Comparable<Job>{public void run(){action.run();}public int compareTo(Job other){int p=Integer.compare(priority,other.priority);return p!=0?p:Long.compare(sequence,other.sequence);}}

 private static final class Flight {CompletableFuture<?> http;final List<CompletableFuture<Object>> listeners=new ArrayList<>();boolean wanted(){return listeners.stream().anyMatch(f->!f.isDone());}}
 private final URI base;
 private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();
 private final ThreadPoolExecutor worker=new ThreadPoolExecutor(1,1,0,TimeUnit.SECONDS,new PriorityBlockingQueue<>(),r->{Thread t=new Thread(r,"restricttiers");t.setDaemon(true);return t;});
 private final Map<String,Cached> cache=new HashMap<>();private final Map<String,Flight> flights=new HashMap<>();
 private long nextRequestAt,pausedUntil,spacing=1000;private volatile String status="Not checked";private boolean closed;
 public RestrictionClient(){this(URI.create("https://restricttiers.raincakecat.com/api/v1/"));}
 public RestrictionClient(URI base){this.base=base;}
 public String status(){return status;}
 public synchronized long cooldownSeconds(){return Math.max(0,(pausedUntil-System.currentTimeMillis()+999)/1000);}
 public CompletableFuture<Result> lookupScan(UUID uuid){if(!PlayerLookup.supported(uuid))return CompletableFuture.failedFuture(new IllegalArgumentException("Unsupported Minecraft UUID"));return request("players/"+uuid.toString().replace("-","")+"?page=1",json->parse(json,uuid,1),1);}
 public CompletableFuture<Result> lookup(UUID uuid,int page){if(!PlayerLookup.supported(uuid))return CompletableFuture.failedFuture(new IllegalArgumentException("Unsupported Minecraft UUID"));if(page<1||page>10000)return CompletableFuture.failedFuture(new IllegalArgumentException("History page out of range"));return request("players/"+uuid.toString().replace("-","")+"?page="+page,json->parse(json,uuid,page));}
 public CompletableFuture<Status> checkStatus(){return request("status",json->{JsonObject data=json.getAsJsonObject("data");if(!"healthy".equals(data.get("status").getAsString()))throw new IllegalArgumentException("Invalid status");return new Status(data.get("checkedAt").getAsString(),data.get("scope").getAsString());});}
 private <T> CompletableFuture<T> request(String path,Function<JsonObject,T> parser){return request(path,parser,0);}
 @SuppressWarnings("unchecked") private synchronized <T> CompletableFuture<T> request(String path,Function<JsonObject,T> parser,int priority){
  if(closed)return CompletableFuture.failedFuture(new CancellationException("Checker closed"));Cached old=cache.get(path);if(old!=null&&old.expires>System.currentTimeMillis())return CompletableFuture.completedFuture((T)old.value);
  CompletableFuture<Object> listener=new CompletableFuture<>();Flight flight=flights.get(path);if(flight!=null){flight.listeners.add(listener);return (CompletableFuture<T>)(CompletableFuture<?>)listener;}
  if(flights.size()>=129)return CompletableFuture.failedFuture(new Failure("BUSY","Restriction request queue is full","",0));
  flight=new Flight();flight.listeners.add(listener);flights.put(path,flight);Flight job=flight;
  try{worker.execute(new Job(priority,SEQUENCE.getAndIncrement(),()->run(path,parser,job)));}catch(RejectedExecutionException ex){flights.remove(path);listener.completeExceptionally(new Failure("BUSY","Restriction request queue is full","",0));}
  return (CompletableFuture<T>)(CompletableFuture<?>)listener;
 }
 private <T> void run(String path,Function<JsonObject,T> parser,Flight flight){
  try{
   while(true){long wait;synchronized(this){if(!flight.wanted())return;wait=Math.max(nextRequestAt,pausedUntil)-System.currentTimeMillis();if(wait<=0){nextRequestAt=System.currentTimeMillis()+spacing;break;}}Thread.sleep(Math.min(wait,250));}
   CompletableFuture<HttpResponse<byte[]>> request;
   synchronized(this){if(closed||!flight.wanted())return;status="Checking";request=http.sendAsync(HttpRequest.newBuilder(base.resolve(path)).timeout(Duration.ofSeconds(10)).header("Accept","application/json").header("User-Agent","GlobalTierTagger/1.0.1 RestrictTiers integration").GET().build(),HttpBodies.limited(2*1024*1024));flight.http=request;}
   HttpResponse<byte[]> response;
   try{response=request.get(11,TimeUnit.SECONDS);}catch(TimeoutException timeout){request.cancel(true);throw new Failure("SERVICE_UNAVAILABLE","Restriction request timed out","",0);}catch(ExecutionException wrapped){if(wrapped.getCause() instanceof Exception cause)throw cause;throw wrapped;}
   byte[] bytes=response.body();
   response.headers().firstValue("X-RateLimit-Limit").ifPresent(value->{try{int allowance=Integer.parseInt(value);if(allowance>0)synchronized(this){spacing=Math.max(spacing,(60000L+allowance-1)/allowance);nextRequestAt=Math.max(nextRequestAt,System.currentTimeMillis()+spacing);}}catch(NumberFormatException ignored){}});
   if(response.statusCode()==429){long retry;retry=RetryAfter.seconds(response.headers().firstValue("Retry-After").orElse("60"),60);synchronized(this){pausedUntil=System.currentTimeMillis()+Math.min(retry,86400)*1000;}}
   JsonObject json;try{json=JsonParser.parseString(new String(bytes,StandardCharsets.UTF_8)).getAsJsonObject();}catch(RuntimeException ex){if(response.statusCode()==200)throw new Failure("SERVICE_UNAVAILABLE","Invalid restriction response","",0);json=new JsonObject();}
   String requestId=json.has("meta")?text(json.getAsJsonObject("meta"),"requestId"):"";
   if(response.statusCode()!=200){long retry=0;if(response.statusCode()==429){String raw=response.headers().firstValue("Retry-After").orElse("60");retry=RetryAfter.seconds(raw,60);if(json.has("error")&&json.getAsJsonObject("error").has("retryAfter"))retry=Math.max(retry,json.getAsJsonObject("error").get("retryAfter").getAsLong());synchronized(this){pausedUntil=System.currentTimeMillis()+Math.min(retry,86400)*1000;}}
    String code=response.statusCode()==429?"RATE_LIMITED":response.statusCode()==404?"PLAYER_NOT_FOUND":response.statusCode()==400?"INVALID_REQUEST":"SERVICE_UNAVAILABLE";
    throw new Failure(code,response.statusCode()==429?"Rate limited; wait "+retry+" seconds":response.statusCode()==404?"Player not found":"Restriction service unavailable",requestId,retry);
   }
   T value;try{value=parser.apply(json);}catch(RuntimeException ex){throw new Failure("INVALID_RESPONSE","Restriction response identity or fields did not match",requestId,0);}
   synchronized(this){if(flight.wanted()&&!closed){cache.entrySet().removeIf(e->e.getValue().expires<System.currentTimeMillis());if(cache.size()>=2048)cache.clear();cache.put(path,new Cached(value,System.currentTimeMillis()+60_000));status="Available";}for(var f:flight.listeners)if(!f.isDone())f.complete(value);}
  }catch(InterruptedException ex){Thread.currentThread().interrupt();finishFailure(flight,new CancellationException("Check cancelled"));}catch(Exception ex){synchronized(this){if(!closed&&flight.wanted())status="Error: "+(ex instanceof Failure f?f.getMessage():ProviderStatus.failure(ex).reason());}finishFailure(flight,ex instanceof Failure?ex:new Failure("SERVICE_UNAVAILABLE","Restriction service unavailable","",0));}
  finally{synchronized(this){flights.remove(path,flight);}}
 }
 private synchronized void finishFailure(Flight flight,Throwable error){for(var f:flight.listeners)if(!f.isDone())f.completeExceptionally(error);}
 public synchronized void cancelPending(){for(Flight f:flights.values()){for(var listener:f.listeners)listener.cancel(false);if(f.http!=null)f.http.cancel(true);}worker.getQueue().clear();flights.clear();if(status.equals("Checking"))status="Not checked";}
 public void close(){synchronized(this){closed=true;cancelPending();worker.shutdownNow();cache.clear();}http.close();}
 static Result parse(JsonObject root,UUID expected,int page){
  JsonObject data=root.getAsJsonObject("data"),player=data.getAsJsonObject("player"),counts=data.getAsJsonObject("counts"),pagination=data.getAsJsonObject("pagination"),limits=data.getAsJsonObject("limits"),meta=root.getAsJsonObject("meta");
  Integer next=integer(pagination,"nextPage");if(next!=null&&(next<=page||next>10000))throw new IllegalArgumentException("Invalid next page");
  UUID uuid=MinecraftIdentityResolver.uuid(player.get("uuid").getAsString());if(!"1".equals(meta.get("apiVersion").getAsString())||!expected.equals(uuid)||pagination.get("page").getAsInt()!=page)throw new IllegalArgumentException("Identity/page mismatch");
  return new Result(uuid,nullable(player,"username"),player.get("nameUnavailable").getAsBoolean(),safeUrl(player.get("profileUrl").getAsString()),nonnegative(counts,"active"),nonnegative(counts,"past"),records(data.getAsJsonArray("active"),100),records(data.getAsJsonArray("past"),50),page,integer(pagination,"nextPage"),limits.get("activeTruncated").getAsBoolean(),limits.get("pastTruncated").getAsBoolean(),limits.get("hasMorePast").getAsBoolean(),limits.get("beyondPageLimit").getAsBoolean(),data.get("sample").getAsBoolean(),text(data,"notice"),text(meta,"requestId"),nullable(meta,"respondedAt"));
 }
 private static List<Record> records(JsonArray rows,int max){if(rows.size()>max)throw new IllegalArgumentException("Too many records");List<Record> result=new ArrayList<>();for(JsonElement e:rows){JsonObject r=e.getAsJsonObject();List<String> categories=new ArrayList<>();for(JsonElement c:r.getAsJsonArray("categories"))categories.add(c.getAsString());String flag=r.get("verification").getAsString();if(!Set.of("needs_review","no_review_flag").contains(flag))throw new IllegalArgumentException("Invalid review flag");result.add(new Record(text(r,"community"),text(r,"reason"),List.copyOf(categories),integer(r,"durationDays"),nullable(r,"restrictedAt"),nullable(r,"expiresAt"),nullable(r,"archivedAt"),flag,safeUrl(nullable(r,"sourceUrl"))));}return List.copyOf(result);}
 private static int nonnegative(JsonObject o,String key){int value=o.get(key).getAsInt();if(value<0)throw new IllegalArgumentException("Invalid count");return value;}
 private static Integer integer(JsonObject o,String key){return !o.has(key)||o.get(key).isJsonNull()?null:o.get(key).getAsInt();}
 private static String nullable(JsonObject o,String key){return o.has(key)&&!o.get(key).isJsonNull()?o.get(key).getAsString():null;}
 private static String text(JsonObject o,String key){String value=nullable(o,key);return value==null?"":value;}
 public static String safeUrl(String raw){if(raw==null)return null;URI uri=URI.create(raw);if(!"https".equals(uri.getScheme())||uri.getHost()==null||uri.getUserInfo()!=null)throw new IllegalArgumentException("Invalid source URL");return uri.toString();}
}
