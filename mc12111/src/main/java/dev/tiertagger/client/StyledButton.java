package dev.tiertagger.client;
import net.minecraft.client.gui.GuiGraphics;import net.minecraft.client.gui.Font;import net.minecraft.client.gui.components.Button;import net.minecraft.network.chat.Component;
public final class StyledButton extends Button {
 private final Font textFont;
 public boolean selected=false,outlined=false,quiet=false;
 public String displayLabel=null;
 public StyledButton(int x,int y,int width,int height,Component label,OnPress action,Font font){super(x,y,width,height,label,action,DEFAULT_NARRATION);textFont=font;}
 @Override protected void renderContents(GuiGraphics g,int mouseX,int mouseY,float delta){
  int x=getX(),y=getY();boolean focus=isHoveredOrFocused();
  boolean action=getMessage().getString().equals("Done") || getMessage().getString().equals("Start playing");
  int border=(selected||outlined)?UiTheme.FOCUS:!active?UiTheme.DIVIDER:focus?UiTheme.FOCUS:action?UiTheme.ACTION:UiTheme.CONTROL_BORDER;
  int surface=selected?UiTheme.HOVER:!active?UiTheme.DISABLED:action?UiTheme.ACTION:focus?UiTheme.HOVER:UiTheme.CONTROL;
  int top=action?0xff983744:focus?0xff302125:0xff23191c;
  int bottom=action?0xff601d29:focus?0xff1b1215:0xff120e10;
  if(!active){top=UiTheme.DISABLED;bottom=UiTheme.DISABLED;}
  if(!quiet)UiDraw.panel(g::fill,x,y,getWidth(),getHeight(),3,border,top,bottom);else if(focus)UiDraw.rounded(g::fill,x,y,getWidth(),getHeight(),3,0xff302125);
  if(selected)g.fill(x+2,y+getHeight()-2,x+getWidth()-2,y+getHeight(),UiTheme.FOCUS);
  Component label=Component.literal(displayLabel==null?getMessage().getString():displayLabel);float scale=Math.min(1f,(getWidth()-8f)/Math.max(1,textFont.width(label)));int color=!active?UiTheme.DISABLED_TEXT:action?UiTheme.ACTION_TEXT:UiTheme.TEXT;
  g.pose().pushMatrix();g.pose().translate(x+getWidth()/2f-textFont.width(label)*scale/2f,y+(getHeight()-8*scale)/2f);g.pose().scale(scale,scale);g.drawString(textFont,label,0,0,color);g.pose().popMatrix();
 }
}
