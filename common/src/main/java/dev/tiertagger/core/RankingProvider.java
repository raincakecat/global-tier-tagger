package dev.tiertagger.core;
import java.util.Optional;import java.util.UUID;
/** Provider-specific fetching and naming stay behind this shared interface. */
public interface RankingProvider extends AutoCloseable {
 String id();
 default long retryAtMillis(){return 0;}
 default long lastSuccessMillis(){return 0;}
 default void identify(UUID uuid,String name){}
 default boolean resolved(UUID player){return false;}
 Optional<String> tier(UUID player,String sharedMode);
 void request(UUID player);
 default void requestRetry(UUID player){request(player);}
 String status();
 default ProviderStatus connection(){return ProviderStatus.fromLegacy(status());}
 void clear();
 @Override void close();
}
