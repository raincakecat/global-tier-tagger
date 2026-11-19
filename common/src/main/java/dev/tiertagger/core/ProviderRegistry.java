package dev.tiertagger.core;
import java.util.*;
public final class ProviderRegistry implements AutoCloseable {
 private final Map<String,RankingProvider> providers;
 public ProviderRegistry(Collection<? extends RankingProvider> implementations){Map<String,RankingProvider> entries=new LinkedHashMap<>();for(RankingProvider p:implementations)if(entries.putIfAbsent(p.id(),p)!=null)throw new IllegalArgumentException("Duplicate provider: "+p.id());providers=Map.copyOf(entries);}
 public Optional<String> tier(String provider,UUID player,String mode){RankingProvider p=providers.get(provider);return p==null?Optional.empty():p.tier(player,mode);}
 public void request(String provider,UUID player){RankingProvider p=providers.get(provider);if(p!=null)p.request(player);}
 public void request(String provider,UUID player,String name){RankingProvider p=providers.get(provider);if(p!=null){p.identify(player,name);p.request(player);}}
 public void retry(String id,UUID player,String name){var p=providers.get(id);if(p!=null&&System.currentTimeMillis()>=p.retryAtMillis()){p.identify(player,name);if(p.lookup(player).failed())p.requestForeground(player);else p.requestRetry(player);}}
 public boolean resolved(String provider,UUID id){RankingProvider p=providers.get(provider);return p!=null&&p.resolved(id);}
 public PlayerLookup lookup(String provider,UUID id){var p=providers.get(provider);return p==null?new PlayerLookup(PlayerLookup.Phase.FAILED,"Provider unavailable",0):p.lookup(id);}
 public void requestForeground(String provider,UUID player,String name){var p=providers.get(provider);if(p!=null){p.identify(player,name);p.requestForeground(player);}}
 public long retryAtMillis(String id){var p=providers.get(id);return p==null?0:p.retryAtMillis();}
 public long lastSuccessMillis(String id){var p=providers.get(id);return p==null?0:p.lastSuccessMillis();}
 public void suspend(){providers.values().forEach(RankingProvider::suspend);}
 public void retainBackground(Set<String> selected){providers.forEach((id,p)->{if(!selected.contains(id))p.suspend();});}
 public void requestBackground(String provider,UUID id,String name,boolean nearby){var p=providers.get(provider);if(p!=null){p.identify(id,name);p.requestBackground(id,nearby);}}
 public void clear(){providers.values().forEach(RankingProvider::clear);}
 public String status(String provider){RankingProvider p=providers.get(provider);return p==null?"Provider unavailable":p.status();}
 public ProviderStatus connection(String id){RankingProvider p=providers.get(id);return p==null?new ProviderStatus(ProviderStatus.State.ERROR,"Provider unavailable"):p.connection();}
 public void close(){providers.values().forEach(RankingProvider::close);}
}
