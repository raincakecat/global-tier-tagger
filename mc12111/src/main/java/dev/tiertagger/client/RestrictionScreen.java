package dev.tiertagger.client;
import dev.tiertagger.core.RestrictionClient;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
public final class RestrictionScreen extends BaseRestrictionScreen {
 public RestrictionScreen(Screen parent,RestrictionClient.Result result){super(parent,result);}
 @Override public void render(GuiGraphics g,int mx,int my,float delta){
  UiDraw.panel(g::fill,left,top,boxWidth,boxHeight,5,UiTheme.FRAME_BORDER,UiTheme.FRAME,UiTheme.FRAME_BOTTOM);
  g.drawString(font,Component.literal("Restriction Checker"),left+10,top+12,UiTheme.TEXT);
  UiDraw.panel(g::fill,left+8,viewTop-2,boxWidth-16,viewBottom-viewTop+4,3,UiTheme.CONTENT_BORDER,UiTheme.CONTENT,UiTheme.CONTENT_BOTTOM);
  super.render(g,mx,my,delta);
  g.enableScissor(left+10,viewTop,left+boxWidth-10,viewBottom);
  for(int i=offset;i<rows.size()&&i<offset+visibleCount();i++){Row row=rows.get(i);g.drawString(font,row.text(),left+14,viewTop+4+(i-offset)*12,row.url()!=null||row.details()!=null?0xfff1b7b7:UiTheme.TEXT);}
  g.disableScissor();
 }
}
