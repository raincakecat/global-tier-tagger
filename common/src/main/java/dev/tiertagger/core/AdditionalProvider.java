package dev.tiertagger.core;
import java.net.URI;
import java.util.*;
import java.util.function.LongSupplier;

/** Individual adapters share request safety; wire schemas stay provider-specific. */
public final class AdditionalProvider extends QueuedRankingProvider {
 private final String id;
 private final URI endpointOverride;
 private final Map<UUID,String> names=Collections.synchronizedMap(new LinkedHashMap<>());
 public AdditionalProvider(String id){this(id,null,System::currentTimeMillis);}
 AdditionalProvider(String id,URI endpoint,LongSupplier clock){super(id,id.equals("nova")?3_600_000:id.equals("crack")?900_000:300_000,id.equals("nova")||id.equals("crack"),clock);this.id=id;endpointOverride=endpoint;}
 public void identify(UUID id,String name){if(!PlayerLookup.supported(id)||name==null||!name.matches("[A-Za-z0-9_]{3,16}"))return;synchronized(names){if(names.size()>=4096&&!names.containsKey(id))names.remove(names.keySet().iterator().next());names.put(id,name);}}
 protected void onClear(){names.clear();}
 protected Map<UUID,Map<String,String>> fetchSnapshot()throws Exception{String url=endpointOverride!=null?endpointOverride.toString():id.equals("nova")?"https://novatiers.com/users":"https://cracktiers.me/api/players";return ProviderParsers.snapshot(id,get(url));}
 protected Map<String,String> fetch(UUID uuid)throws Exception{
  if(!id.equals("nexus")){String json=getProfile(url(uuid));return json==null?Map.of():ProviderParsers.parse(id,json,uuid);}
  Map<String,String> ranks=new HashMap<>();Exception failure=null;
  String[] prefixes={"","mace/","spearmace/","smp/"},modes={"crystal","mace","spear-mace","smp"};
  for(int i=0;i<prefixes.length;i++){
   try{String endpoint=endpointOverride!=null?endpointOverride.resolve(prefixes[i]+"tier/"+uuid).toString():"https://nexustiers.com/"+prefixes[i]+"tier/"+uuid;String json=getProfile(endpoint);if(json!=null){String tier=ProviderParsers.parse("nexus",json,uuid).get("crystal");if(tier!=null)ranks.put(modes[i],tier);}}
   catch(Exception e){if(e instanceof java.util.concurrent.CancellationException||e instanceof InterruptedException)throw e;failure=e;if(e instanceof HttpFailure h&&(h.code==429||h.code>=500))break;}
  }
  if(failure!=null)throw new PartialFailure(ranks,failure);
  return Map.copyOf(ranks);
 }
 private String url(UUID uuid){if(endpointOverride!=null)return endpointOverride.resolve(uuid.toString()).toString();String plain=uuid.toString().replace("-","");return switch(id){case "pvptiers"->"https://pvptiers.com/api/profile/"+plain;case "pvphq"->"https://pvphq.com/api/ranked/"+uuid;case "lucnoxity"->"https://apiv2.luctierlist.com/api/players/"+uuid;case "jptiers"->"https://api.mcpvp.jp/api/v2/profile/"+uuid;case "tiermc"->"https://tiermc.com/api/v2/profile/"+uuid;case "prestige"->"https://yiaeivzhqvgnmsiufzcy.supabase.co/functions/v1/tier-api/players/"+uuid;case "mcpvp"->"https://www.mcpvp.com/tiers/search?version=beta&kit=overall&include_retired=1&q="+java.net.URLEncoder.encode(java.util.Objects.requireNonNull(names.get(uuid),"Player name not yet available"),java.nio.charset.StandardCharsets.UTF_8);default->throw new IllegalArgumentException();};}
}
