package dev.tiertagger.client;
import dev.tiertagger.core.McTiersClient; import dev.tiertagger.core.TierTestsClient; import dev.tiertagger.core.LunarClient; import dev.tiertagger.core.SubTiersClient; import dev.tiertagger.core.ProviderRegistry; import java.util.List; import net.fabricmc.api.ClientModInitializer; import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents; import net.minecraft.client.KeyMapping; import org.slf4j.*;
public final class TierTagger implements ClientModInitializer {
 public static final Logger LOG=LoggerFactory.getLogger("tier-tagger"); public static Preferences config; public static ProviderRegistry ranks; private Object connection; private int ticks; private static volatile boolean openRequested;
 @Override public void onInitializeClient(){config=Preferences.load();var providers=new java.util.ArrayList<dev.tiertagger.core.RankingProvider>(List.of(new McTiersClient(),new TierTestsClient(),new LunarClient(),new SubTiersClient()));for(var p:dev.tiertagger.core.ProviderCatalog.ALL)if(p.supported()&&!java.util.Set.of("mctiers","tiertests","lunar","subtiers").contains(p.id()))providers.add(new dev.tiertagger.core.AdditionalProvider(p.id()));ranks=new ProviderRegistry(providers);KeyMapping key=Platform.registerKey();NearestProfile.key=Platform.registerNearestProfileKey();
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
  if(client.getConnection()!=connection){connection=client.getConnection();ranks.clear();RestrictionChecks.disconnect();}
  if(client.player!=null && !config.setupComplete && Platform.screen(client)==null)client.setScreenAndShow(new SettingsScreen(null));
  if(++ticks>=20){ticks=0;if(config.enabled && connection!=null && client.level!=null){
   var selected=dev.tiertagger.core.ProviderCatalog.ALL.stream().filter(dev.tiertagger.core.ProviderCatalog.Provider::supported).map(dev.tiertagger.core.ProviderCatalog.Provider::id).toList();
   for(String provider:selected){boolean active=provider.equals(config.primary.provider)||provider.equals(config.secondary.provider)||provider.equals(config.tertiary.provider);if(!active)continue;
    if(!provider.equals("tiertests"))client.getConnection().getOnlinePlayers().stream().filter(info->dev.tiertagger.core.PlayerLookup.supported(info.getProfile().id())).forEach(info->ranks.request(provider,info.getProfile().id(),info.getProfile().name()));
    else client.level.players().stream().filter(player->dev.tiertagger.core.PlayerLookup.supported(player.getUUID())).forEach(player->ranks.request(provider,player.getUUID()));
   }
  }}
 });Runtime.getRuntime().addShutdownHook(new Thread(()->{ranks.close();RestrictionChecks.close();},"tier-tagger-close")); }
}
