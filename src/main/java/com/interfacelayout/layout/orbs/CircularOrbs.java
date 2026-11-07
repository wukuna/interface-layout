package com.interfacelayout.layout.orbs;

import com.interfacelayout.InterfaceLayoutConfig;
import com.interfacelayout.InterfaceLayoutConfig.OrbContent;
import com.interfacelayout.layout.menu.MenuButtonSprites;
import com.interfacelayout.layout.orbs.widget.elements.Orbs;
import com.interfacelayout.util.WidgetGeometry;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.BeforeRender;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.PostMenuSort;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.ProfileChanged;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.*;
import net.runelite.client.util.Text;

@Singleton
public final class CircularOrbs extends Overlay {
    private static final Orbs[] ORBS={Orbs.HP_ORB_CONTAINER,Orbs.PRAYER_ORB_CONTAINER,Orbs.RUN_ORB_CONTAINER,Orbs.SPEC_ORB_CONTAINER};
    private static final int[] NUMBERS={InterfaceID.Orbs.HEALTH_TEXT,InterfaceID.Orbs.PRAYER_TEXT,InterfaceID.Orbs.RUNENERGY_TEXT,InterfaceID.Orbs.SPECENERGY_TEXT};
    @Inject Client client;
    @Inject ClientThread thread;
    @Inject InterfaceLayoutConfig config;
    @Inject MenuButtonSprites sprites;
    @Inject OrbController manager;
    private final Map<Widget,Integer> opacities=new IdentityHashMap<>();
    private final CircularOrbInput input = new CircularOrbInput();
    private final CircularOrbArtwork artwork = new CircularOrbArtwork();
    private final CircularCompassFrame compass = new CircularCompassFrame();
    private final List<View> views=new ArrayList<>();
    private volatile boolean running;
    private volatile int generation;
    private int scriptDepth;
    private final Overlay frames=new Overlay() {
        {setPosition(OverlayPosition.DYNAMIC);setLayer(OverlayLayer.ABOVE_WIDGETS);}
        @Override public Dimension render(Graphics2D g) {
            if(running && config.hideOrbTabs()) {
                compass.paint(g, artwork);
            }
            return null;
        }
    };
    public CircularOrbs() {setPosition(OverlayPosition.DYNAMIC);setLayer(OverlayLayer.ABOVE_WIDGETS);}
    public Overlay frames() {return frames;}
    public void start() {running=true;generation++;}
    public void stop() {running=false;int token=++generation;thread.invoke(()->{if(!running && token==generation){scriptDepth=0;restore();artwork.clear();}});}
    private void restore() {
        compass.restore(manager);
        input.restore(client);
        opacities.forEach((widget,opacity)->{if(CircularOrbInput.current(client,widget) && widget.getOpacity()==255)widget.setOpacity(opacity);});
        opacities.clear();views.clear();
    }
    @Subscribe(priority=4) public void onScriptPreFired(ScriptPreFired event) {
        if(running && scriptDepth++==0)restore();
    }
    @Subscribe public void onGameStateChanged(GameStateChanged event) {
        if(event.getGameState()!=GameState.LOGGED_IN){scriptDepth=0;restore();}
    }
    @Subscribe(priority=-4) public void onBeforeRender(BeforeRender event) {

        scriptDepth=0;refresh();
    }
    @Subscribe(priority=-4) public void onClientTick(ClientTick event) {refresh();}
    @Subscribe(priority=-4) public void onScriptPostFired(ScriptPostFired event) {
        if(!running)return;
        if(scriptDepth>0)--scriptDepth;

        if(scriptDepth==0)refresh();
    }
    private void releaseBeforeLayout() {
        if(!running)return;
        int token=generation;
        thread.invoke(()->{if(running && token==generation)restore();});
    }
    @Subscribe(priority=4) public void onConfigChanged(ConfigChanged event) {
        if(OrbConstants.ConfigGroup.GROUP_NAME.equals(event.getGroup()))releaseBeforeLayout();
    }
    @Subscribe(priority=4) public void onProfileChanged(ProfileChanged event) {releaseBeforeLayout();}
    private void refresh() {
        if(scriptDepth>0)return;
        restore();
        if(!running || !config.hideOrbTabs() || config.editInterface() || client.getGameState()!=GameState.LOGGED_IN)return;
        compass.refresh(client,manager);
        for(int i=0;i<ORBS.length;i++) {
            Orbs orb=ORBS[i];Widget parent=client.getWidget(orb.getComponentId()),backing=client.getWidget(orb.getBackingId());
            Widget indicator=client.getWidget(orb.getIndicatorId()),icon=client.getWidget(orb.getIconId()),number=client.getWidget(NUMBERS[i]);
            if(!visible(parent)||!visible(backing))continue;
            Rectangle fill=WidgetGeometry.bounds(indicator),bounds=WidgetGeometry.bounds(backing);
            if(bounds.width<=0 || bounds.height<=0 || WidgetGeometry.bounds(parent).isEmpty())continue;
            if(fill.width<=0 || fill.height<=0)fill=new Rectangle(bounds.x+bounds.width-30,bounds.y+4,26,26);
            Rectangle circle=CircularOrbArtwork.around(fill);
            Widget button=client.getWidget(orb.getButtonId());
            CircularOrbLayers layers=CircularOrbLayers.capture(parent,backing,button,icon,sprites,this::hide);
            View v=new View(parent,sprites.get(icon),circle,WidgetGeometry.bounds(parent),layers,
                number==null?"":Text.removeTags(number.getText()),number==null?Color.WHITE:new Color(number.getTextColor()),
                backing.getOpacity(),visible(icon)?icon.getOpacity():255,visible(number)?number.getOpacity():255);
            views.add(v);hide(backing);hide(number);hide(icon);
            net.runelite.api.Point mouse=client.getMouseCanvasPosition();
            boolean hovered=mouse!=null && CircularOrbArtwork.contains(circle,mouse.getX(),mouse.getY());
            input.apply(parent,backing,button,number,circle,hovered);
            if(mouse!=null && v.original.contains(mouse.getX(),mouse.getY()) && !hovered)
                hideTree(client.getWidget(InterfaceID.Orbs.TOOLTIP),Collections.newSetFromMap(new IdentityHashMap<>()));
        }
    }
    @Subscribe(priority=-30) public void onPostMenuSort(PostMenuSort event) {
        if(!running || !config.hideOrbTabs() || config.editInterface() || client.isMenuOpen() || client.getMenu()==null)return;
        net.runelite.api.Point mouse=client.getMouseCanvasPosition();if(mouse==null)return;

        net.runelite.api.MenuEntry[] entries=client.getMenu().getMenuEntries();
        net.runelite.api.MenuEntry[] kept=java.util.Arrays.stream(entries).filter(e->{
            for(View view:views)if(!CircularOrbArtwork.contains(view.circle,mouse.getX(),mouse.getY())) {
                if(e.getParam1()==view.parent.getId())return false;
                for(Orbs orb:ORBS)if(orb.getComponentId()==view.parent.getId()
                    && (e.getParam1()==orb.getButtonId() || e.getParam1()==orb.getBackingId()))return false;
                Set<Widget> visited=Collections.newSetFromMap(new IdentityHashMap<>());
                for(Widget widget=e.getWidget();widget!=null && visited.add(widget);widget=widget.getParent())
                    if(widget==view.parent)return false;
            }
            return true;
        }).toArray(net.runelite.api.MenuEntry[]::new);
        if(kept.length!=entries.length)client.getMenu().setMenuEntries(kept);
    }
    private static boolean visible(Widget w) {return w!=null && !w.isHidden() && w.getOpacity()<255;}
    private void hide(Widget w) {if(w!=null){opacities.putIfAbsent(w,w.getOpacity());w.setOpacity(255);}}
    private void hideTree(Widget widget,Set<Widget> visited) {
        if(widget==null || !visited.add(widget))return;
        hide(widget);
        for(Widget child:CircularOrbLayers.children(widget))hideTree(child,visited);
    }
    @Override public Dimension render(Graphics2D g) {
        if(running && config.hideOrbTabs())for(View v:views)if(!v.parent.isHidden()) {
            Graphics2D draw=(Graphics2D)g.create();
            try {
                draw.setComposite(AlphaComposite.SrcOver.derive((255-v.frameOpacity)/255f));
                artwork.paintBackground(draw,v.circle);
                v.layers.paint(draw,v.circle);
                artwork.paint(draw,v.circle);
                paintContent(draw,v.circle,v.icon,v.number,v.color,config.orbContent(),v.iconOpacity,v.numberOpacity);
            } finally {draw.dispose();}
        }
        return null;
    }
    static void paintContent(Graphics2D g,Rectangle circle,BufferedImage icon,String number,Color color,OrbContent mode,int iconOpacity,int numberOpacity) {
        Graphics2D draw=(Graphics2D)g.create();
        try {
            draw.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            draw.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION,RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
            draw.clip(new Ellipse2D.Double(circle.x,circle.y,circle.width,circle.height));
            int cx=circle.x+circle.width/2;
            if(mode!=OrbContent.NUMBER_ONLY && icon!=null) {
                int size=mode==OrbContent.ICON_ONLY?Math.min(26,circle.width-8):12;
                double scale=Math.min(1,Math.min((double)size/icon.getWidth(),(double)size/icon.getHeight()));
                int w=Math.max(1,(int)(icon.getWidth()*scale)),h=Math.max(1,(int)(icon.getHeight()*scale));
                draw.setComposite(AlphaComposite.SrcOver.derive((255-iconOpacity)/255f));
                draw.drawImage(icon,cx-w/2,mode==OrbContent.ICON_ONLY?circle.y+(circle.height-h)/2:circle.y+3,w,h,null);
            }
            if(mode!=OrbContent.ICON_ONLY && number!=null) {
                draw.setComposite(AlphaComposite.SrcOver.derive((255-numberOpacity)/255f));draw.setFont(FontManager.getRunescapeSmallFont());
                while(draw.getFontMetrics().stringWidth(number)>circle.width-4 && draw.getFont().getSize()>8)draw.setFont(draw.getFont().deriveFont((float)draw.getFont().getSize()-1));
                FontMetrics fm=draw.getFontMetrics();int x=cx-fm.stringWidth(number)/2;
                int y=mode==OrbContent.NUMBER_ONLY?circle.y+(circle.height-fm.getHeight())/2+fm.getAscent():circle.y+circle.height-5;
                draw.setColor(Color.BLACK);draw.drawString(number,x+1,y+1);draw.setColor(color);draw.drawString(number,x,y);
            }
        }finally {draw.dispose();}
    }
    private static final class View {
        final Widget parent;final BufferedImage icon;final Rectangle circle,original;final CircularOrbLayers layers;
        final String number;final Color color;final int frameOpacity,iconOpacity,numberOpacity;
        View(Widget p,BufferedImage i,Rectangle c,Rectangle original,CircularOrbLayers layers,String n,Color col,int fo,int io,int no) {
            parent=p;icon=i;circle=c;this.original=original;this.layers=layers;number=n;color=col;frameOpacity=fo;iconOpacity=io;numberOpacity=no;
        }
    }
}
