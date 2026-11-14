package com.interfacelayout.layout.orbs;

import java.awt.Point;
import java.awt.Rectangle;

public enum EdgeAlignment
{
    LEFT("Align left",-1,0),RIGHT("Align right",1,0),TOP("Align top",0,-1),BOTTOM("Align bottom",0,1),
    TOP_LEFT("Align top left",-1,-1),TOP_RIGHT("Align top right",1,-1),
    BOTTOM_LEFT("Align bottom left",-1,1),BOTTOM_RIGHT("Align bottom right",1,1);

    public final String label;
    private final int horizontal,vertical;
    EdgeAlignment(String label,int horizontal,int vertical) {this.label=label;this.horizontal=horizontal;this.vertical=vertical;}

    public Point place(Rectangle control,Rectangle visible,Point requested,int width,int height)
    {
        int offsetX=visible.x-control.x,offsetY=visible.y-control.y;
        int x=Math.max(0,Math.min(requested.x+offsetX,width-visible.width));
        int y=Math.max(0,Math.min(requested.y+offsetY,height-visible.height));
        if(horizontal!=0)x=horizontal<0?0:Math.max(0,width-visible.width);
        if(vertical!=0)y=vertical<0?0:Math.max(0,height-visible.height);
        return new Point(x-offsetX,y-offsetY);
    }

    static EdgeAlignment parse(String text)
    {
        for(EdgeAlignment edge:values())if(edge.name().equals(text))return edge;
        return null;
    }
}
