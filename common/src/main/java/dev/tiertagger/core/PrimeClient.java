package dev.tiertagger.core;

import com.google.gson.*;
import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;

/** PrimeTiers uses premium usernames; its ranks do not retain account UUIDs. */
public final class PrimeClient extends BatchedRankingProvider {
 private final Function<String,CompletableFuture<MinecraftIdentityResolver.Identity>> resolve;
 private final Map<UUID,String> names=new LinkedHashMap<>();
 private Map<UUID,String> batchNames=Map.of();

 public PrimeClient(MinecraftIdentityResolver identities){this(URI.create("https://primetiers.net/api/players"),identities::resolve);}
 PrimeClient(URI endpoint,Function<String,CompletableFuture<MinecraftIdentityResolver.Identity>> resolve){super("prime",endpoint);this.resolve=resolve;}
 @Override protected int batchSize(){return 50;}
 @Override public synchronized void identify(UUID id,String name){
  if(!PlayerLookup.supported(id)||name==null||!name.matches("[A-Za-z0-9_]{3,16}"))return;
  if(names.size()>=4096&&!names.containsKey(id))names.remove(names.keySet().iterator().next());
  names.put(id,name);
 }
 @Override protected synchronized boolean eligible(UUID id){return names.containsKey(id);}
 @Override protected synchronized void onClear(){names.clear();}
 @Override protected HttpRequest buildRequest(List<UUID> batch){
  Map<UUID,String> requested=new LinkedHashMap<>();
  synchronized(this){for(UUID id:batch){String name=names.get(id);if(name==null)throw new IllegalStateException("Player name unavailable");requested.put(id,name);}}
  batchNames=Map.copyOf(requested);
  String query=URLEncoder.encode(String.join(",",requested.values()),java.nio.charset.StandardCharsets.UTF_8);
  return HttpRequest.newBuilder(URI.create(endpoint+"?names="+query)).timeout(Duration.ofSeconds(10)).header("Accept","application/json").header("User-Agent","GlobalTierTagger/1.0.3").GET().build();
 }
 @Override protected BatchResult parseBatch(String json,List<UUID> batch)throws Exception {
  Map<UUID,String> requested=batchNames;
  JsonObject root=JsonParser.parseString(json).getAsJsonObject();
  JsonArray items=root.getAsJsonArray("items"),missing=root.getAsJsonArray("missing");
  if(items==null||missing==null||!root.has("count")||root.get("count").getAsInt()!=items.size())throw new IllegalArgumentException("Invalid PrimeTiers response");
  Set<String> expected=new HashSet<>();requested.values().forEach(n->expected.add(n.toLowerCase(Locale.ROOT)));
  Map<String,JsonObject> players=new HashMap<>();Set<String> absent=new HashSet<>();
  for(JsonElement item:items){JsonObject player=item.getAsJsonObject();String name=player.get("username").getAsString().toLowerCase(Locale.ROOT);if(!expected.contains(name)||players.putIfAbsent(name,player)!=null)throw new IllegalArgumentException("Unexpected or duplicate player");}
  for(JsonElement item:missing){String name=item.getAsString().toLowerCase(Locale.ROOT);if(!expected.contains(name)||players.containsKey(name)||!absent.add(name))throw new IllegalArgumentException("Invalid missing player");}
  Map<UUID,Map<String,String>> values=new HashMap<>();Map<UUID,PlayerLookup> errors=new HashMap<>();
  for(UUID id:batch){checkActive();String name=requested.get(id);String key=name.toLowerCase(Locale.ROOT);
   try{
    JsonObject player=players.get(key);
    if(player==null){if(!absent.contains(key))throw new IllegalArgumentException("Incomplete player response");values.put(id,Map.of());continue;}
    if(!player.has("platform")||!"premium".equalsIgnoreCase(player.get("platform").getAsString())){values.put(id,Map.of());continue;}
    var identity=resolve.apply(name).get(12,TimeUnit.SECONDS);
    if(!id.equals(identity.uuid())||!name.equalsIgnoreCase(identity.username()))throw new IllegalArgumentException("Premium player UUID mismatch");
    values.put(id,ProviderParsers.prime(player.toString(),name));
   }catch(InterruptedException|CancellationException e){throw e;}
   catch(Exception e){errors.put(id,new PlayerLookup(PlayerLookup.Phase.FAILED,"PrimeTiers identity or player response could not be verified",System.currentTimeMillis()+60_000));}
  }
  return new BatchResult(Map.copyOf(values),Map.copyOf(errors),300_000);
 }
}
