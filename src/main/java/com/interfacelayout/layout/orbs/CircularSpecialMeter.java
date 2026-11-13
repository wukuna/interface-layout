package com.interfacelayout.layout.orbs;

import com.interfacelayout.layout.menu.MenuButtonSprites;
import com.interfacelayout.util.WidgetGeometry;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetType;

public final class CircularSpecialMeter
{
    final Widget source;
    final Rectangle original,bounds;
    private final List<Part> parts=new ArrayList<>();

    private CircularSpecialMeter(Widget source,Rectangle circle,int gap)
    {
        this.source=source;original=WidgetGeometry.bounds(source);
        bounds=new Rectangle((int)Math.round(circle.getCenterX()-original.width/2.0),circle.y+circle.height+gap,original.width,original.height);
    }

    public static boolean matches(Widget orb,Widget child)
    {
        return child!=null && child.getParentId()==orb.getId() && child.getType()==WidgetType.LAYER
            && child.getIndex()>=0 && child.getOriginalWidth()==33 && child.getOriginalHeight()==4;
    }

    static CircularSpecialMeter capture(Widget parent,Rectangle circle,int gap,MenuButtonSprites sprites)
    {
        Widget[] children=parent.getDynamicChildren();
        if(children==null)return null;
        for(Widget child:children)if(matches(parent,child) && !child.isHidden()) {
            CircularSpecialMeter meter=new CircularSpecialMeter(child,circle,gap);
            if(!meter.original.isEmpty() && meter.capture(child,meter.original,Math.max(0,255-parent.getOpacity())/255f,sprites,
                Collections.newSetFromMap(new IdentityHashMap<>())) && !meter.parts.isEmpty())return meter;
        }
        return null;
    }

    private boolean capture(Widget widget,Rectangle clip,float alpha,MenuButtonSprites sprites,Set<Widget> seen)
    {
        if(widget==null || !seen.add(widget) || widget.isHidden() || widget.getOpacity()>=255)return true;
        Rectangle area=WidgetGeometry.bounds(widget),visible=clip.intersection(area);
        if(visible.isEmpty())return true;
        alpha*=Math.max(0,255-widget.getOpacity())/255f;
        if(widget.getType()==WidgetType.LAYER) {
            for(Widget child:CircularOrbLayers.children(widget))if(!capture(child,visible,alpha,sprites,seen))return false;
        } else if(widget.getType()==WidgetType.RECTANGLE) {
            parts.add(new Part(area,visible,alpha,new Color(widget.getTextColor()),widget.isFilled(),null));
        } else if(widget.getType()==WidgetType.GRAPHIC) {
            BufferedImage image=sprites.getPadded(widget);
            if(image==null)return false;
            parts.add(new Part(area,visible,alpha,null,false,image));
        } else return false;
        return true;
    }

    void paint(Graphics2D graphics)
    {
        Graphics2D draw=(Graphics2D)graphics.create();
        try {
            draw.translate(bounds.x-original.x,bounds.y-original.y);
            for(Part part:parts) {
                Graphics2D item=(Graphics2D)draw.create();
                try {
                    item.clip(part.clip);item.setComposite(AlphaComposite.SrcOver.derive(part.alpha));
                    Rectangle r=part.area;
                    if(part.image!=null)item.drawImage(part.image,r.x,r.y,r.width,r.height,null);
                    else {
                        item.setColor(part.color);
                        if(part.filled)item.fillRect(r.x,r.y,r.width,r.height);
                        else item.drawRect(r.x,r.y,r.width-1,r.height-1);
                    }
                } finally {item.dispose();}
            }
        } finally {draw.dispose();}
    }

    private static final class Part
    {
        final Rectangle area,clip;
        final float alpha;
        final Color color;
        final boolean filled;
        final BufferedImage image;
        Part(Rectangle area,Rectangle clip,float alpha,Color color,boolean filled,BufferedImage image)
        {this.area=area;this.clip=clip;this.alpha=alpha;this.color=color;this.filled=filled;this.image=image;}
    }
}
