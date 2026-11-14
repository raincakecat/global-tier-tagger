package dev.tiertagger.core;
import com.google.gson.*;
import java.net.URI;
import java.util.*;

/** TierTests retains its four-hour per-player cache and lower request budget. */
public final class TierTestsClient extends QueuedRankingProvider {
 private final URI endpoint;
 public TierTestsClient(){this(URI.create("https://api.tiertests.com/v1/tiers/current/all?version=MODERN&minecraftUuid="));}
 TierTestsClient(URI endpoint){super("tiertests",4*60*60*1000L,false,System::currentTimeMillis);this.endpoint=endpoint;}
 protected String connectedLabel(){return "TierTests connected";}
 protected Map<String,String> fetch(UUID id)throws Exception{String json=getProfile(endpoint.toString()+id);return json==null?Map.of():parse(json,id);}
 static Map<String,String> parse(String response){return parse(response,null);}
 static Map<String,String> parse(String response,UUID expected){
  JsonObject root=JsonParser.parseString(response).getAsJsonObject();if(!root.has("success")||!root.get("success").getAsBoolean()||!root.has("data")||!root.get("data").isJsonArray())throw new IllegalArgumentException("Invalid TierTests response");
  Map<String,String> result=new HashMap<>();
  for(JsonElement e:root.getAsJsonArray("data")){if(!e.isJsonObject())continue;JsonObject t=e.getAsJsonObject();
   if(!t.has("gamemode")||!t.get("gamemode").isJsonObject()||!t.has("tier")||t.get("tier").isJsonNull())continue;
   if(expected!=null&&(!t.has("user")||!t.get("user").isJsonObject()||!t.getAsJsonObject("user").has("minecraftUUID")||!expected.equals(MinecraftIdentityResolver.uuid(t.getAsJsonObject("user").get("minecraftUUID").getAsString()))))throw new IllegalArgumentException("TierTests profile UUID mismatch");
   JsonObject g=t.getAsJsonObject("gamemode");if(!g.has("name")||g.get("name").isJsonNull())continue;
   String name=g.get("name").getAsString();String mode=switch(name){case "CRYSTAL"->"crystal";case "SWORD"->"sword";case "SMP"->"smp";case "NETH"->"nethpot";case "MACE"->"mace";case "UHC"->"uhc";default->null;};
   String raw=t.get("tier").getAsString();String rank=switch(raw){case "S","F"->raw;case "A_PLUS","B_PLUS","C_PLUS","D_PLUS"->raw.substring(0,1)+"+";case "A_MINUS","B_MINUS","C_MINUS","D_MINUS"->raw.substring(0,1)+"-";default->null;};
   if(mode!=null&&rank!=null)result.put(mode,rank);
  }return Map.copyOf(result);
 }
}
