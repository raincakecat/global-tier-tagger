package dev.tiertagger.core;
/** Row virtualization keeps offscreen controls out of both rendering and hit testing. */
public final class ScrollWindow {
 private int first,total,visible=1;
 public void layout(int total,int visible){this.total=Math.max(0,total);this.visible=Math.max(1,visible);first(first);}
 public int first(){return first;} public void first(int value){first=Math.clamp(value,0,maximum());}
 public int total(){return total;} public int visible(){return visible;} public int maximum(){return Math.max(0,total-visible);}
 public void wheel(double amount){first(first-(int)Math.signum(amount)*2);}
 public int thumb(int height){return Math.min(height,Math.max(14,height*visible/Math.max(1,total)));}
 public int thumbOffset(int height){return maximum()==0?0:(height-thumb(height))*first/maximum();}
 public void drag(double position,int height){first((int)Math.round((position-thumb(height)/2.0)*maximum()/Math.max(1,height-thumb(height))));}
}
