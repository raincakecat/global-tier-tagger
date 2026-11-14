package dev.tiertagger.core;
import com.google.gson.*;
import java.util.*;
/** Converts MCTiers v2 rankings into shared game mode names. */
public final class McTiersParser {
 private McTiersParser() {}
 public static Map<String,String> parse(String json) {
  JsonObject root=JsonParser.parseString(json).getAsJsonObject(); Map<String,String> result=new HashMap<>();
  for(String mode:GameModes.ALL) { JsonElement e=root.get(GameModes.mctiersKey(mode)); if(e==null || !e.isJsonObject()) continue;
   JsonObject rank=e.getAsJsonObject(); boolean retired=rank.has("retired") && rank.get("retired").getAsBoolean();
   String tier=retired && rank.has("peak_tier") && !rank.get("peak_tier").isJsonNull() ? "peak_tier" : "tier";
   String pos=tier.equals("peak_tier") ? "peak_pos" : "pos";
   if(!rank.has(tier)||rank.get(tier).isJsonNull()||!rank.has(pos)||rank.get(pos).isJsonNull()) continue;
   int t=rank.get(tier).getAsInt(), v=rank.get(pos).getAsInt(); if(t>=1 && t<=5 && (v==0||v==1)) result.put(mode,(v==0?"HT":"LT")+t);
  } return Map.copyOf(result);
 }
}
