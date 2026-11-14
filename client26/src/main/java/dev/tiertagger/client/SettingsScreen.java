package dev.tiertagger.client;
import net.minecraft.client.gui.GuiGraphicsExtractor;import net.minecraft.client.gui.screens.Screen;import net.minecraft.network.chat.Component;
public final class SettingsScreen extends BaseSettingsScreen {
 public SettingsScreen(Screen parent){super(parent);}
 @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float delta){
  UiDraw.panel(g::fill,x-7,y-4,w+14,h+8,5,UiTheme.FRAME_BORDER,UiTheme.FRAME,UiTheme.FRAME_BOTTOM);
  UiDraw.panel(g::fill,x-3,bodyTop-3,w+6,bodyBottom-bodyTop+6,4,UiTheme.CONTENT_BORDER,UiTheme.CONTENT,UiTheme.CONTENT_BOTTOM);
  for(Panel p:panels)UiDraw.panel(g::fill,p.x(),p.y(),p.width(),p.height(),3,0xff4a3037,0xff191317,0xff0c0a0c);
  if(page==0){g.enableScissor(previewX+2,previewY+27,previewX+previewW-2,previewY+previewH-26);var state=playerPreview();if(state!=null){var rotation=new org.joml.Quaternionf().rotateZ((float)Math.PI).rotateX((float)Math.toRadians(previewPitch));float scale=previewModelScale();g.entity(state,scale,new org.joml.Vector3f(0,state.boundingBoxHeight/2,0),rotation,new org.joml.Quaternionf(),previewX+5,previewY+29,previewX+previewW-5,previewY+previewH-28);}g.disableScissor();}
  super.extractRenderState(g,mx,my,delta);
  g.centeredText(font,Component.literal("Global Tier Tagger").withStyle(net.minecraft.ChatFormatting.BOLD),x+w/2,y+7,UiTheme.TEXT);
  g.enableScissor(x,bodyTop,x+w,bodyBottom);
  for(Label label:drawLabels()){g.enableScissor(label.x(),label.y(),Math.min(x+w,label.x()+label.maxWidth()),Math.min(bodyBottom,label.y()+10));g.text(font,label.text(),label.x(),label.y(),UiTheme.TEXT);g.disableScissor();}
  g.disableScissor();
  if(page==0){g.enableScissor(previewX+2,previewY+2,previewX+previewW-2,previewY+previewH-2);fitPreview(g,previewComponent(false),previewNameY());fitPreview(g,Component.literal("Tab: ").append(previewComponent(true)),previewY+previewH-12);g.disableScissor();}
  drawDiscordScrollbar(g::fill);
 }
 private void fitPreview(GuiGraphicsExtractor g,Component text,int top){float scale=fittedPreviewScale(text);g.pose().pushMatrix();g.pose().translate(previewX+previewW/2f-font.width(text)*scale/2f,top);g.pose().scale(scale,scale);g.text(font,text,0,0,UiTheme.TEXT);g.pose().popMatrix();}
}
