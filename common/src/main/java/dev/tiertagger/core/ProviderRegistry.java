package dev.tiertagger.core;
import java.util.*;
public final class ProviderRegistry implements AutoCloseable {
 private final Map<String,RankingProvider> providers;
 public ProviderRegistry(Collection<? extends RankingProvider> implementations){Map<String,RankingProvider> entries=new LinkedHashMap<>();for(RankingProvider p:implementations)if(entries.putIfAbsent(p.id(),p)!=null)throw new IllegalArgumentException("Duplicate provider: "+p.id());providers=Map.copyOf(entries);}
 public Optional<String> tier(String provider,UUID player,String mode){RankingProvider p=providers.get(provider);return p==null?Optional.empty():p.tier(player,mode);}
 public void request(String provider,UUID player){RankingProvider p=providers.get(provider);if(p!=null)p.request(player);}
 public void request(String provider,UUID player,String name){RankingProvider p=providers.get(provider);if(p!=null){p.identify(player,name);p.request(player);}}
 public void retry(String id,UUID player,String name){var p=providers.get(id);if(p!=null&&System.currentTimeMillis()>=p.retryAtMillis()){p.identify(player,name);p.requestRetry(player);}}
 public boolean resolved(String provider,UUID id){RankingProvider p=providers.get(provider);return p!=null&&p.resolved(id);}
 public long retryAtMillis(String id){var p=providers.get(id);return p==null?0:p.retryAtMillis();}
 public long lastSuccessMillis(String id){var p=providers.get(id);return p==null?0:p.lastSuccessMillis();}
 public void clear(){providers.values().forEach(RankingProvider::clear);}
 public String status(String provider){RankingProvider p=providers.get(provider);return p==null?"Provider unavailable":p.status();}
 public ProviderStatus connection(String id){RankingProvider p=providers.get(id);return p==null?new ProviderStatus(ProviderStatus.State.ERROR,"Provider unavailable"):p.connection();}
 public void close(){providers.values().forEach(RankingProvider::close);}
}
