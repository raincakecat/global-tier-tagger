package dev.tiertagger.client;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
public final class ScreenTest implements FabricClientGameTest {
 private static void select(ClientGameTestContext context,String name){context.runOnClient(client->{var screen=Platform.screen(client);screen.children().stream().filter(w->w instanceof net.minecraft.client.gui.components.EditBox).map(w->(net.minecraft.client.gui.components.EditBox)w).findFirst().orElseThrow().setValue(name);});context.waitTicks(2);context.clickScreenButton("Select "+name);context.runOnClient(client->Platform.screen(client).children().stream().filter(w->w instanceof net.minecraft.client.gui.components.EditBox).map(w->(net.minecraft.client.gui.components.EditBox)w).findFirst().orElseThrow().setValue(""));context.waitTicks(2);}
 private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
 @Override public void runTest(ClientGameTestContext context){
  context.runOnClient(client->{TierTagger.config=new Preferences();});
  if(net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer("minecraft").orElseThrow().getMetadata().getVersion().getFriendlyString().equals("26.3"))check(java.lang.management.ManagementFactory.getPlatformMXBean(com.sun.management.HotSpotDiagnosticMXBean.class).getVMOption("StackShadowPages").getValue().equals("32"),"Mojang 26.3 native-stack launch requirement applied");

  context.runOnClient(client->check(net.minecraft.network.chat.Component.translatable("key.category.multi_tier_tagger.keys").getString().equals("Global Tier Tagger"),"Controls category translated"));
  context.setScreen(()->new SettingsScreen(null));context.waitTicks(10);context.takeScreenshot("tier-settings");
  context.clickScreenButton("Crystal");
  context.clickScreenButton("Sword");
  context.runOnClient(client->check(TierTagger.config.sharedMode.equals("sword"),"shared mode changes"));
  context.clickScreenButton("McTiers");
  context.waitTicks(5);context.takeScreenshot("tags-picker");
  select(context,"tiertests.com");
  context.runOnClient(client->check(TierTagger.config.primary.provider.equals("tiertests"),"TierTests selectable"));
  select(context,"Lunar Tiers");
  context.runOnClient(client->check(TierTagger.config.primary.provider.equals("lunar"),"Lunar selectable"));
  select(context,"SubTiers");
  context.runOnClient(client->check(TierTagger.config.primary.provider.equals("subtiers"),"SubTiers selectable"));
  select(context,"McTiers");
  select(context,"PvP Tiers");
  context.runOnClient(client->check(TierTagger.config.primary.provider.equals("pvptiers"),"additional provider selectable"));
  select(context,"McTiers");
  context.clickScreenButton("Secondary: Off");
  select(context,"McTiers");
  context.runOnClient(client->check(TierTagger.config.secondary.provider.equals("mctiers"),"secondary enabled"));
  context.clickScreenButton("Status");context.waitTicks(5);context.takeScreenshot("provider-status");
  context.clickScreenButton("Advanced");
  context.clickScreenButton("Secondary: Shared");
  context.clickScreenButton("Crystal");
  context.runOnClient(client->check(TierTagger.config.secondary.modeOverride.equals("crystal"),"secondary override"));
  context.clickScreenButton("Main");context.clickScreenButton("Start playing");
  context.runOnClient(client->check(TierTagger.config.setupComplete,"first run marked complete"));
  context.runOnClient(client->{client.options.renderDistance().set(2);client.options.simulationDistance().set(2);});
  try(var world=context.worldBuilder().create()){
   context.waitFor(client->client.level!=null && client.player!=null);context.waitTicks(20);
   context.runOnClient(client->{
    TierTagger.ranks.close();
    var fixture=new dev.tiertagger.core.RankingProvider(){
     public String id(){return "mctiers";} public java.util.Optional<String> tier(java.util.UUID id,String mode){return mode.equals("sword")?java.util.Optional.of("HT2"):java.util.Optional.empty();}
     public void request(java.util.UUID id){}public String status(){return "MCTiers connected";}public void clear(){}public void close(){}
    };
    var subtiersFixture=new dev.tiertagger.core.RankingProvider(){
     public String id(){return "subtiers";} public java.util.Optional<String> tier(java.util.UUID id,String mode){return mode.equals("minecart")?java.util.Optional.of("HT4"):java.util.Optional.empty();}
     public void request(java.util.UUID id){} public String status(){return "SubTiers connected";} public void clear(){} public void close(){}
    };
    TierTagger.ranks=new dev.tiertagger.core.ProviderRegistry(java.util.List.of(fixture,subtiersFixture));
    TierTagger.config.primary.provider="subtiers";TierTagger.config.sharedMode="minecart";
    var subName=net.minecraft.network.chat.Component.literal("Player");
    check(Tags.decorate(client.player.getUUID(),subName,false).getString().replaceAll("[\\uE000-\\uF8FF] ","").equals("HT4 | Player"),"SubTiers name tag");
    check(Tags.decorate(client.player.getUUID(),subName,true).getString().replaceAll("[\\uE000-\\uF8FF] ","").equals("HT4 | Player"),"SubTiers tab");
    TierTagger.config.primary.provider="mctiers";TierTagger.config.sharedMode="sword";
    var original=net.minecraft.network.chat.Component.literal("Player").withColor(0x123456);
    var rendered=Tags.decorate(client.player.getUUID(),original,false);
    check(rendered.getString().replaceAll("[\\uE000-\\uF8FF] ","").equals("HT2 | Player"),"name tag shows available tier");
    check(Tags.decorate(client.player.getUUID(),original,true).getString().replaceAll("[\\uE000-\\uF8FF] ","").equals("HT2 | Player"),"tab shows primary");
    TierTagger.config.secondary.provider="subtiers";TierTagger.config.secondary.modeOverride=null;
    check(Tags.decorate(client.player.getUUID(),original,false).getString().replaceAll("[\\uE000-\\uF8FF] ","").equals("HT2 | HT4 | Player"),"separate primary and secondary with pipes");
    check(Tags.decorate(client.player.getUUID(),original,true).getString().replaceAll("[\\uE000-\\uF8FF] ","").equals("HT2 | Player"),"secondary never appears in tab");
    TierTagger.config.modeIcons.put("sword","set2");
    check(Icons.icon("sword",TierTagger.config.primary).getString().equals(Icons.sample("sword",1).getString()),"shared icon selection");
    TierTagger.config.primary.iconOverrides.put("sword","set1");
    check(Icons.icon("sword",TierTagger.config.primary).getString().equals(Icons.sample("sword",0).getString()),"advanced icon override");
    TierTagger.config.primary.iconOverrides.clear();TierTagger.config.modeIcons.clear();TierTagger.config.secondary.provider="mctiers";TierTagger.config.secondary.modeOverride="crystal";
    try {
     var method=net.minecraft.client.renderer.entity.EntityRenderer.class.getDeclaredMethod("getNameTag",net.minecraft.world.entity.Entity.class);method.setAccessible(true);
     var renderer=client.getEntityRenderDispatcher().getRenderer(client.player);
     check(((net.minecraft.network.chat.Component)method.invoke(renderer,client.player)).getString().contains("HT2 | "),"name tag mixin");
    }catch(ReflectiveOperationException error){throw new AssertionError(error);}
    check(!client.player.getDisplayName().getString().contains("HT2 | "),"player and chat names untouched");
   });
   context.runOnClient(client->{client.player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.NETHERITE_CHESTPLATE));client.player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND_SWORD));});
   context.setScreen(()->new SettingsScreen(null));context.waitTicks(10);context.takeScreenshot("player-preview");
   context.runOnClient(client->{
    SettingsScreen screen=(SettingsScreen)Platform.screen(client);
    check(screen.playerPreview()!=null,"player render state available");
    client.player.setPose(net.minecraft.world.entity.Pose.SWIMMING);var standing=screen.playerPreview();check(standing.boundingBoxHeight==1.8f&&standing.boundingBoxWidth==0.6f,"preview bounds stay standing while real player swims");check(client.player.getPose()==net.minecraft.world.entity.Pose.SWIMMING,"preview does not mutate actual pose");client.player.setPose(net.minecraft.world.entity.Pose.STANDING);
    var skin=(net.minecraft.client.renderer.entity.state.HumanoidRenderState)screen.playerPreview();check(skin.chestEquipment.isEmpty(),"preview hides armor");check(skin.rightHandItemState.isEmpty()&&skin.leftHandItemState.isEmpty(),"preview hides held items");check(!client.player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST).isEmpty(),"real equipment untouched");
    var click=new net.minecraft.client.input.MouseButtonEvent(screen.previewX+10,screen.previewY+35,new net.minecraft.client.input.MouseButtonInfo(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT,0));
    check(screen.mouseClicked(click,false),"preview receives mouse down");
    check(screen.mouseDragged(click,50,20),"preview receives drag");
    check(screen.previewRotation==-100f && screen.playerPreview().bodyRot==80f && screen.previewPitch==-24f,"drag rotates preview render state");
    check(screen.mouseReleased(click),"preview releases drag");
   });
   context.waitTicks(5);context.takeScreenshot("player-preview-rotated");
   context.runOnClient(client->{SettingsScreen screen=(SettingsScreen)Platform.screen(client);try{var idField=BaseSettingsScreen.class.getDeclaredField("lookupUuid");idField.setAccessible(true);idField.set(screen,client.player.getUUID());var nameField=BaseSettingsScreen.class.getDeclaredField("lookupText");nameField.setAccessible(true);nameField.set(screen,"Player0");}catch(ReflectiveOperationException ex){throw new AssertionError(ex);}screen.page=3;screen.refresh();check(screen.profileRows().size()==2,"lookup omits unranked modes and providers");});
   context.waitTicks(5);context.takeScreenshot("lookup-profile");context.clickScreenButton("Group: Tier list");context.waitTicks(3);context.takeScreenshot("lookup-grouped-by-mode");context.runOnClient(client->{SettingsScreen screen=(SettingsScreen)Platform.screen(client);check(screen.drawLabels().stream().anyMatch(l->l.text().getString().equals("Sword")),"lookup mode header present");});context.clickScreenButton("Group: Game mode");
   context.clickScreenButton("Main");context.runOnClient(client->{TierTagger.config.secondary.provider="mctiers";TierTagger.config.secondary.modeOverride="sword";TierTagger.config.tertiary.provider="mctiers";TierTagger.config.tertiary.modeOverride="sword";SettingsScreen screen=(SettingsScreen)Platform.screen(client);screen.refresh();var tag=screen.previewComponent(false);check(tag.getString().split("HT2",-1).length==4,"three tiers preserved in preview");check(client.font.width(tag)*screen.fittedPreviewScale(tag)<=screen.previewW-9,"whole tag including username fits");check(screen.fittedPreviewScale(net.minecraft.network.chat.Component.literal("HT1 | HT2 | HT3 | LongestUsername16"))<1,"long name fits by scaling");});context.waitTicks(3);context.takeScreenshot("three-tier-preview");context.runOnClient(client->{TierTagger.config.secondary.modeOverride="crystal";TierTagger.config.tertiary.provider="off";});
   context.clickScreenButton("Main");context.clickScreenButton("Tier list Discords");context.waitTicks(3);context.takeScreenshot("discord-directory");
   context.runOnClient(client->{SettingsScreen screen=(SettingsScreen)Platform.screen(client);check(screen.mouseScrolled(screen.x+20,screen.bodyTop+65,0,-1),"Discord directory scrolls");});
   context.clickScreenButton("SubTiers | View Discords");context.waitTicks(3);context.takeScreenshot("discord-invites");
   context.runOnClient(client->{SettingsScreen screen=(SettingsScreen)Platform.screen(client);for(int i=0;i<20;i++)check(screen.mouseScrolled(screen.x+20,screen.bodyTop+65,0,-1),"invite list scrolls");check(screen.children().stream().filter(c->c instanceof net.minecraft.client.gui.components.AbstractWidget).map(c->((net.minecraft.client.gui.components.AbstractWidget)c).getMessage().getString()).anyMatch(n->n.contains("dn9YUKYbKH")),"last SubTiers invite reachable");});context.waitTicks(3);context.takeScreenshot("discord-invites-bottom");context.clickScreenButton("Back");context.clickScreenButton("Back");
   context.clickScreenButton("Icons");context.waitTicks(5);context.takeScreenshot("icons");
   context.runOnClient(client->{SettingsScreen screen=(SettingsScreen)Platform.screen(client);for(int i=0;i<10;i++)check(screen.mouseScrolled(screen.x+10,screen.bodyTop+40,0,-1),"icon list scrolls");check(screen.children().stream().filter(w->w instanceof net.minecraft.client.gui.components.AbstractWidget).map(w->((net.minecraft.client.gui.components.AbstractWidget)w).getMessage().getString()).anyMatch(n->n.equals("Spear Mace set 2")),"last icon mode reachable");});context.waitTicks(3);context.takeScreenshot("icons-bottom");context.clickScreenButton("Back");context.runOnClient(client->check(((SettingsScreen)Platform.screen(client)).page==0,"Back returns to preceding Main destination"));
   context.clickScreenButton("Main");context.clickScreenButton("Sword");context.waitTicks(3);context.takeScreenshot("expanded-game-modes");context.clickScreenButton("Main");
   context.runOnClient(client->{SettingsScreen screen=(SettingsScreen)Platform.screen(client);screen.resize(320,180);for(var widget:screen.children())if(widget instanceof net.minecraft.client.gui.components.AbstractWidget control)check(control.getX()>=0&&control.getY()>=0&&control.getRight()<=screen.width&&control.getBottom()<=screen.height,"small menu controls remain inside viewport");check(screen.previewY+screen.previewH<=screen.bodyBottom,"preview remains contained");check(client.font.width(screen.drawLabels().stream().filter(l->l.text().getString().startsWith("Drag")).findFirst().orElseThrow().text())<=screen.previewW-8,"compact rotation hint fits in full");for(var widget:screen.children())if(widget instanceof net.minecraft.client.gui.components.AbstractWidget control&&control.getY()>=screen.bodyTop&&control.getY()<screen.bodyBottom)check(control.getBottom()<=screen.bodyBottom,"body controls remain inside panel");});context.waitTicks(3);context.takeScreenshot("settings-small");
   context.runOnClient(client->RestrictionScreenTest.scale(client,1));context.waitTicks(3);context.takeScreenshot("settings-scale-one");context.runOnClient(client->RestrictionScreenTest.scale(client,2));context.waitTicks(3);context.takeScreenshot("settings-scale-two");
   context.clickScreenButton("Advanced");context.runOnClient(client->{check(!((SettingsScreen)Platform.screen(client)).hasSubtiers(),"SubTiers preferences hidden without active SubTiers");});
   context.runOnClient(client->{TierTagger.config.primary.provider="subtiers";});context.clickScreenButton("Advanced");context.waitTicks(5);context.takeScreenshot("subtiers-preferences");
   context.clickScreenButton("Preferred mode: Minecart");context.waitTicks(5);context.takeScreenshot("subtiers-modes");
   context.clickScreenButton("Trident");context.runOnClient(client->check(TierTagger.config.preferredSubtiersMode.equals("trident"),"SubTiers preferred mode saved"));
   context.clickScreenButton("Tags");context.runOnClient(client->{SettingsScreen screen=(SettingsScreen)Platform.screen(client);for(int i=0;i<30;i++)screen.mouseScrolled(screen.x+15,screen.bodyTop+55,0,-1);check(screen.children().stream().filter(w->w instanceof net.minecraft.client.gui.components.AbstractWidget).map(w->((net.minecraft.client.gui.components.AbstractWidget)w).getMessage().getString()).anyMatch(n->n.equals("Select Crack Tiers")),"last provider reachable without pagination");});context.waitTicks(3);context.takeScreenshot("tags-bottom");
   context.clickScreenButton("Status");context.runOnClient(client->{SettingsScreen screen=(SettingsScreen)Platform.screen(client);for(int i=0;i<30;i++)screen.mouseScrolled(screen.x+15,screen.bodyTop+55,0,-1);check(screen.children().stream().filter(w->w instanceof net.minecraft.client.gui.components.AbstractWidget).map(w->((net.minecraft.client.gui.components.AbstractWidget)w).getMessage().getString()).anyMatch(n->n.equals("RestrictTiers status")),"restriction connectivity in scrollable Status");for(int i=0;i<30;i++)screen.mouseScrolled(screen.x+15,screen.bodyTop+55,0,1);});context.clickScreenButton("PvP Tiers status");context.runOnClient(client->check(Platform.screen(client) instanceof net.minecraft.client.gui.screens.ConfirmLinkScreen,"error click routes to support confirmation"));context.setScreen(()->new SettingsScreen(null));
   context.clickScreenButton("Main");context.clickScreenButton("Sword");context.runOnClient(client->{SettingsScreen screen=(SettingsScreen)Platform.screen(client);var click=new net.minecraft.client.input.MouseButtonEvent(screen.x+screen.w-4,screen.bodyBottom-1,new net.minecraft.client.input.MouseButtonInfo(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT,0));check(screen.mouseClicked(click,false),"scrollbar receives click");check(screen.mouseDragged(click,0,0),"scrollbar receives drag");screen.mouseReleased(click);check(screen.children().stream().filter(w->w instanceof net.minecraft.client.gui.components.AbstractWidget).map(w->((net.minecraft.client.gui.components.AbstractWidget)w).getMessage().getString()).anyMatch(n->n.equals("Diamond Op")),"last game mode reachable by scrollbar");});context.waitTicks(3);context.takeScreenshot("modes-bottom");
   context.runOnClient(client->{SettingsScreen screen=(SettingsScreen)Platform.screen(client);screen.setFocused(null);check(screen.keyPressed(new net.minecraft.client.input.KeyEvent(com.mojang.blaze3d.platform.InputConstants.KEY_HOME,0,0)),"Home scrolls to first mode");check(screen.keyPressed(new net.minecraft.client.input.KeyEvent(com.mojang.blaze3d.platform.InputConstants.KEY_END,0,0)),"End scrolls to last mode");check(screen.keyPressed(new net.minecraft.client.input.KeyEvent(com.mojang.blaze3d.platform.InputConstants.KEY_PAGEUP,0,0)),"Page Up scrolls list");});
   context.clickScreenButton("Tags");context.runOnClient(client->{SettingsScreen screen=(SettingsScreen)Platform.screen(client);var field=(net.minecraft.client.gui.components.EditBox)screen.children().stream().filter(w->w instanceof net.minecraft.client.gui.components.EditBox).findFirst().orElseThrow();screen.setFocused(field);field.setValue("Lu");screen.tick();check(screen.getFocused() instanceof net.minecraft.client.gui.components.EditBox,"search retains keyboard focus after filtering");var focused=(net.minecraft.client.gui.components.EditBox)screen.getFocused();focused.setCursorPosition(focused.getValue().length());focused.insertText("nar");check(focused.getValue().equals("Lunar"),"typing continues after refresh");});
   context.runOnClient(client->{TierTagger.ranks=new dev.tiertagger.core.ProviderRegistry(java.util.List.of(new dev.tiertagger.core.RankingProvider(){public String id(){return "mctiers";}public java.util.Optional<String> tier(java.util.UUID id,String mode){return java.util.Optional.of("HT3");}public void request(java.util.UUID id){}public String status(){return "Connected";}public boolean resolved(java.util.UUID id){return true;}public void clear(){}public void close(){}}));SettingsScreen screen=(SettingsScreen)Platform.screen(client);try{var id=BaseSettingsScreen.class.getDeclaredField("lookupUuid");id.setAccessible(true);id.set(screen,client.player.getUUID());}catch(Exception ex){throw new AssertionError(ex);}screen.page=3;screen.refresh();check(screen.profileRows().size()==dev.tiertagger.core.GameModes.ALL.size(),"lookup includes every ranked mode");check(screen.drawLabels().stream().filter(l->l.text().getString().equals("McTiers")).count()==1,"provider header grouped once");for(int i=0;i<30;i++)check(screen.mouseScrolled(screen.x+15,screen.bodyTop+80,0,-1),"lookup results scroll");check(screen.drawLabels().stream().anyMatch(l->l.text().getString().contains("Diamond Op")),"last profile result reachable");});context.waitTicks(3);context.takeScreenshot("full-profile-bottom");
   context.clickScreenButton("Info");context.clickScreenButton("Support & mod information");context.runOnClient(client->{var screen=(SettingsScreen)Platform.screen(client);check(screen.page==9,"Info support page opens");check(screen.drawLabels().stream().anyMatch(l->l.text().getString().contains("Global Tier Tagger")),"installed version visible");});context.waitTicks(3);context.takeScreenshot("info-support");context.clickScreenButton("Back");context.clickScreenButton("McTiers");context.waitTicks(3);context.takeScreenshot("info-provider");context.clickScreenButton("Tier list Discords");context.runOnClient(client->check(((SettingsScreen)Platform.screen(client)).page==6,"provider info opens its Discords"));context.clickScreenButton("Back");context.runOnClient(client->check(((SettingsScreen)Platform.screen(client)).page==9,"Back restores provider information"));context.clickScreenButton("Info");context.runOnClient(client->{var screen=(SettingsScreen)Platform.screen(client);for(int i=0;i<25;i++)screen.mouseScrolled(screen.x+10,screen.bodyTop+25,0,-1);check(screen.children().stream().filter(w->w instanceof net.minecraft.client.gui.components.AbstractWidget).map(w->((net.minecraft.client.gui.components.AbstractWidget)w).getMessage().getString()).anyMatch("Crack Tiers"::equals),"last provider info reachable");screen.resize(320,180);check(screen.children().stream().filter(w->w instanceof net.minecraft.client.gui.components.AbstractWidget).map(w->((net.minecraft.client.gui.components.AbstractWidget)w).getMessage().getString()).anyMatch("Info"::equals),"compact Info navigation available");});context.waitTicks(3);context.takeScreenshot("info-compact");
   RestrictionScreenTest.run(context);
   PolishTest.run(context);
   context.setScreen(()->new SettingsScreen(null));context.clickScreenButton("Tags");context.clickScreenButton("Back");context.runOnClient(client->check(((SettingsScreen)Platform.screen(client)).page==0,"Back still navigates to prior page"));
   context.clickScreenButton("Advanced");context.runOnClient(client->{var screen=Platform.screen(client);check(screen.keyPressed(new net.minecraft.client.input.KeyEvent(com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE,0,0)),"Escape handled on nested settings page");check(Platform.screen(client)==null,"Escape exits settings instead of navigating Back");check(TierTagger.config.setupComplete,"Escape saves setup");});
   context.runOnClient(client->{var settings=new SettingsScreen(null);var root=new RestrictionScreen(settings,null);client.setScreenAndShow(new RestrictionScreen(root,null));});context.clickScreenButton("Back");context.runOnClient(client->check(Platform.screen(client) instanceof BaseRestrictionScreen,"restriction Back returns to parent page"));
   context.runOnClient(client->{Platform.screen(client).keyPressed(new net.minecraft.client.input.KeyEvent(com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE,0,0));check(Platform.screen(client)==null,"Escape exits restriction screen and parent settings");var settings=new SettingsScreen(null);var root=new RestrictionScreen(settings,null);client.setScreenAndShow(new RestrictionScreen(root,null));Platform.screen(client).keyPressed(new net.minecraft.client.input.KeyEvent(com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE,0,0));check(Platform.screen(client)==null,"Escape exits nested restriction details in one press");});
  }
 }
}
