package com.interfacelayout.layout.orbs;

import com.interfacelayout.InterfaceLayoutConfig;
import com.interfacelayout.InterfaceLayoutConfig.OrbContent;
import com.interfacelayout.layout.menu.MenuButtonSprites;
import com.interfacelayout.layout.orbs.widget.elements.Orbs;
import com.interfacelayout.util.WidgetGeometry;
import com.interfacelayout.util.InterfaceWindows;
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
import net.runelite.api.events.GameTick;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarbitID;
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
    @Inject net.runelite.client.ui.DrawManager drawManager;
    @Inject InterfaceLayoutConfig config;
    @Inject MenuButtonSprites sprites;
    @Inject OrbController manager;
    @Inject SoulreaperOrbCompatibility soulreaper;
    @Inject net.runelite.client.game.ItemManager itemManager;
    final OrbTimers timers = new OrbTimers();
    private final Map<Widget,Integer> opacities=new IdentityHashMap<>();
    private final Set<Widget> coveredWidgets=Collections.newSetFromMap(new IdentityHashMap<>());
    private final CircularOrbInput input = new CircularOrbInput();
    private final Runnable afterFrame = () -> input.restoreRenderBounds(client);
    private final CircularOrbArtwork artwork = new CircularOrbArtwork();
    private final CircularCompassFrame compass = new CircularCompassFrame();
    private final CircularMinimapFrame minimap = new CircularMinimapFrame();
    private final List<View> views=new ArrayList<>();
    private final List<NativeTimerView> nativeTimers=new ArrayList<>();
    private final List<CircularSpecialMeter> specialMeters=new ArrayList<>();
    private volatile boolean running;
    private volatile int generation;
    private int timerGeneration = -1;
    private int scriptDepth;
    private final Overlay frames=new Overlay() {
        {setPosition(OverlayPosition.DYNAMIC);setLayer(OverlayLayer.ABOVE_WIDGETS);}
        @Override public Dimension render(Graphics2D g) {
            if(running && (config.hideOrbTabs() || freeMinimap())) {
                minimap.paint(g, artwork, config.orbRim());
                compass.paint(g, artwork, config.orbRim());
            }
            return null;
        }
    };
    public CircularOrbs() {setPosition(OverlayPosition.DYNAMIC);setLayer(OverlayLayer.ABOVE_WIDGETS);setPriority(Overlay.PRIORITY_HIGHEST);}
    public Overlay frames() {return frames;}
    public void start() {running=true;generation++;if(drawManager!=null)drawManager.registerEveryFrameListener(afterFrame);}
    public void stop() {if(drawManager!=null)drawManager.unregisterEveryFrameListener(afterFrame);running=false;int token=++generation;thread.invoke(()->{if(!running && token==generation){scriptDepth=0;restore();timers.reset();if(soulreaper!=null)soulreaper.restore();artwork.clear();}});}
    public void releaseForLayout() {restore();}
    public void refreshAfterLayout() {refresh();}
    private void restore() {
        for (Widget widget : coveredWidgets)
            if (CircularOrbInput.current(client, widget) && widget.isSelfHidden()) widget.setHidden(false);
        coveredWidgets.clear();
        minimap.restore(client);
        compass.restore(client,manager);
        input.restore(client);
        opacities.forEach((widget,opacity)->{if(CircularOrbInput.current(client,widget) && widget.getOpacity()==255)widget.setOpacity(opacity);});
        opacities.clear();views.clear();nativeTimers.clear();specialMeters.clear();
    }
    @Subscribe(priority=4) public void onScriptPreFired(ScriptPreFired event) {
        if(running && scriptDepth++==0)restore();
    }
    @Subscribe public void onGameStateChanged(GameStateChanged event) {
        if(event.getGameState()==GameState.LOGIN_SCREEN || event.getGameState()==GameState.HOPPING)timers.reset();
        if(event.getGameState()!=GameState.LOGGED_IN){scriptDepth=0;restore();if(soulreaper!=null)soulreaper.restore();}
    }
    @Subscribe public void onGameTick(GameTick event) {
        if(running && client.getGameState()==GameState.LOGGED_IN) {
            if(timerGeneration!=generation){timers.reset();timerGeneration=generation;}
            timers.tick(client,itemManager,System.nanoTime());
        }
    }
    @Subscribe public void onVarbitChanged(VarbitChanged event) {
        if(running && event.getVarbitId()==VarbitID.PRAYER_RAPIDHEAL)timers.resetHitpoints();
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
        thread.invoke(()->{if(running && token==generation){restore();if(soulreaper!=null)soulreaper.restore();}});
    }
    @Subscribe(priority=4) public void onConfigChanged(ConfigChanged event) {
        if(OrbConstants.ConfigGroup.GROUP_NAME.equals(event.getGroup()))releaseBeforeLayout();
    }
    @Subscribe(priority=4) public void onProfileChanged(ProfileChanged event) {releaseBeforeLayout();}
    private boolean freeMinimap() {
        return config.layout() == OrbLayout.FREE_POSITION && !manager.isFixedMode();
    }
    private void refresh() {
        if(scriptDepth>0)return;
        restore();
        boolean visible=running && client.getGameState()==GameState.LOGGED_IN;
        boolean enabled=visible && config.hideOrbTabs() && !config.editInterface();
        Widget nativeMap = visible && freeMinimap() ? client.getWidget(manager.isClassicResizable()
            ? OrbConstants.Widgets.Classic.MINIMAP_MASK : OrbConstants.Widgets.Modern.MINIMAP_MASK) : null;
        boolean freeMap = visible(nativeMap) && !config.hideMinimap()
            && nativeMap.getContentType() == OrbConstants.Layout.MinimapOverlay.MINIMAP_CONTENT;
        boolean customMap = freeMap && config.minimapFrame() == InterfaceLayoutConfig.MinimapFrame.CIRCULAR;
        if (freeMap && customMap) minimap.refresh(client, manager);
        if (freeMap) compass.refresh(client, manager, customMap);
        Widget spec=client.getWidget(InterfaceID.Orbs.ORB_SPECENERGY),specBacking=client.getWidget(InterfaceID.Orbs.SPECENERGY_BACKING);
        if(soulreaper!=null)soulreaper.refresh(enabled && visible(spec) && visible(specBacking)
            && !WidgetGeometry.bounds(spec).isEmpty() && !WidgetGeometry.bounds(specBacking).isEmpty());
        if(!enabled) {
            if(visible && !config.hideOrbTabs() && !config.editInterface())refreshNativeTimers();
            return;
        }
        List<Rectangle> windows = InterfaceWindows.bounds(client);
        if (!freeMap) compass.refresh(client,manager);
        for(int i=0;i<ORBS.length;i++) {
            Orbs orb=ORBS[i];Widget parent=client.getWidget(orb.getComponentId()),backing=client.getWidget(orb.getBackingId());
            Widget indicator=client.getWidget(orb.getIndicatorId()),icon=client.getWidget(orb.getIconId()),number=client.getWidget(NUMBERS[i]);
            if(!visible(parent)||!visible(backing))continue;
            Rectangle fill=WidgetGeometry.bounds(indicator),bounds=WidgetGeometry.bounds(backing);
            if(bounds.width<=0 || bounds.height<=0 || WidgetGeometry.bounds(parent).isEmpty())continue;
            if(fill.width<=0 || fill.height<=0)fill=new Rectangle(bounds.x+bounds.width-30,bounds.y+4,26,26);
            Rectangle circle=artwork.aroundFill(fill,sprites.getPadded(indicator));
            if(soulreaper!=null && orb==Orbs.SPEC_ORB_CONTAINER)soulreaper.anchor(circle);
            Widget button=client.getWidget(orb.getButtonId());
            boolean tagTimer=!config.disableOrbTimers() && OrbTimerStyles.resolve(config,parent.getId())==InterfaceLayoutConfig.NativeTimerStyle.TAG_METERS
                && (orb==Orbs.HP_ORB_CONTAINER && config.orbHitpointsTimer() || orb==Orbs.PRAYER_ORB_CONTAINER && config.orbPrayerTimer()
                || orb==Orbs.SPEC_ORB_CONTAINER && config.orbSpecialTimer());
            Rectangle footprint=new Rectangle(circle);if(tagTimer)footprint.height+=4;
            if(orb==Orbs.SPEC_ORB_CONTAINER && !config.hideExtraSpecialAttackBar()) {
                CircularSpecialMeter meter=CircularSpecialMeter.capture(parent,circle,tagTimer?7:3,sprites);
                if(meter!=null) {
                    coveredWidgets.add(meter.source);meter.source.setHidden(true);
                    if(windows.stream().noneMatch(window->window.intersects(circle) || window.intersects(meter.bounds)))specialMeters.add(meter);
                }
            }
            CircularOrbLayers layers=CircularOrbLayers.capture(parent,backing,button,icon,sprites,this::hide);
            View v=new View(parent,sprites.get(icon),circle,WidgetGeometry.bounds(parent),layers,
                number==null?"":Text.removeTags(number.getText()),number==null?Color.WHITE:new Color(number.getTextColor()),
                backing.getOpacity(),visible(icon)?icon.getOpacity():255,visible(number)?number.getOpacity():255,
                windows.stream().anyMatch(window -> window.intersects(footprint)),
                client.getBoostedSkillLevel(net.runelite.api.Skill.PRAYER),client.getRealSkillLevel(net.runelite.api.Skill.PRAYER));
            views.add(v);hide(backing);hide(number);hide(icon);
            net.runelite.api.Point mouse=client.getMouseCanvasPosition();
            if (v.covered) {
                coveredWidgets.add(parent); parent.setHidden(true);
                if (mouse != null && v.original.contains(mouse.getX(), mouse.getY()))
                    hideTree(client.getWidget(InterfaceID.Orbs.TOOLTIP),Collections.newSetFromMap(new IdentityHashMap<>()));
                continue;
            }
            boolean hovered=mouse!=null && CircularOrbArtwork.contains(circle,mouse.getX(),mouse.getY());
            input.apply(parent,backing,button,number,circle,hovered);
            if(mouse!=null && v.original.contains(mouse.getX(),mouse.getY()) && !hovered)
                hideTree(client.getWidget(InterfaceID.Orbs.TOOLTIP),Collections.newSetFromMap(new IdentityHashMap<>()));
        }
    }

    private void refreshNativeTimers() {
        List<Rectangle> windows=InterfaceWindows.bounds(client);
        for(int i=0;i<ORBS.length;i++) {
            Orbs orb=ORBS[i];
            if(orb!=Orbs.HP_ORB_CONTAINER && orb!=Orbs.PRAYER_ORB_CONTAINER && orb!=Orbs.SPEC_ORB_CONTAINER)continue;
            Widget parent=client.getWidget(orb.getComponentId()),backing=client.getWidget(orb.getBackingId());
            Widget indicator=client.getWidget(orb.getIndicatorId());
            if(!visible(parent) || !visible(backing) || !visible(indicator))continue;
            Rectangle fill=WidgetGeometry.bounds(indicator);
            if(fill.isEmpty() || WidgetGeometry.bounds(parent).isEmpty())continue;
            Rectangle inset=artwork.insetFill(fill,sprites.getPadded(indicator));
            Rectangle area=new Rectangle(inset);area.grow(5,5);
            Rectangle number=WidgetGeometry.bounds(client.getWidget(NUMBERS[i]));
            area=area.union(number);
            final Rectangle painted=area;
            if(inset.isEmpty() || windows.stream().anyMatch(window -> window.intersects(painted)))continue;
            nativeTimers.add(new NativeTimerView(parent,backing,indicator,inset,number,
                client.getBoostedSkillLevel(net.runelite.api.Skill.PRAYER),client.getRealSkillLevel(net.runelite.api.Skill.PRAYER)));
        }
    }
    @Subscribe(priority=-30) public void onPostMenuSort(PostMenuSort event) {
        if(!running || !config.hideOrbTabs() && !freeMinimap() || config.editInterface() || client.isMenuOpen() || client.getMenu()==null)return;
        net.runelite.api.Point mouse=client.getMouseCanvasPosition();if(mouse==null)return;

        net.runelite.api.MenuEntry[] entries=client.getMenu().getMenuEntries();
        net.runelite.api.MenuEntry[] kept=java.util.Arrays.stream(entries).filter(e->{
            if(compass.coveredMenu(e))return false;
            for(View view:views)if(view.covered || !CircularOrbArtwork.contains(view.circle,mouse.getX(),mouse.getY())) {
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
        if(running && !config.hideOrbTabs() && !config.editInterface() && timerGeneration==generation)
            for(NativeTimerView view:nativeTimers)if(visible(view.parent) && visible(view.backing) && visible(view.indicator)) {
                Graphics2D draw=(Graphics2D)g.create();
                try {
                    int opacity=Math.max(view.parent.getOpacity(),Math.max(view.backing.getOpacity(),view.indicator.getOpacity()));
                    draw.setComposite(AlphaComposite.SrcOver.derive((255-opacity)/255f));
                    timers.paintInset(draw,view.inset,view.number,view.parent.getId(),config,System.nanoTime(),view.currentPrayer,view.maximumPrayer);
                } finally {draw.dispose();}
            }
        if(running && config.hideOrbTabs())for(View v:views)if(!v.covered && !v.parent.isHidden()) {
            Graphics2D draw=(Graphics2D)g.create();
            try {
                draw.setComposite(AlphaComposite.SrcOver.derive((255-v.frameOpacity)/255f));
                artwork.paintBackground(draw,v.circle);
                v.layers.paint(draw,v.circle);
                if(soulreaper!=null && v.parent.getId()==InterfaceID.Orbs.ORB_SPECENERGY)soulreaper.paintNativeTimer(draw,v.circle);
                artwork.paint(draw,v.circle,config.orbRim());
                paintContent(draw,v.circle,v.icon,v.number,v.color,config.orbContent(),v.iconOpacity,v.numberOpacity);
                if(timerGeneration==generation)timers.paint(draw,v.circle,v.parent.getId(),config,System.nanoTime(),v.currentPrayer,v.maximumPrayer);
            } finally {draw.dispose();}
            net.runelite.api.Point mouse = client.getMouseCanvasPosition();
            if (mouse == null || !CircularOrbArtwork.contains(v.circle, mouse.getX(), mouse.getY()))
                input.maskOutsideHover(v.parent);
        }
        if(running && config.hideOrbTabs() && !config.editInterface())for(CircularSpecialMeter meter:specialMeters) {
            Graphics2D draw=(Graphics2D)g.create();
            try {
                java.awt.geom.Area area=new java.awt.geom.Area(meter.bounds);
                for(View view:views)area.subtract(new java.awt.geom.Area(new Ellipse2D.Double(view.circle.x,view.circle.y,view.circle.width,view.circle.height)));
                draw.clip(area);meter.paint(draw);
            } finally {draw.dispose();}
        }
        return null;
    }

    private static final class NativeTimerView {
        final Widget parent,backing,indicator;
        final Rectangle inset,number;
        final int currentPrayer,maximumPrayer;
        NativeTimerView(Widget parent,Widget backing,Widget indicator,Rectangle inset,Rectangle number,int currentPrayer,int maximumPrayer) {
            this.parent=parent;this.backing=backing;this.indicator=indicator;this.inset=inset;this.number=number;
            this.currentPrayer=currentPrayer;this.maximumPrayer=maximumPrayer;
        }
    }
    static void paintContent(Graphics2D g,Rectangle circle,BufferedImage icon,String number,Color color,OrbContent mode,int iconOpacity,int numberOpacity) {
        Graphics2D draw=(Graphics2D)g.create();
        try {
            draw.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            draw.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION,RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
            draw.clip(new Ellipse2D.Double(circle.x,circle.y,circle.width,circle.height));
            int cx=circle.x+circle.width/2;
            if(mode!=OrbContent.NUMBER_ONLY && icon!=null) {
                icon=CircularOrbArtwork.content(icon);
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
        final boolean covered;
        final int currentPrayer,maximumPrayer;
        View(Widget p,BufferedImage i,Rectangle c,Rectangle original,CircularOrbLayers layers,String n,Color col,int fo,int io,int no,boolean covered,int currentPrayer,int maximumPrayer) {
            parent=p;icon=i;circle=c;this.original=original;this.layers=layers;number=n;color=col;frameOpacity=fo;iconOpacity=io;numberOpacity=no;this.covered=covered;
            this.currentPrayer=currentPrayer;this.maximumPrayer=maximumPrayer;
        }
    }
}
