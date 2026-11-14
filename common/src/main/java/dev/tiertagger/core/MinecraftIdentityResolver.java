package dev.tiertagger.core;

import com.google.gson.*;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

/** Resolves premium identities once for both rank lookup and restriction checks. */
public final class MinecraftIdentityResolver implements AutoCloseable {
 public record Identity(UUID uuid,String username) {}
 private record Cached(Identity identity,long expires) {}
 private final URI endpoint;
 private long nextRequestAt;
 private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).build();
 private final Map<String,Cached> cache=new ConcurrentHashMap<>();
 private final Map<String,CompletableFuture<Identity>> pending=new HashMap<>();
 private final ExecutorService worker=new ThreadPoolExecutor(1,1,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(128),r->{Thread t=new Thread(r,"premium-identity");t.setDaemon(true);return t;});
 public MinecraftIdentityResolver(){this(URI.create("https://api.mojang.com/users/profiles/minecraft/"));}
 public MinecraftIdentityResolver(URI endpoint){this.endpoint=endpoint;}
 public static UUID uuid(String raw){String id=raw.replace("-","");if(!id.matches("[a-fA-F0-9]{32}"))throw new IllegalArgumentException("Invalid UUID");return UUID.fromString(id.substring(0,8)+"-"+id.substring(8,12)+"-"+id.substring(12,16)+"-"+id.substring(16,20)+"-"+id.substring(20));}
 public synchronized CompletableFuture<Identity> resolve(String username){
  if(username==null||!username.matches("[A-Za-z0-9_]{3,16}"))return CompletableFuture.failedFuture(new IllegalArgumentException("Enter a Minecraft username (3-16 letters, numbers or underscores)"));
  String key=username.toLowerCase(Locale.ROOT);Cached old=cache.get(key);if(old!=null&&old.expires>System.currentTimeMillis())return CompletableFuture.completedFuture(old.identity);
  var existing=pending.get(key);if(existing!=null)return existing.copy();
  CompletableFuture<Identity> result=new CompletableFuture<>();pending.put(key,result);
  try{worker.execute(()->{try{
   long wait=nextRequestAt-System.currentTimeMillis();if(wait>0)Thread.sleep(wait);nextRequestAt=System.currentTimeMillis()+1000;
   var response=http.send(HttpRequest.newBuilder(endpoint.resolve(username)).timeout(Duration.ofSeconds(8)).GET().build(),HttpResponse.BodyHandlers.ofString());
   if(response.statusCode()==429){long seconds=RetryAfter.seconds(response.headers().firstValue("Retry-After").orElse("60"),60);nextRequestAt=System.currentTimeMillis()+Math.min(86400,seconds)*1000;throw new IllegalStateException("Minecraft account service rate limited");}
   if(response.statusCode()==204||response.statusCode()==404)throw new IllegalArgumentException("Premium Minecraft account not found");
   if(response.statusCode()!=200)throw new IllegalStateException("Minecraft account service unavailable");
   JsonObject json=JsonParser.parseString(response.body()).getAsJsonObject();String name=json.get("name").getAsString();if(!username.equalsIgnoreCase(name))throw new IllegalStateException("Minecraft identity did not match the requested username");
   Identity identity=new Identity(uuid(json.get("id").getAsString()),name);cache.put(key,new Cached(identity,System.currentTimeMillis()+300_000));result.complete(identity);
  }catch(Exception e){result.completeExceptionally(e);}finally{synchronized(this){pending.remove(key);}}});}catch(RejectedExecutionException e){pending.remove(key);result.completeExceptionally(new IllegalStateException("Identity lookup queue is busy"));}
  return result.copy();
 }
 public synchronized void close(){pending.values().forEach(f->f.cancel(false));pending.clear();worker.shutdownNow();cache.clear();}
}
