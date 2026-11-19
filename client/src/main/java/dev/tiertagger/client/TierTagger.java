package dev.tiertagger.client;
import dev.tiertagger.core.McTiersClient; import dev.tiertagger.core.TierTestsClient; import dev.tiertagger.core.LunarClient; import dev.tiertagger.core.SubTiersClient; import dev.tiertagger.core.ProviderRegistry; import java.util.List; import net.fabricmc.api.ClientModInitializer; import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents; import net.minecraft.client.KeyMapping; import org.slf4j.*;
public final class TierTagger implements ClientModInitializer {
 public static final Logger LOG=LoggerFactory.getLogger("tier-tagger"); public static Preferences config; public static ProviderRegistry ranks; private Object connection; private int ticks; private java.util.Set<String> activeProviders=java.util.Set.of(); private static volatile boolean openRequested;
 @Override public void onInitializeClient(){config=Preferences.load();var providers=new java.util.ArrayList<dev.tiertagger.core.RankingProvider>(List.of(new McTiersClient(),new TierTestsClient(),new LunarClient(),new SubTiersClient(),new dev.tiertagger.core.CollapseClient(),new dev.tiertagger.core.PrimeClient(RestrictionChecks.identities)));for(var p:dev.tiertagger.core.ProviderCatalog.ALL)if(p.supported()&&!java.util.Set.of("mctiers","tiertests","lunar","subtiers","collapse","prime").contains(p.id()))providers.add(new dev.tiertagger.core.AdditionalProvider(p.id()));ranks=new ProviderRegistry(providers);KeyMapping key=Platform.registerKey();NearestProfile.key=Platform.registerNearestProfileKey();
 ClientTickEvents.START_CLIENT_TICK.register(client->{if(NearestProfile.tick(client))openRequested=false;});
 net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback.EVENT.register((dispatcher,access)->{
  var check=com.mojang.brigadier.builder.LiteralArgumentBuilder.<net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource>literal("check")
   .executes(ctx->{ctx.getSource().sendFeedback(net.minecraft.network.chat.Component.literal("Usage: /check <username>"));return 1;})
   .then(com.mojang.brigadier.builder.RequiredArgumentBuilder.<net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource,String>argument("username",com.mojang.brigadier.arguments.StringArgumentType.word()).executes(ctx->{RestrictionChecks.command(com.mojang.brigadier.arguments.StringArgumentType.getString(ctx,"username"),ctx.getSource()::sendFeedback);return 1;}));
  dispatcher.register(check);
  dispatcher.register(com.mojang.brigadier.builder.LiteralArgumentBuilder.<net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource>literal("tiertagger").executes(ctx->{openRequested=true;return 1;}).then(check)
   .then(com.mojang.brigadier.builder.LiteralArgumentBuilder.<net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource>literal("restrictions").executes(ctx->{ctx.getSource().getClient().setScreenAndShow(new RestrictionScreen(null,null));return 1;})
    .then(com.mojang.brigadier.builder.RequiredArgumentBuilder.<net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource,String>argument("uuid",com.mojang.brigadier.arguments.StringArgumentType.word()).executes(ctx->{RestrictionChecks.openDetails(com.mojang.brigadier.arguments.StringArgumentType.getString(ctx,"uuid"));return 1;}))));
 });
 ClientTickEvents.END_CLIENT_TICK.register(client->{
  RestrictionChecks.tick();
  while(key.consumeClick())openRequested=true;
  if(openRequested && Platform.screen(client)==null){openRequested=false;client.setScreenAndShow(new SettingsScreen(null));}
  if(client.getConnection()!=connection){connection=client.getConnection();ranks.suspend();activeProviders=java.util.Set.of();RestrictionChecks.disconnect();}
  if(client.player!=null && !config.setupComplete && Platform.screen(client)==null)client.setScreenAndShow(new SettingsScreen(null));
  if(++ticks>=20){ticks=0;
   java.util.Set<String> selected=new java.util.HashSet<>();
   if(config.enabled&&connection!=null&&client.level!=null)for(var slot:java.util.List.of(config.primary,config.secondary,config.tertiary)){var provider=dev.tiertagger.core.ProviderCatalog.find(slot.provider);if(provider!=null&&provider.supported())selected.add(provider.id());}
   if(!selected.equals(activeProviders)){ranks.retainBackground(selected);activeProviders=java.util.Set.copyOf(selected);}
   if(!selected.isEmpty()){
    var nearby=new java.util.HashSet<java.util.UUID>();if(client.player!=null)for(var p:client.level.players())if(p.distanceToSqr(client.player)<=64*64)nearby.add(p.getUUID());
    for(String provider:selected)client.getConnection().getOnlinePlayers().stream().filter(info->dev.tiertagger.core.PlayerLookup.supported(info.getProfile().id())).forEach(info->ranks.requestBackground(provider,info.getProfile().id(),info.getProfile().name(),nearby.contains(info.getProfile().id())));
   }
  }
 });Runtime.getRuntime().addShutdownHook(new Thread(()->{ranks.close();RestrictionChecks.close();},"tier-tagger-close")); }
}
