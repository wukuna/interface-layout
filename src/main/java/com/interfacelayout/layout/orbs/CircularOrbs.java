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
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.*;
import net.runelite.client.util.Text;

/** Orb artwork and native button clipping; actions and game availability stay native. */
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
    private int generation;
    private final Overlay frames=new Overlay() {
        {setPosition(OverlayPosition.DYNAMIC);setLayer(OverlayLayer.ABOVE_WIDGETS);}
        @Override public Dimension render(Graphics2D g) {
            if(running && config.hideOrbTabs()) {
                compass.paint(g, artwork);
                for(View v:views)if(!v.parent.isHidden()) {
                Graphics2D draw=(Graphics2D)g.create();
                try {draw.setComposite(AlphaComposite.SrcOver.derive((255-v.frameOpacity)/255f));artwork.paint(draw,v.circle);}
                finally {draw.dispose();}
                }
            }
            return null;
        }
    };
    public CircularOrbs() {setPosition(OverlayPosition.DYNAMIC);setLayer(OverlayLayer.ABOVE_WIDGETS);}
    public Overlay frames() {return frames;}
    public void start() {running=true;generation++;}
    public void stop() {running=false;int token=++generation;thread.invoke(()->{if(!running && token==generation){restore();artwork.clear();}});}
    private void restore() {
        compass.restore(manager);
        input.restore(client);
        opacities.forEach((widget,opacity)->{if(client.getWidget(widget.getId())==widget && widget.getOpacity()==255)widget.setOpacity(opacity);});
        opacities.clear();views.clear();
    }
    @Subscribe(priority=4) public void onScriptPreFired(ScriptPreFired event) {if(running)restore();}
    @Subscribe public void onGameStateChanged(GameStateChanged event) {if(event.getGameState()!=GameState.LOGGED_IN)restore();}
    @Subscribe(priority=-4) public void onBeforeRender(BeforeRender event) {refresh();}
    @Subscribe(priority=-4) public void onClientTick(ClientTick event) {refresh();}
    @Subscribe(priority=-4) public void onScriptPostFired(ScriptPostFired event) {refresh();}
    private void refresh() {
        restore();
        if(!running || !config.hideOrbTabs() || config.editInterface() || client.getGameState()!=GameState.LOGGED_IN)return;
        compass.refresh(client,manager);
        for(int i=0;i<ORBS.length;i++) {
            Orbs orb=ORBS[i];Widget parent=client.getWidget(orb.getComponentId()),backing=client.getWidget(orb.getBackingId());
            Widget indicator=client.getWidget(orb.getIndicatorId()),icon=client.getWidget(orb.getIconId()),number=client.getWidget(NUMBERS[i]);
            if(!visible(parent)||!visible(backing)||!visible(indicator)||!visible(icon)||!visible(number))continue;
            BufferedImage picture=sprites.get(icon);
            if(picture==null)continue; // Never hide before artwork is ready.
            Rectangle fill=WidgetGeometry.bounds(indicator),bounds=WidgetGeometry.bounds(backing);
            if(fill==null || bounds==null || fill.width<=0 || fill.height<=0 || bounds.width<=0 || bounds.height<=0)continue;
            Rectangle circle=CircularOrbArtwork.around(fill);
            View v=new View(parent,picture,circle,Text.removeTags(number.getText()),new Color(number.getTextColor()),backing.getOpacity(),icon.getOpacity(),number.getOpacity());
            views.add(v);hide(backing);hide(number);hide(icon);
            input.apply(parent,backing,client.getWidget(orb.getButtonId()),number,circle);
        }
    }
    @Subscribe(priority=-30) public void onPostMenuSort(PostMenuSort event) {
        if(!running || !config.hideOrbTabs() || config.editInterface() || client.isMenuOpen() || client.getMenu()==null)return;
        net.runelite.api.Point mouse=client.getMouseCanvasPosition();if(mouse==null)return;
        // Covers the transition tick if a menu was built before the setting or
        // native script changed. Keep opened menus and unrelated entries intact.
        net.runelite.api.MenuEntry[] entries=client.getMenu().getMenuEntries();
        net.runelite.api.MenuEntry[] kept=java.util.Arrays.stream(entries).filter(e->{
            for(View view:views)if(!view.circle.contains(mouse.getX(),mouse.getY()))
                for(Orbs orb:ORBS)if(orb.getComponentId()==view.parent.getId()
                    && (e.getParam1()==orb.getButtonId() || e.getParam1()==orb.getBackingId())
                    && (e.getType()==net.runelite.api.MenuAction.CC_OP || e.getType()==net.runelite.api.MenuAction.CC_OP_LOW_PRIORITY))return false;
            return true;
        }).toArray(net.runelite.api.MenuEntry[]::new);
        if(kept.length!=entries.length)client.getMenu().setMenuEntries(kept);
    }
    private static boolean visible(Widget w) {return w!=null && !w.isHidden() && w.getOpacity()<255;}
    private void hide(Widget w) {opacities.put(w,w.getOpacity());w.setOpacity(255);}
    @Override public Dimension render(Graphics2D g) {
        if(running && config.hideOrbTabs())for(View v:views)if(!v.parent.isHidden())paintContent(g,v.circle,v.icon,v.number,v.color,config.orbContent(),v.iconOpacity,v.numberOpacity);
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
        final Widget parent;final BufferedImage icon;final Rectangle circle;final String number;final Color color;final int frameOpacity,iconOpacity,numberOpacity;
        View(Widget p,BufferedImage i,Rectangle c,String n,Color col,int fo,int io,int no) {
            parent=p;icon=i;circle=c;number=n;color=col;frameOpacity=fo;iconOpacity=io;numberOpacity=no;
        }
    }
}
