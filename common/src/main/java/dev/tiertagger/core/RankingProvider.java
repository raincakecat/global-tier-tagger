package dev.tiertagger.core;
import java.util.Optional;import java.util.UUID;
/** Provider-specific fetching and naming stay behind this shared interface. */
public interface RankingProvider extends AutoCloseable {
 String id();
 default long retryAtMillis(){return 0;}
 default long lastSuccessMillis(){return 0;}
 default void identify(UUID uuid,String name){}
 default boolean resolved(UUID player){return false;}
 default PlayerLookup lookup(UUID player){if(!PlayerLookup.supported(player))return new PlayerLookup(PlayerLookup.Phase.SKIPPED,"Unsupported Minecraft UUID",0);return resolved(player)?PlayerLookup.of(PlayerLookup.Phase.READY):connection().state()==ProviderStatus.State.ERROR?new PlayerLookup(PlayerLookup.Phase.FAILED,connection().reason(),retryAtMillis()):PlayerLookup.of(PlayerLookup.Phase.NOT_REQUESTED);}
 Optional<String> tier(UUID player,String sharedMode);
 void request(UUID player);
 default void requestBackground(UUID player,boolean nearby){request(player);}
 default void suspend(){clear();}
 default void requestForeground(UUID player){request(player);}
 default void requestRetry(UUID player){request(player);}
 String status();
 default ProviderStatus connection(){return ProviderStatus.fromLegacy(status());}
 void clear();
 @Override void close();
}
