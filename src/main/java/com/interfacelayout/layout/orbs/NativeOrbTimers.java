package com.interfacelayout.layout.orbs;

import com.interfacelayout.InterfaceLayoutConfig;
import com.interfacelayout.InterfaceLayoutConfig.NativeTimerStyle;
import com.interfacelayout.InterfaceLayoutConfig.PrayerDisplay;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Rectangle2D;
import net.runelite.api.gameval.InterfaceID;

final class NativeOrbTimers
{
    private static final Color AMOUNT=new Color(125,219,231);
    private static final Color COUNTDOWN=new Color(240,238,227);
    private static final Color TRACK=new Color(28,32,35,200);

    static void paint(Graphics2D graphics,Rectangle inset,Rectangle number,int component,
        InterfaceLayoutConfig config,Color color,double progress,int current,int maximum)
    {
        Rectangle meter=new Rectangle(number.x+3,number.y+number.height-4,
            Math.max(0,Math.min(number.width-6,inset.x-7-number.x)),number.height<6?0:2);
        paintStyle(graphics,inset,meter,component,config,color,progress,current,maximum);
    }

    static void paintCircular(Graphics2D graphics,Rectangle circle,int component,
        InterfaceLayoutConfig config,Color color,double progress,int current,int maximum)
    {
        Rectangle inset=new Rectangle(circle);inset.grow(-5,-5);
        Rectangle meter=new Rectangle(circle.x+6,circle.y+circle.height+2,circle.width-12,2);
        paintStyle(graphics,inset,meter,component,config,color,progress,current,maximum);
    }

    private static void paintStyle(Graphics2D graphics,Rectangle inset,Rectangle meter,int component,
        InterfaceLayoutConfig config,Color color,double progress,int current,int maximum)
    {
        if(inset.width<8 || inset.height<8 || !Double.isFinite(progress))return;
        NativeTimerStyle style=OrbTimerStyles.resolve(config,component);
        boolean prayer=component==InterfaceID.Orbs.ORB_PRAYER;
        boolean dual=style==NativeTimerStyle.TAG_METERS || style==NativeTimerStyle.EIGHT_SLICES;
        PrayerDisplay display=config.prayerOrbDisplay();
        if(display==null)display=PrayerDisplay.BOTH;
        boolean amount=prayer && dual && display!=PrayerDisplay.DRAIN && maximum>0;
        boolean drain=!prayer || !dual || display!=PrayerDisplay.AMOUNT;
        double value=Math.max(0,Math.min(1,maximum>0?current/(double)maximum:0));
        double radius=Math.min(inset.width,inset.height)/2.0;
        Graphics2D draw=(Graphics2D)graphics.create();
        try {
            draw.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            draw.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,RenderingHints.VALUE_STROKE_PURE);
            if(prayer) {
                if(amount) {
                    if(style==NativeTimerStyle.TAG_METERS)meter(draw,meter,AMOUNT,value);
                    else {
                        Graphics2D amountDraw=(Graphics2D)draw.create();
                        try {
                            amountDraw.clip(new Ellipse2D.Double(inset.getCenterX()-radius-1.8,inset.getCenterY()-radius-1.8,
                                2*(radius+1.8),2*(radius+1.8)));
                            segments(amountDraw,inset,radius+1.3,AMOUNT,value,true);
                        } finally {amountDraw.dispose();}
                    }
                }
                if(drain && progress>0) {
                    boolean split=amount && style==NativeTimerStyle.EIGHT_SLICES;
                    if(split) {
                        java.awt.geom.Area outside=new java.awt.geom.Area(new Rectangle(inset.x-5,inset.y-5,inset.width+10,inset.height+10));
                        outside.subtract(new java.awt.geom.Area(new Ellipse2D.Double(inset.getCenterX()-radius-1.8,
                            inset.getCenterY()-radius-1.8,2*(radius+1.8),2*(radius+1.8))));draw.clip(outside);
                    }
                    ring(draw,inset,radius+(split?2.3:2),amount?COUNTDOWN:color,progress,split?.7f:1.2f);
                }
                return;
            }
            if(progress<=0)return;
            switch(style) {
                case TAG_METERS: meter(draw,meter,color,progress);break;
                case QUIET_RIMS: ring(draw,inset,radius+2,color,progress,1.2f);break;
                case EIGHT_SLICES: segments(draw,inset,radius,color,progress,false);break;
                default: OrbTimers.paintWedge(draw,inset,color,progress);
            }
        } finally {draw.dispose();}
    }

    private static void ring(Graphics2D draw,Rectangle inset,double radius,Color color,double value,float width)
    {
        draw.setColor(color);draw.setStroke(new BasicStroke(width));
        draw.draw(new Arc2D.Double(inset.getCenterX()-radius,inset.getCenterY()-radius,
            radius*2,radius*2,90,-360*Math.min(1,value),Arc2D.OPEN));
    }

    private static void meter(Graphics2D draw,Rectangle bounds,Color color,double value)
    {
        if(bounds.width<4 || bounds.height<1)return;
        draw.setColor(TRACK);draw.fill(bounds);
        draw.setColor(color);draw.fill(new Rectangle2D.Double(bounds.x,bounds.y,bounds.width*Math.min(1,value),bounds.height));
    }

    private static void segments(Graphics2D draw,Rectangle inset,double radius,Color color,double value,boolean rim)
    {
        double x=inset.getCenterX()-radius,y=inset.getCenterY()-radius,diameter=radius*2;
        if(rim)draw.setStroke(new BasicStroke(.8f));
        else draw.clip(new Ellipse2D.Double(x,y,diameter,diameter));
        for(int i=0;i<8;i++) {
            double part=Math.max(0,Math.min(1,value*8-i));
            double angle=90-i*45-3;
            if(rim) {
                draw.setColor(TRACK);draw.draw(new Arc2D.Double(x,y,diameter,diameter,angle,-39,Arc2D.OPEN));
                if(part>0) {draw.setColor(color);draw.draw(new Arc2D.Double(x,y,diameter,diameter,angle,-39*part,Arc2D.OPEN));}
            } else {
                draw.setColor(new Color(color.getRed(),color.getGreen(),color.getBlue(),12));
                draw.fill(new Arc2D.Double(x,y,diameter,diameter,angle,-39,Arc2D.PIE));
                if(part>0) {
                    draw.setColor(new Color(color.getRed(),color.getGreen(),color.getBlue(),52));
                    draw.fill(new Arc2D.Double(x,y,diameter,diameter,angle,-39*part,Arc2D.PIE));
                }
            }
        }
    }
}
