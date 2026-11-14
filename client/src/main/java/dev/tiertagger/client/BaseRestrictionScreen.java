package dev.tiertagger.client;

import dev.tiertagger.core.RestrictionClient;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import java.util.*;
import java.util.concurrent.*;

/** Contained, wrapped scrolling for restriction records and online scan results. */
public abstract class BaseRestrictionScreen extends Screen {
 protected record Row(FormattedCharSequence text,String url,RestrictionClient.Result details) {}
 protected final List<Row> rows=new ArrayList<>();
 protected int left,top,boxWidth,boxHeight,viewTop,viewBottom,offset;
 private final Screen parent;private String username="",message="";private RestrictionClient.Result result;
 private CompletableFuture<RestrictionClient.Result> historyJob;private boolean pending,closed;private int ticks;
 protected BaseRestrictionScreen(Screen parent,RestrictionClient.Result result){super(Component.literal("Restriction Checker"));this.parent=parent;this.result=result;}
 private Button button(String label,int x,int y,int width,Runnable action){return addRenderableWidget(new StyledButton(x,y,Math.max(1,width),20,Component.literal(label),b->{action.run();},font));}
 @Override protected void init(){
  closed=false;boxWidth=Math.min(520,width-24);boxHeight=Math.min(320,height-16);left=(width-boxWidth)/2;top=(height-boxHeight)/2;viewTop=top+81;viewBottom=top+boxHeight-57;
  button("Back",left+10,top+boxHeight-26,56,this::back);
  button("Support",left+boxWidth-74,top+boxHeight-26,64,()->net.minecraft.client.gui.screens.ConfirmLinkScreen.confirmLinkNow(this,java.net.URI.create("https://discord.gg/j3QxZ7xYR4")));
  if(result==null){EditBox field=new EditBox(font,left+10,top+29,boxWidth-108,20,Component.literal("Minecraft username"));field.setHint(Component.literal("Minecraft username"));field.setMaxLength(16);field.setValue(username);field.setResponder(value->username=value);addRenderableWidget(field);
   button("Check player",left+boxWidth-92,top+29,82,()->{message="";RestrictionChecks.check(username);offset=0;rebuildRows();});
   button("Check online players",left+10,top+54,boxWidth-112,()->{message="";RestrictionChecks.scan();offset=0;rebuildRows();});button("Cancel",left+boxWidth-92,top+54,82,()->{RestrictionChecks.cancelScan();rebuildRows();});
  }else{
   button("Copy profile link",left+10,top+30,(boxWidth-26)/2,()->copy(result.profileUrl()));button("Copy correction link",left+16+(boxWidth-26)/2,top+30,(boxWidth-26)/2,()->copy("https://restricttiers.raincakecat.com/contact"));
   Button previous=button("Previous history",left+10,top+55,(boxWidth-26)/2,()->history(result.page()-1));previous.active=result.page()>1&&!pending;
   Button next=button("Next history",left+16+(boxWidth-26)/2,top+55,(boxWidth-26)/2,()->history(result.nextPage()==null?result.page():result.nextPage()));next.active=result.nextPage()!=null&&!pending;
  }
  button("Scroll up",left+10,top+boxHeight-51,(boxWidth-26)/2,()->offset=Math.max(0,offset-5));button("Scroll down",left+16+(boxWidth-26)/2,top+boxHeight-51,(boxWidth-26)/2,()->offset=Math.min(maxOffset(),offset+5));rebuildRows();
 }
 private void copy(String url){if(url!=null){minecraft.keyboardHandler.setClipboard(url);message="Link copied";rebuildRows();}}
 private void history(int page){if(pending||page<1)return;pending=true;message="Loading history...";clearWidgets();init();historyJob=RestrictionChecks.api.lookup(result.uuid(),page);historyJob.whenComplete((value,error)->minecraft.execute(()->{if(closed)return;pending=false;if(error==null){result=value;message="";offset=0;}else message=RestrictionChecks.error(error);clearWidgets();init();}));}
 private void add(String text){add(text,null,null);}
 private void add(String text,String url,RestrictionClient.Result details){for(var part:font.split(Component.literal(text),Math.max(20,boxWidth-42)))rows.add(new Row(part,url,details));}
 protected void rebuildRows(){
  rows.clear();if(!message.isBlank())add(message);
  if(result==null){add(RestrictionChecks.notice);if(RestrictionChecks.scanning){add("Progress: "+RestrictionChecks.completed()+" / "+RestrictionChecks.entries.size());if(RestrictionChecks.api.cooldownSeconds()>0)add("Paused by API rate limit: "+RestrictionChecks.api.cooldownSeconds()+" seconds");}
   for(var entry:RestrictionChecks.entries){add(entry.name+" | "+entry.message,null,entry.result);if(entry.result!=null){if(entry.result.sample())add("SAMPLE DATA",null,entry.result);if(entry.result.activeTruncated()||entry.result.pastTruncated()||entry.result.hasMorePast()||entry.result.beyondPageLimit())add("Incomplete results/history - click to view",null,entry.result);add("View details",null,entry.result);}}
  }else{
   add((result.username()==null?"Name unavailable":result.username())+" | "+result.uuid());add(result.activeCount()+" active | "+result.pastCount()+" past | History page "+result.page());
   if(result.nameUnavailable())add("Current name unavailable; UUID results shown");if(result.sample())add("SAMPLE DATA");if(!result.notice().isBlank())add(result.notice());
   if(result.activeTruncated())add("Additional active records were omitted by the API");if(result.pastTruncated()||result.hasMorePast())add("History is incomplete on this page; use Next history");if(result.beyondPageLimit())add("History exceeds the accessible page limit");
   if(result.page()==1){add("ACTIVE RECORDS");if(result.activeCount()==0)add("No active records returned");for(var record:result.active())record(record);}
   else add("Active records are shown on history page 1");
   add("PAST RECORDS");if(result.past().isEmpty())add("No past records returned on this page");for(var record:result.past())record(record);
   add("RestrictTiers profile (click to copy)",result.profileUrl(),null);add("Request a correction (click to copy)","https://restricttiers.raincakecat.com/contact",null);
   if(!result.requestId().isBlank())add("Request: "+result.requestId());if(result.respondedAt()!=null)add("Response time: "+result.respondedAt());
  }
  offset=Math.min(offset,maxOffset());
 }
 private void record(RestrictionClient.Record record){add(record.community()+" | "+RestrictionChecks.review(record.verification()));add("Reason: "+record.reason());add("Categories: "+String.join(", ",record.categories()));add("Duration: "+(record.durationDays()==null?"Unknown":record.durationDays()+" days"));add("Restricted: "+date(record.restrictedAt())+" | Expires: "+date(record.expiresAt()));add("Archived: "+date(record.archivedAt()));if(record.sourceUrl()!=null)add("Source (click to copy): "+record.sourceUrl(),record.sourceUrl(),null);add(" ");}
 private static String date(String value){return value==null?"Unknown":value;}
 protected int visibleCount(){return Math.max(1,(viewBottom-viewTop-8)/12);}
 private int maxOffset(){return Math.max(0,rows.size()-visibleCount());}
 @Override public void tick(){super.tick();if(++ticks%5==0)rebuildRows();}
 @Override public boolean mouseScrolled(double mx,double my,double horizontal,double vertical){if(mx>=left&&mx<=left+boxWidth&&my>=viewTop&&my<=viewBottom){offset=Math.max(0,Math.min(maxOffset(),offset-(int)Math.signum(vertical)*3));return true;}return super.mouseScrolled(mx,my,horizontal,vertical);}
 @Override public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event,boolean doubled){if(event.button()==com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT&&event.x()>=left+10&&event.x()<left+boxWidth-10&&event.y()>=viewTop&&event.y()<viewBottom){int index=offset+(int)(event.y()-viewTop-4)/12;if(index>=0&&index<rows.size()){Row row=rows.get(index);if(row.details()!=null){minecraft.setScreenAndShow(new RestrictionScreen(this,row.details()));return true;}if(row.url()!=null){copy(row.url());return true;}}}return super.mouseClicked(event,doubled);}
 private void closePendingHistory(){closed=true;if(historyJob!=null)historyJob.cancel(false);}
 private void back(){closePendingHistory();minecraft.setScreenAndShow(parent);}
 @Override public void onClose(){closePendingHistory();if(parent instanceof BaseSettingsScreen||parent instanceof BaseRestrictionScreen)parent.onClose();else minecraft.setScreenAndShow(parent);}
 @Override public boolean keyPressed(net.minecraft.client.input.KeyEvent event){if(event.key()==com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE){onClose();return true;}if(event.hasControlDown()&&event.key()==com.mojang.blaze3d.platform.InputConstants.KEY_F){for(var child:children())if(child instanceof EditBox box){setFocused(box);return true;}}if(result==null&&(event.key()==com.mojang.blaze3d.platform.InputConstants.KEY_RETURN||event.key()==com.mojang.blaze3d.platform.InputConstants.KEY_NUMPADENTER)){message="";RestrictionChecks.check(username);offset=0;rebuildRows();return true;}return super.keyPressed(event);}
 @Override public boolean isPauseScreen(){return false;}
}
