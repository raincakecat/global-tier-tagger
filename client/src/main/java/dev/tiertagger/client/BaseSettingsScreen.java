package dev.tiertagger.client;

import dev.tiertagger.core.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.*;
import net.minecraft.network.chat.*;
import java.util.*;

/** Shared menu behavior. Version-specific classes only render Minecraft primitives. */
public abstract class BaseSettingsScreen extends Screen {
 protected record Label(Component text,int x,int y,int maxWidth) {}
 protected record Panel(int x,int y,int width,int height) {}
 protected record ProfileRow(String provider,String mode,String tier) {}
 private record ProfileLine(Component text,String group,boolean heading) {}
 private record Destination(int page,String detail,int modeTarget,String infoProvider) {}
 protected final List<Label> labels=new ArrayList<>();
 protected final List<Panel> panels=new ArrayList<>();
 protected int x,y,w,h,bodyTop,bodyBottom,rowHeight,previewX,previewY,previewW,previewH,page;
 protected float previewRotation,previewPitch;
 protected boolean advanced;
 private final Screen parent;
 private final Deque<Destination> history=new ArrayDeque<>();
 private final Map<String,ScrollWindow> scrolls=new HashMap<>();
 private ScrollWindow scroll;
 private int scrollX,scrollY,scrollW,scrollH,selectedSlot,iconScope,modeTarget=-1,ticks,lookupGeneration;
 private boolean rotating,draggingBar,groupByMode;
 private String filter="",modeFilter="",discordFilter="",lookupText="",lookupStatus="Enter a username",observedSearch="";
 private UUID lookupUuid;
 private String profileName="",actionNotice="";
 private NearestProfile.Target capturedTarget;
 private boolean lookupClosed;
 private Object lookupConnection;
 private java.util.concurrent.CompletableFuture<?> identityJob;
 private final Set<String> collapsedGroups=new HashSet<>();
 private ProviderCatalog.Provider discordDetail;
 private String infoProvider="support";
 protected BaseSettingsScreen(Screen parent){super(Component.literal("Global Tier Tagger"));this.parent=parent;}
 private List<ProviderCatalog.Provider> catalog(){return ProviderCatalog.SUPPORTED;}
 private Preferences.Slot slot(int i){return i==0?TierTagger.config.primary:i==1?TierTagger.config.secondary:TierTagger.config.tertiary;}
 private String slotLabel(int i){return i==0?"Primary":i==1?"Secondary":"Tertiary";}
 protected void refresh(){var focused=getFocused();String message=focused instanceof AbstractWidget widget?widget.getMessage().getString():null;boolean editing=focused instanceof EditBox;int cursor=editing?((EditBox)focused).getCursorPosition():0;clearWidgets();init();if(message!=null)for(var child:children())if(child instanceof AbstractWidget widget&&widget.getMessage().getString().equals(message)&&(editing==(child instanceof EditBox))){setFocused(child);if(child instanceof EditBox field)field.setCursorPosition(Math.min(cursor,field.getValue().length()));break;}}
 private void go(int next){if(next==page)return;history.push(new Destination(page,discordDetail==null?null:discordDetail.id(),modeTarget,infoProvider));page=next;advanced=page==5;}
 private void back(){if(history.isEmpty()){if(page==0)closeMenu();else{page=0;discordDetail=null;}}else{Destination d=history.pop();page=d.page();discordDetail=d.detail()==null?null:ProviderCatalog.find(d.detail());modeTarget=d.modeTarget();infoProvider=d.infoProvider();}advanced=page==5;refresh();}
 private void support(){net.minecraft.client.gui.screens.ConfirmLinkScreen.confirmLinkNow(this,java.net.URI.create("https://discord.gg/j3QxZ7xYR4"));}
 private StyledButton button(String text,int bx,int by,int bw,Runnable action){return addRenderableWidget(new StyledButton(bx,by,Math.max(15,bw),Math.min(18,Math.max(10,rowHeight-1)),Component.literal(text),b->{action.run();TierTagger.config.save();if(Platform.screen(minecraft)==this)refresh();},font));}
 private void label(String text,int lx,int ly,int max){labels.add(new Label(Component.literal(text),lx,ly,max));}
 private void search(int bx,int by,int bw,String value,java.util.function.Consumer<String> changed,String hint){EditBox f=new EditBox(font,bx,by,bw,18,Component.literal(hint));f.setHint(Component.literal(hint));f.setValue(value);f.setResponder(v->{changed.accept(v);if(scroll!=null)scroll.first(0);});addRenderableWidget(f);observedSearch=value;}
 private String scrollKey(){return page+":"+(page==6?(discordDetail==null?"directory":discordDetail.id()):page==7?modeTarget:page==2?iconScope:page==9?infoProvider:page==3?groupByMode:"");}
 private int list(int bx,int by,int bw,int bottom,int total,int row){scrollX=bx;scrollY=by;scrollW=bw;scrollH=Math.max(row,bottom-by);scroll=scrolls.computeIfAbsent(scrollKey(),k->new ScrollWindow());scroll.layout(total,Math.max(1,scrollH/row));return scroll.first();}
 private List<String> modes(){return (modeTarget==3?Icons.FIXED:GameModes.ALL).stream().filter(m->SearchAliases.mode(m,modeFilter)).toList();}
 @Override protected void init(){
  labels.clear();panels.clear();scroll=null;
  Preferences c=TierTagger.config;w=Math.min(520,width-32);h=Math.min(300,height-16);x=(width-w)/2;y=(height-h)/2;bodyTop=y+49;bodyBottom=y+h-33;rowHeight=Math.max(11,Math.min(24,(bodyBottom-bodyTop)/7));
  boolean inlineInfo=w>=448;String[] nav=inlineInfo?new String[]{"Main","Tags","Icons","Lookup","Status","Advanced","Info"}:new String[]{"Main","Tags","Icons","Lookup","Status","Advanced"};int nw=w/nav.length;
  for(int i=0;i<nav.length;i++){int target=i==6?8:i;button(nav[i],x+i*nw,y+25,(i==nav.length-1?w-i*nw:nw)-3,()->go(target)).selected=page==target||(target==8&&page==9);}
  if(!inlineInfo)button("Info",x+(w-60)/2,y+h-23,60,()->go(8)).selected=page==8||page==9;
  button("Back",x,y+h-23,64,this::back);button("Support",x+w-72,y+h-23,72,this::support);
  switch(page){
   case 0 -> main(c);
   case 1 -> tags();
   case 2 -> icons(c);
   case 3 -> lookupPage();
   case 4 -> statuses();
   case 5 -> advanced(c);
   case 7 -> modePicker(c);
   case 8 -> infoDirectory();
   case 9 -> infoDetails();
   case 12 -> profileActions();
   default -> discords();
  }
 }
 private void infoDirectory(){
  label("Provider & support information",x,bodyTop+1,w);int start=list(x,bodyTop+19,w,bodyBottom,catalog().size()+2,23);
  for(int i=start;i<Math.min(scroll.total(),start+scroll.visible());i++){boolean support=i==0,help=i==1;var provider=i<2?null:catalog().get(i-2);button(support?"Support & mod information":help?"Why is a tier missing?":provider.name(),x,scrollY+(i-start)*23,w-12,()->{go(9);infoProvider=support?"support":help?"help":provider.id();});}
 }
 private List<net.minecraft.util.FormattedCharSequence> infoLines(){
  List<String> text=new ArrayList<>();
  if(infoProvider.equals("help")){
   text.add("Why is a tier missing?");text.add("Check that the relevant slot is enabled, with the intended tier list and game mode.");text.add("The player may not have a rank in that mode, or the provider may not offer it. A missing result alone cannot distinguish those cases.");text.add("A failed or pending lookup is not proof the player is unranked. Check Status for the error and retry countdown.");text.add("Tab uses only Primary. Secondary and Tertiary can appear above names.");text.add("Check the Name tags and Tab list toggles in Advanced Options. Missing tiers never add placeholders to in-game names.");text.add("Default P opens the nearest loaded player's full profile. Existing game-mode icon choices are shared unless a slot override is set.");
  }else if(infoProvider.startsWith("report:")){text.add(supportReport(infoProvider.substring(7)));text.add("Copy the report, open the support Discord, and paste it into your support request. Add a screenshot and the steps that caused the issue.");
  }else if(infoProvider.equals("support")){
   var loader=net.fabricmc.loader.api.FabricLoader.getInstance();String version=loader.getModContainer("multi_tier_tagger").orElseThrow().getMetadata().getVersion().getFriendlyString();String mc=loader.getModContainer("minecraft").orElseThrow().getMetadata().getVersion().getFriendlyString();String api=loader.getModContainer("fabric-api").map(m->m.getMetadata().getVersion().getFriendlyString()).orElse("Not detected");
   text.add("Global Tier Tagger "+version);text.add("Minecraft "+mc);text.add("Fabric API "+api);text.add("Support: discord.gg/j3QxZ7xYR4");text.add("When reporting a problem, include your mod and Minecraft versions, affected provider, exact error and a screenshot.");text.add("Primary appears in tab. Primary, secondary and tertiary can appear above names. Missing tiers are hidden.");text.add("Default P opens the nearest loaded player profile; default I opens settings. Both are rebindable in Minecraft Controls.");text.add("Escape closes the menu. Back returns to the previous page.");text.add("This client-side mod is independent of the supported tier-list communities. A provider lookup error does not prove its entire service is offline.");
  }else{text.addAll(ProviderInformation.details(infoProvider));text.add("Shared game-mode icon choices apply across tier lists unless a slot override is set.");text.add("Unranked modes are hidden. A failed lookup is not an unranked result.");}
  List<net.minecraft.util.FormattedCharSequence> lines=new ArrayList<>();for(String paragraph:text){lines.addAll(font.split(Component.literal(paragraph),w-24));lines.add(Component.empty().getVisualOrderText());}return lines;
 }
 private String supportReport(String provider){
  var loader=net.fabricmc.loader.api.FabricLoader.getInstance();return "Global Tier Tagger "+loader.getModContainer("multi_tier_tagger").orElseThrow().getMetadata().getVersion().getFriendlyString()+"\nMinecraft: "+loader.getModContainer("minecraft").orElseThrow().getMetadata().getVersion().getFriendlyString()+"\nFabric API: "+loader.getModContainer("fabric-api").map(m->m.getMetadata().getVersion().getFriendlyString()).orElse("Not detected")+"\nProvider: "+ProviderCatalog.name(provider)+"\nStatus: "+TierTagger.ranks.connection(provider).label()+"\nLast successful request: "+(TierTagger.ranks.lastSuccessMillis(provider)==0?"Not recorded":java.time.Instant.ofEpochMilli(TierTagger.ranks.lastSuccessMillis(provider)))+"\nLocal session requests: "+TrafficStats.snapshot(provider).requests()+"\nResponse body bytes received: "+TrafficStats.snapshot(provider).responseBodyBytes()+"\nRetry available in: "+retrySeconds(provider)+" seconds";
 }
 private void infoDetails(){
  boolean support=infoProvider.equals("support"),help=infoProvider.equals("help"),report=infoProvider.startsWith("report:");label(support?"Support & mod information":help?"Why is a tier missing?":report?"Report a provider problem":ProviderCatalog.name(infoProvider),x,bodyTop+1,w);
  if(report){button(actionNotice.equals("Report copied")?"Report copied":"Copy report",x,bodyTop+16,w/2-3,()->{minecraft.keyboardHandler.setClipboard(supportReport(infoProvider.substring(7)));actionNotice="Report copied";});button("Open support Discord",x+w/2,bodyTop+16,w/2-12,this::support);}
  else if(support||help)button("Open support Discord",x,bodyTop+16,w-12,this::support);
  else{button("Tier list Discords",x,bodyTop+16,w/2-3,()->{go(6);discordDetail=ProviderCatalog.find(infoProvider);});button("Report a problem",x+w/2,bodyTop+16,w/2-12,()->{String id=infoProvider;history.push(new Destination(page,discordDetail==null?null:discordDetail.id(),modeTarget,infoProvider));infoProvider="report:"+id;actionNotice="";});}
  list(x,bodyTop+40,w,bodyBottom,infoLines().size(),13);
 }
 private void profileActions(){
  label("Profile actions",x,bodyTop+1,w);label(actionNotice.isBlank()?"Choose an action":actionNotice,x,bodyTop+14,w);String[] actions={"Copy username","Copy UUID","Expand all","Collapse all"};int start=list(x,bodyTop+30,w,bodyBottom,actions.length,23);
  for(int i=start;i<Math.min(scroll.total(),start+scroll.visible());i++){int action=i;var control=button(actions[i],x,scrollY+(i-start)*23,w-12,()->{if(action==0){minecraft.keyboardHandler.setClipboard(profileName.isBlank()?lookupText:profileName);actionNotice="Username copied";}else if(action==1){minecraft.keyboardHandler.setClipboard(lookupUuid.toString());actionNotice="UUID copied";}else{if(action==2)collapsedGroups.clear();else for(var row:allProfileRows())collapsedGroups.add(groupKey(row));back();}});if(i<2)control.active=lookupUuid!=null;}
 }
 private void main(Preferences c){
  previewX=x+3;previewY=bodyTop+2;previewW=Math.max(90,w/3-8);previewH=bodyBottom-previewY;panels.add(new Panel(previewX,previewY,previewW,previewH));
  int right=previewX+previewW+12,rw=x+w-right,lw=Math.min(76,rw/3),cx=right+lw,cw=rw-lw;
  label("Game mode:",right,bodyTop+5,lw-3);button(GameModes.label(c.sharedMode),cx,bodyTop,cw,()->{modeTarget=-1;go(7);});
  for(int i=0;i<3;i++){int n=i;label(slotLabel(i)+":",right,bodyTop+rowHeight*(i+1)+5,lw-3);button(ProviderCatalog.name(slot(i).provider),cx,bodyTop+rowHeight*(i+1),cw,()->{selectedSlot=n;go(1);}).setTooltip(Tooltip.create(Component.literal(i==0?"Primary appears in tab and above names. Missing tiers: Info > Why is a tier missing?":"Secondary and Tertiary appear above names only.")));}
  button("Advanced Options",right,bodyTop+rowHeight*4,rw/2-2,()->go(5));button("Restriction Checker",right+rw/2+2,bodyTop+rowHeight*4,rw/2-2,()->minecraft.setScreenAndShow(new RestrictionScreen(this,null)));
  button("Tier list Discords",right,bodyTop+rowHeight*5,rw,()->go(6)).outlined=true;
  int doneWidth=Math.min(120,rw);button(c.setupComplete?"Done":"Start playing",right+(rw-doneWidth)/2,bodyTop+rowHeight*6,doneWidth,this::closeMenu);
 }
 private void tags(){
  int left=w*58/100,right=x+left+10,rw=w-left-10;label("Tier list picker",x,bodyTop+1,left);search(x,bodyTop+14,left,filter,v->filter=v,"Search tier lists...");
  var found=catalog().stream().filter(p->SearchAliases.provider(p,filter)).toList();
  boolean off=selectedSlot>0&&filter.isBlank();int start=list(x,bodyTop+37,left,bodyBottom,found.size()+(off?1:0),23);
  for(int index=start;index<Math.min(scroll.total(),start+scroll.visible());index++){
   boolean isOff=off&&index==0;var p=isOff?null:found.get(index-(off?1:0));String name=isOff?"Off":p.name();int py=scrollY+(index-start)*23;
   panels.add(new Panel(x,py,left-12,20));label(name,x+5,py+5,left-86);button("Select "+name,x+left-76,py,62,()->{slot(selectedSlot).provider=isOff?"off":p.id();}).displayLabel="Select";
  }
  for(int i=0;i<3;i++){int n=i;button(slotLabel(i)+": "+ProviderCatalog.name(slot(i).provider),right,bodyTop+14+i*27,rw,()->{selectedSlot=n;}).selected=selectedSlot==i;}
 }
 private void icons(Preferences c){
  label("Game mode icons",x,bodyTop+2,w/2);button("Scope: "+(iconScope==0?"All lists":slotLabel(iconScope-1)),x+w/2,bodyTop,w/2,()->iconScope=(iconScope+1)%4);
  int start=list(x,bodyTop+24,w,bodyBottom,Icons.MAIN.size(),25),usable=w-12;
  for(int i=start;i<Math.min(scroll.total(),start+scroll.visible());i++){
   String mode=Icons.MAIN.get(i);int py=scrollY+(i-start)*25;Preferences.Slot chosen=iconScope==0?null:slot(iconScope-1);
   labels.add(new Label(Component.empty().append(Icons.icon(mode,chosen)).append(Component.literal(" "+GameModes.label(mode))),x+4,py+5,usable/3-8));
   Map<String,String> choices=chosen==null?c.modeIcons:chosen.iconOverrides;int bx=x+usable/3,bw=(usable-usable/3)/3;
   String effective=IconPreferences.selected(mode,c.modeIcons,chosen==null?Map.of():chosen.iconOverrides);
   StyledButton a=button(GameModes.label(mode)+" set 1",bx,py,bw-2,()->choices.put(mode,"set1"));a.displayLabel="Set 1";a.selected=!"set2".equals(effective);
   StyledButton b=button(GameModes.label(mode)+" set 2",bx+bw,py,bw-2,()->choices.put(mode,"set2"));b.displayLabel="Set 2";b.selected="set2".equals(effective);
   button(GameModes.label(mode)+" default",bx+bw*2,py,bw-2,()->choices.remove(mode)).displayLabel=chosen==null?"Default":"Global";
  }
 }
 private boolean compactProfile(){return bodyBottom-bodyTop<110;}
 private int lookupSearchOffset(){return compactProfile()?16:23;}
 private int lookupListOffset(){return compactProfile()?62:78;}
 private void lookupPage(){
  label("Player lookup",x,bodyTop+1,w/3-5);button("Group: "+(groupByMode?"Game mode":"Tier list"),x+w/3,bodyTop,w/3-3,()->groupByMode=!groupByMode);button("Profile actions",x+w*2/3,bodyTop,w-w*2/3,()->{actionNotice="";go(12);});
  search(x,bodyTop+lookupSearchOffset(),w-82,lookupText,v->lookupText=v,"Minecraft username");button("Lookup",x+w-78,bodyTop+lookupSearchOffset(),78,()->{capturedTarget=null;lookup(lookupText,null);});
  list(x,bodyTop+lookupListOffset(),w,bodyBottom,profileLines(allProfileRows()).size(),13);
 }
 private long retrySeconds(String id){return Math.max(0,(TierTagger.ranks.retryAtMillis(id)-System.currentTimeMillis()+999)/1000);}
 private void retryProvider(String id){if(retrySeconds(id)>0)return;UUID target=lookupUuid!=null?lookupUuid:minecraft.player==null?null:minecraft.player.getUUID();String name=lookupUuid!=null?(profileName.isBlank()?lookupText:profileName):minecraft.player==null?"":minecraft.player.getName().getString();if(target!=null)TierTagger.ranks.retry(id,target,name);}
 private void statuses(){
  label("Provider status",x,bodyTop+2,w);int start=list(x,bodyTop+19,w,bodyBottom,catalog().size()+1,36);
  for(int i=start;i<Math.min(scroll.total(),start+scroll.visible());i++){
   int py=scrollY+(i-start)*36;boolean restrictions=i==catalog().size();String id=restrictions?"restrictions":catalog().get(i).id(),name=restrictions?"RestrictTiers":catalog().get(i).name();var providerState=restrictions?ProviderStatus.fromLegacy(RestrictionChecks.api.status()):TierTagger.ranks.connection(id);var playerState=!restrictions&&lookupUuid!=null?TierTagger.ranks.lookup(id,lookupUuid):null;long wait=restrictions?RestrictionChecks.api.cooldownSeconds():Math.max(retrySeconds(id),playerState==null?0:Math.max(0,(playerState.retryAtMillis()-System.currentTimeMillis()+999)/1000));var state=playerState!=null&&playerState.failed()?new ProviderStatus(ProviderStatus.State.ERROR,"Player lookup: "+playerState.reason()):providerState;
   label(name,x+3,py+3,w/3-7);var status=button(name+" status",x+w/3,py,w-w/3-59,()->{if(state.state()==ProviderStatus.State.ERROR)support();else if(restrictions)RestrictionChecks.api.checkStatus();});status.displayLabel=state.label();status.quiet=true;status.setTooltip(Tooltip.create(Component.literal(state.label())));
   var retry=button("Retry "+name,x+w-55,py,43,()->{if(restrictions)RestrictionChecks.api.checkStatus();else retryProvider(id);});retry.displayLabel=wait>0?wait+"s":"Retry";retry.active=wait==0&&state.state()!=ProviderStatus.State.CHECKING&&(restrictions||lookupUuid!=null||minecraft.player!=null);
   long last=restrictions?0:TierTagger.ranks.lastSuccessMillis(id);String detail=wait>0?"Retry available in "+wait+"s":restrictions?"Cached, on-demand checks":last==0?"Last success: not recorded":"Last success: "+Math.max(0,(System.currentTimeMillis()-last)/1000)+"s ago";label(detail,x+3,py+20,w-17);
  }
 }
 private void advanced(Preferences c){
  int rh=Math.max(13,Math.min(25,(bodyBottom-bodyTop)/6));
  for(int i=0;i<3;i++){int n=i;button(slotLabel(i)+": "+(slot(i).modeOverride==null?"Shared":GameModes.label(slot(i).modeOverride)),x,bodyTop+i*rh,w*2/3-3,()->{modeTarget=n;go(7);});button("Reset "+slotLabel(i),x+w*2/3,bodyTop+i*rh,w/3,()->slot(n).modeOverride=null);}
  button("Name tags: "+(c.nametags?"On":"Off"),x,bodyTop+rh*3,w/2-2,()->c.nametags=!c.nametags);button("Tab list: "+(c.tab?"On":"Off"),x+w/2+2,bodyTop+rh*3,w/2-2,()->c.tab=!c.tab);
  if(hasSubtiers()){label("SubTiers preferences",x,bodyTop+rh*4+2,w);button("Preferred mode: "+GameModes.label(c.preferredSubtiersMode),x,bodyTop+rh*5,w,()->{modeTarget=3;go(7);});}else label("Keybind: Minecraft Controls > Global Tier Tagger",x,bodyTop+rh*4+5,w);
 }
 private void modePicker(Preferences c){
  label(modeTarget==3?"Preferred SubTiers game mode":"Choose game mode",x,bodyTop+1,w);search(x,bodyTop+14,w-12,modeFilter,v->modeFilter=v,"Search game modes...");
  var found=modes();int start=list(x,bodyTop+37,w,bodyBottom,(found.size()+1)/2,22),cw=(w-16)/2;
  for(int row=start;row<Math.min(scroll.total(),start+scroll.visible());row++)for(int col=0;col<2;col++){
   int index=row*2+col;if(index>=found.size())continue;String mode=found.get(index);
   button(GameModes.label(mode),x+col*(cw+4),scrollY+(row-start)*22,cw,()->{if(modeTarget==3)c.preferredSubtiersMode=mode;else if(modeTarget>=0)slot(modeTarget).modeOverride=mode;else c.sharedMode=mode;back();});
  }
 }
 private void discords(){
  label(discordDetail==null?"Tier list Discords":discordDetail.name()+" Discords",x,bodyTop+1,w);int usable=w-12;
  if(discordDetail==null){
   search(x,bodyTop+14,usable,discordFilter,v->discordFilter=v,"Search tier lists...");
   var found=catalog().stream().filter(p->SearchAliases.provider(p,discordFilter)).toList();int start=list(x,bodyTop+37,w,bodyBottom,found.size(),23);
   for(int i=start;i<Math.min(scroll.total(),start+scroll.visible());i++){var p=found.get(i);button(p.name()+" | View Discords",x,scrollY+(i-start)*23,usable,()->{history.push(new Destination(page,null,modeTarget,infoProvider));discordDetail=p;});}
  }else{
   if(discordDetail.discords().isEmpty())label("No community Discord link supplied",x,bodyTop+24,usable);
   int start=list(x,bodyTop+22,w,bodyBottom,discordDetail.discords().size(),27);
   for(int i=start;i<Math.min(scroll.total(),start+scroll.visible());i++){String url=discordDetail.discords().get(i);int py=scrollY+(i-start)*27;label(ProviderCatalog.discordLabel(url),x+4,py+5,usable/2-8);
    button("Open "+url,x+usable/2,py,usable/4-2,()->net.minecraft.client.gui.screens.ConfirmLinkScreen.confirmLinkNow(this,java.net.URI.create(url))).displayLabel="Open";
    button("Copy "+url,x+usable*3/4+2,py,usable/4-2,()->minecraft.keyboardHandler.setClipboard(url)).displayLabel="Copy";
   }
  }
 }
 protected boolean hasSubtiers(){return java.util.stream.Stream.of(TierTagger.config.primary,TierTagger.config.secondary,TierTagger.config.tertiary).anyMatch(s->"subtiers".equals(s.provider));}
 private List<ProfileRow> allProfileRows(){
  List<ProfileRow> rows=new ArrayList<>();if(lookupUuid!=null)for(var p:catalog())for(String mode:GameModes.ALL)TierTagger.ranks.tier(p.id(),lookupUuid,mode).ifPresent(t->rows.add(new ProfileRow(p.name(),mode,t)));
  if(groupByMode)rows.sort(Comparator.comparingInt((ProfileRow r)->GameModes.ALL.indexOf(r.mode())));return rows;
 }
 protected int profileCapacity(){return Math.max(1,(bodyBottom-bodyTop-lookupListOffset())/13);}
 protected List<ProfileRow> profileRows(){return allProfileRows();}
 private String groupKey(ProfileRow row){return (groupByMode?"mode:":"provider:")+(groupByMode?row.mode():row.provider());}
 private List<ProfileLine> profileLayout(List<ProfileRow> rows){
  List<ProfileLine> lines=new ArrayList<>();String group=null;
  for(var row:rows){String key=groupKey(row),name=groupByMode?GameModes.label(row.mode()):row.provider();if(!key.equals(group)){group=key;lines.add(new ProfileLine(Component.literal(name+ (collapsedGroups.contains(key)?" (collapsed)":"")).withStyle(net.minecraft.ChatFormatting.BOLD,net.minecraft.ChatFormatting.UNDERLINE),key,true));}
   if(collapsedGroups.contains(key))continue;Component left=groupByMode?Component.literal(row.provider()):Component.empty().append(Icons.icon(row.mode(),null)).append(Component.literal(" "+GameModes.label(row.mode())));lines.add(new ProfileLine(Component.empty().append(left).append(Component.literal("  "+row.tier()).withColor(TierColors.color(row.tier()))),key,false));
  }return lines;
 }
 private List<Component> profileLines(List<ProfileRow> rows){return profileLayout(rows).stream().map(ProfileLine::text).toList();}
 private String previewFeedback(){if(!TierTagger.config.enabled)return previewW<130?"Mod paused":"Tier display disabled";if(!TierTagger.config.nametags&&!TierTagger.config.tab)return previewW<130?"Tags off":"Tags are turned off";if(minecraft.player==null)return "Join a world";if(!previewComponent(false).getString().equals(minecraft.player.getName().getString()))return "";boolean pending=false,error=false;for(var s:new Preferences.Slot[]{TierTagger.config.primary,TierTagger.config.secondary,TierTagger.config.tertiary})if(!s.provider.equals("off")&&!TierTagger.ranks.resolved(s.provider,minecraft.player.getUUID())){pending=true;error|=TierTagger.ranks.lookup(s.provider,minecraft.player.getUUID()).failed();}return error?(previewW<130?"API error":"Lookup error: see Status"):pending?(previewW<130?"Checking":"Checking selected tiers"):(previewW<130?"No rank":"No rank in selected modes");}
 protected List<Label> drawLabels(){
  List<Label> out=new ArrayList<>(labels);
  if(page==0){String feedback=previewFeedback();if(!feedback.isEmpty()){var parts=font.split(Component.literal(feedback),previewW-8);if(!parts.isEmpty()){StringBuilder text=new StringBuilder();parts.getFirst().accept((i,style,c)->{text.appendCodePoint(c);return true;});out.add(new Label(Component.literal(text.toString()),previewX+4,previewY+3,previewW-8));}}out.add(new Label(Component.literal(previewW<130?"Drag / tilt":"Drag to rotate / tilt"),previewX+4,previewY+previewH-23,previewW-8));}
  if(page==9&&scroll!=null){var lines=infoLines();for(int i=scroll.first();i<Math.min(lines.size(),scroll.first()+scroll.visible());i++){var line=lines.get(i);StringBuilder plain=new StringBuilder();line.accept((index,style,code)->{plain.appendCodePoint(code);return true;});out.add(new Label(Component.literal(plain.toString()),x+5,scrollY+(i-scroll.first())*13,w-20));}}
  if(page==3){String header=profileName.isBlank()?lookupText:profileName;if(capturedTarget!=null)header+=" | "+String.format(Locale.ROOT,"%.1f blocks at selection",capturedTarget.distance());out.add(new Label(Component.literal(header),x+4,bodyTop+(compactProfile()?38:47),w-8));out.add(new Label(Component.literal(lookupUuid==null?lookupStatus:profileProgress()),x+4,bodyTop+(compactProfile()?49:60),w-8));var lines=profileLines(allProfileRows());if(scroll!=null){scroll.layout(lines.size(),scroll.visible());for(int i=scroll.first();i<Math.min(lines.size(),scroll.first()+scroll.visible());i++)out.add(new Label(lines.get(i),x+5,scrollY+(i-scroll.first())*13,w-20));}}

  return out;
 }
 protected Component previewComponent(boolean tab){return minecraft.player==null?Component.literal("Player"):Tags.decorate(minecraft.player.getUUID(),minecraft.player.getName(),tab);}
 private String profileProgress(){return LookupProgress.describe(catalog().stream().map(p->TierTagger.ranks.lookup(p.id(),lookupUuid)).toList(),!allProfileRows().isEmpty());}
 protected float fittedPreviewScale(Component text){return Math.min(1f,(previewW-10f)/Math.max(1,font.width(text)));}
 protected float previewModelScale(){return Math.max(8f,Math.min(40f,(previewH-45)/1.95f));}
 protected int previewNameY(){return (int)(previewY+previewH/2f-Math.cos(Math.toRadians(previewPitch))*0.9f*previewModelScale()-12);}
 protected net.minecraft.client.renderer.entity.state.LivingEntityRenderState playerPreview(){if(minecraft.player==null)return null;var raw=minecraft.getEntityRenderDispatcher().getRenderer(minecraft.player).createRenderState(minecraft.player,1.0f);if(!(raw instanceof net.minecraft.client.renderer.entity.state.LivingEntityRenderState state))return null;state.nameTag=null;state.lightCoords=15728880;state.shadowPieces.clear();state.outlineColor=0;state.bodyRot=180+previewRotation;state.yRot=0;state.xRot=0;state.walkAnimationSpeed=0;state.deathTime=0;state.pose=net.minecraft.world.entity.Pose.STANDING;state.headItem.clear();state.wornHeadType=null;state.wornHeadProfile=null;
  if(state instanceof net.minecraft.client.renderer.entity.state.HumanoidRenderState humanoid){humanoid.headEquipment=net.minecraft.world.item.ItemStack.EMPTY;humanoid.chestEquipment=net.minecraft.world.item.ItemStack.EMPTY;humanoid.legsEquipment=net.minecraft.world.item.ItemStack.EMPTY;humanoid.feetEquipment=net.minecraft.world.item.ItemStack.EMPTY;humanoid.rightHandItemState.clear();humanoid.leftHandItemState.clear();humanoid.isUsingItem=false;humanoid.isFallFlying=false;humanoid.isCrouching=false;humanoid.isVisuallySwimming=false;humanoid.rightArmPose=net.minecraft.client.model.HumanoidModel.ArmPose.EMPTY;humanoid.leftArmPose=net.minecraft.client.model.HumanoidModel.ArmPose.EMPTY;}
  if(state instanceof net.minecraft.client.renderer.entity.state.AvatarRenderState avatar){avatar.showCape=false;avatar.arrowCount=0;avatar.stingerCount=0;avatar.parrotOnLeftShoulder=null;avatar.parrotOnRightShoulder=null;avatar.heldOnHead.clear();}state.boundingBoxWidth=0.6f;state.boundingBoxHeight=1.8f;state.scale=1;return state;
 }

 public void openNearestProfile(NearestProfile.Target target){capturedTarget=target;page=3;lookupText=target.username();profileName=target.username();refresh();lookup(target.username(),target.uuid());}
 private void lookup(String name,UUID expected){int generation=++lookupGeneration;lookupClosed=false;lookupConnection=minecraft.getConnection();lookupUuid=null;profileName=name;lookupStatus="Resolving premium account...";collapsedGroups.clear();scrolls.remove("3:false");scrolls.remove("3:true");identityJob=RestrictionChecks.identities.resolve(name).whenComplete((identity,error)->minecraft.execute(()->{if(generation!=lookupGeneration||lookupClosed||lookupConnection!=minecraft.getConnection())return;if(error!=null){lookupStatus=RestrictionChecks.error(error);refresh();return;}if(expected!=null&&!expected.equals(identity.uuid())){lookupStatus="Player UUID does not match premium account";refresh();return;}lookupUuid=identity.uuid();profileName=identity.username();lookupText=identity.username();lookupStatus="Loading; provider details in Status";for(var p:catalog())TierTagger.ranks.requestForeground(p.id(),identity.uuid(),identity.username());refresh();}));}
 protected void drawDiscordScrollbar(UiDraw.Rect out){if(scroll==null||scroll.maximum()==0)return;int thumb=scroll.thumb(scrollH),top=scrollY+scroll.thumbOffset(scrollH);out.fill(scrollX+scrollW-7,scrollY,scrollX+scrollW-2,scrollY+scrollH,0xff37272d);UiDraw.rounded(out,scrollX+scrollW-7,top,5,thumb,2,UiTheme.FOCUS);}
 @Override public boolean mouseScrolled(double mx,double my,double horizontal,double vertical){if(scroll!=null&&mx>=scrollX&&mx<=scrollX+scrollW&&my>=scrollY&&my<=scrollY+scrollH){scroll.wheel(vertical);refresh();return true;}return super.mouseScrolled(mx,my,horizontal,vertical);}
 @Override public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent e,boolean doubled){if(page==3&&scroll!=null&&e.button()==com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT&&e.x()>=scrollX&&e.x()<scrollX+scrollW-10&&e.y()>=scrollY&&e.y()<scrollY+scrollH){var lines=profileLayout(allProfileRows());int index=scroll.first()+(int)(e.y()-scrollY)/13;if(index<lines.size()&&lines.get(index).heading()){String key=lines.get(index).group();if(!collapsedGroups.remove(key))collapsedGroups.add(key);refresh();return true;}}if(scroll!=null&&scroll.maximum()>0&&e.button()==com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT&&e.x()>=scrollX+scrollW-9&&e.x()<=scrollX+scrollW&&e.y()>=scrollY&&e.y()<=scrollY+scrollH){draggingBar=true;scroll.drag(e.y()-scrollY,scrollH);refresh();return true;}if(page==0&&minecraft.player!=null&&e.button()==com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT&&e.x()>=previewX&&e.x()<previewX+previewW&&e.y()>=previewY+10&&e.y()<previewY+previewH-26){rotating=true;return true;}return super.mouseClicked(e,doubled);}
 @Override public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent e,double dx,double dy){if(draggingBar&&scroll!=null){scroll.drag(e.y()-scrollY,scrollH);refresh();return true;}if(rotating){previewRotation=(previewRotation-(float)dx*2)%360;previewPitch=Math.clamp(previewPitch-(float)dy*1.2f,-75,75);return true;}return super.mouseDragged(e,dx,dy);}
 @Override public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent e){if(draggingBar){draggingBar=false;return true;}if(rotating){rotating=false;return true;}return super.mouseReleased(e);}
 @Override public boolean keyPressed(net.minecraft.client.input.KeyEvent event){if(event.key()==com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE){onClose();return true;}if(event.key()==com.mojang.blaze3d.platform.InputConstants.KEY_F&&event.hasControlDown()){for(var child:children())if(child instanceof EditBox box){setFocused(box);box.setCursorPosition(box.getValue().length());return true;}}if(page==3&&(event.key()==com.mojang.blaze3d.platform.InputConstants.KEY_RETURN||event.key()==com.mojang.blaze3d.platform.InputConstants.KEY_NUMPADENTER)){capturedTarget=null;lookup(lookupText,null);return true;}if(scroll!=null&&!(getFocused() instanceof EditBox)){int key=event.key();if(key==com.mojang.blaze3d.platform.InputConstants.KEY_PAGEUP||key==com.mojang.blaze3d.platform.InputConstants.KEY_PAGEDOWN||key==com.mojang.blaze3d.platform.InputConstants.KEY_HOME||key==com.mojang.blaze3d.platform.InputConstants.KEY_END){if(key==com.mojang.blaze3d.platform.InputConstants.KEY_HOME)scroll.first(0);else if(key==com.mojang.blaze3d.platform.InputConstants.KEY_END)scroll.first(scroll.maximum());else scroll.first(scroll.first()+(key==com.mojang.blaze3d.platform.InputConstants.KEY_PAGEUP?-1:1)*scroll.visible());refresh();return true;}}return super.keyPressed(event);}
 @Override public void tick(){String current=page==1?filter:page==7?modeFilter:page==6&&discordDetail==null?discordFilter:observedSearch;if(!current.equals(observedSearch))refresh();if((page==4||page==3)&&++ticks%20==0){if(page==3&&lookupUuid!=null&&!lookupClosed)for(var p:catalog())TierTagger.ranks.requestForeground(p.id(),lookupUuid,profileName);refresh();}}
 private void closeMenu(){lookupClosed=true;lookupGeneration++;if(identityJob!=null)identityJob.cancel(false);TierTagger.config.setupComplete=true;TierTagger.config.save();minecraft.setScreenAndShow(parent);}
 @Override public void onClose(){closeMenu();}
 @Override public boolean isPauseScreen(){return false;}
}
