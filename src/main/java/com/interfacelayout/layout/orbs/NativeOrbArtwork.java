package com.interfacelayout.layout.orbs;

import com.interfacelayout.layout.menu.MenuButtonSprites;
import com.interfacelayout.util.WidgetGeometry;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetType;
import net.runelite.client.ui.FontManager;
import net.runelite.client.util.Text;

final class NativeOrbArtwork
{
    final Rectangle bounds;
    private final BufferedImage backing;
    private final boolean mirrored;
    private final Rectangle inset;
    private final List<Part> parts=new ArrayList<>();

    private NativeOrbArtwork(Widget widget, BufferedImage backing, boolean mirrored,Rectangle inset)
    { this.bounds=WidgetGeometry.bounds(widget); this.backing=backing; this.mirrored=mirrored;this.inset=new Rectangle(inset); }

    static NativeOrbArtwork capture(Widget parent, Widget backing, Widget button, Widget icon, Widget number,
        boolean mirrored, Rectangle inset, MenuButtonSprites sprites, Consumer<Widget> hide)
    {
        BufferedImage image=sprites.getPadded(backing);
        if(image==null || WidgetGeometry.bounds(backing).isEmpty())return null;
        NativeOrbArtwork result=new NativeOrbArtwork(backing,image,mirrored,inset);
        Set<Widget> visited=Collections.newSetFromMap(new IdentityHashMap<>());
        result.capture(parent,backing,button,WidgetGeometry.bounds(parent),sprites,hide,visited);
        for(Widget child:new Widget[]{backing,icon,number})
            result.capture(child,backing,button,WidgetGeometry.bounds(parent),sprites,hide,visited);
        return result;
    }

    private void capture(Widget widget,Widget frame,Widget button,Rectangle clip,MenuButtonSprites sprites,
        Consumer<Widget> hide,Set<Widget> visited)
    {
        if(widget==null || !visited.add(widget) || widget.isHidden())return;
        Rectangle area=WidgetGeometry.bounds(widget),visible=clip.intersection(area);
        if(visible.isEmpty())return;
        if(widget!=button && widget.getOpacity()<255)
        {
            BufferedImage image=widget==frame?backing:widget.getType()==WidgetType.GRAPHIC?sprites.getPadded(widget):null;
            String text=widget.getType()==WidgetType.TEXT?Text.removeTags(widget.getText()):null;
            if(image!=null || text!=null || widget.getType()==WidgetType.RECTANGLE)
            {
                parts.add(new Part(area,visible,image,text,widget.getTextColor(),widget.getOpacity(),
                    widget==frame && mirrored,widget.getXTextAlignment(),widget.getYTextAlignment(),widget.getTextShadowed(),widget.isFilled()));
                hide.accept(widget);
            }
        }
        for(Widget child:CircularOrbLayers.children(widget))
            capture(child,frame,button,visible,sprites,hide,visited);
    }

    boolean contains(int x,int y)
    {
        if(CircularOrbArtwork.contains(inset,x,y))return true;
        if(!bounds.contains(x,y))return false;
        int ix=Math.min(backing.getWidth()-1,(x-bounds.x)*backing.getWidth()/bounds.width);
        int iy=Math.min(backing.getHeight()-1,(y-bounds.y)*backing.getHeight()/bounds.height);
        if(mirrored)ix=backing.getWidth()-1-ix;
        return (backing.getRGB(ix,iy)>>>24)!=0;
    }

    void paint(Graphics2D graphics)
    {
        for(Part part:parts)
        {
            Graphics2D draw=(Graphics2D)graphics.create();
            try
            {
                draw.clip(part.clip);draw.setComposite(AlphaComposite.SrcOver.derive((255-part.opacity)/255f));
                Rectangle b=part.bounds;
                if(part.image!=null)
                    draw.drawImage(part.image,part.flip?b.x+b.width:b.x,b.y,part.flip?-b.width:b.width,b.height,null);
                else if(part.text!=null)
                {
                    draw.setFont(FontManager.getRunescapeSmallFont());FontMetrics fm=draw.getFontMetrics();
                    int x=part.xAlign==1?b.x+(b.width-fm.stringWidth(part.text))/2:part.xAlign==2?b.x+b.width-fm.stringWidth(part.text):b.x;
                    int y=part.yAlign==1?b.y+(b.height-fm.getHeight())/2+fm.getAscent():part.yAlign==2?b.y+b.height-fm.getDescent():b.y+fm.getAscent();
                    if(part.shadow){draw.setColor(Color.BLACK);draw.drawString(part.text,x+1,y+1);}
                    draw.setColor(new Color(part.color));draw.drawString(part.text,x,y);
                }
                else
                {
                    draw.setColor(new Color(part.color));
                    if(part.filled)draw.fillRect(b.x,b.y,b.width,b.height);else draw.drawRect(b.x,b.y,b.width-1,b.height-1);
                }
            }
            finally {draw.dispose();}
        }
    }

    private static final class Part
    {
        final Rectangle bounds,clip;
        final BufferedImage image;
        final String text;
        final int color,opacity,xAlign,yAlign;
        final boolean flip,shadow,filled;
        Part(Rectangle bounds,Rectangle clip,BufferedImage image,String text,int color,int opacity,boolean flip,
            int xAlign,int yAlign,boolean shadow,boolean filled)
        {
            this.bounds=bounds;this.clip=clip;this.image=image;this.text=text;this.color=color;this.opacity=opacity;
            this.flip=flip;this.xAlign=xAlign;this.yAlign=yAlign;this.shadow=shadow;this.filled=filled;
        }
    }
}
