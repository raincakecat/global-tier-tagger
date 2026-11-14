package dev.tiertagger.core;
import com.google.gson.*;
import java.net.URI;
import java.util.*;

/** Public UUID profiles using the shared paced queue and per-player outcomes. */
public final class SubTiersClient extends QueuedRankingProvider {
 private final URI base;
 public SubTiersClient(){this(URI.create("https://subtiers.net/api/profile/"));}
 SubTiersClient(URI base){super("subtiers",300_000,false,System::currentTimeMillis);this.base=base;}
 protected String connectedLabel(){return "SubTiers connected";}
 protected Map<String,String> fetch(UUID id)throws Exception{String json=getProfile(base.resolve(id.toString().replace("-","")).toString());return json==null?Map.of():parse(json,id);}
 private static final Map<String,String> MODES=Map.ofEntries(
  Map.entry("minecart","minecart"),Map.entry("dia_crystal","diamond-vanilla"),Map.entry("debuff","debuff"),
  Map.entry("elytra","elytra"),Map.entry("speed","speed"),Map.entry("creeper","creeper"),
  Map.entry("manhunt","manhunt"),Map.entry("dia_smp","diamond-smp"),Map.entry("bow","bow"),
  Map.entry("bed","bed"),Map.entry("og_vanilla","og-vanilla"),Map.entry("trident","trident"));
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
}
