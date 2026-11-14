package dev.tiertagger.client;

/** Shared rounded geometry, rendered through the version-specific GUI API. */
public final class UiDraw {
 private UiDraw() {}
 @FunctionalInterface public interface Rect {void fill(int x1,int y1,int x2,int y2,int color);}
 public static void rounded(Rect out,int x,int y,int width,int height,int radius,int color){
  if(width<=0||height<=0)return;int r=Math.max(0,Math.min(radius,Math.min(width,height)/2));
  for(int row=0;row<height;row++){int inset=0;if(row<r){double d=r-row-0.5;inset=(int)Math.ceil(r-Math.sqrt(r*r-d*d)-0.5);}else if(row>=height-r){double d=row-(height-r)+0.5;inset=(int)Math.ceil(r-Math.sqrt(r*r-d*d)-0.5);}out.fill(x+inset,y+row,x+width-inset,y+row+1,color);}
 }
 public static void panel(Rect out,int x,int y,int width,int height,int radius,int border,int top,int bottom){
  if((top>>>24)>0)rounded(out,x+1,y+2,width,height,radius,0x28000000);rounded(out,x,y,width,height,radius,border);
  // Draw a gentle vertical surface gradient inside the same rounded boundary.
  for(int row=1;row<height-1;row++){double t=(double)row/height;int color=mix(top,bottom,t);int r=Math.max(0,radius-1),inset=0;int innerH=height-2,iy=row-1;
   if(iy<r){double d=r-iy-0.5;inset=(int)Math.ceil(r-Math.sqrt(r*r-d*d)-0.5);}else if(iy>=innerH-r){double d=iy-(innerH-r)+0.5;inset=(int)Math.ceil(r-Math.sqrt(r*r-d*d)-0.5);}
   out.fill(x+1+inset,y+row,x+width-1-inset,y+row+1,color);
  }
 }
 private static int mix(int a,int b,double t){int result=0;for(int shift:new int[]{0,8,16,24})result|=((int)(((a>>>shift)&255)*(1-t)+((b>>>shift)&255)*t))<<shift;return result;}
}
