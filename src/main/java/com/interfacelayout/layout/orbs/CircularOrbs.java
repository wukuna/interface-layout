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
    @Inject NativeOrbTimerCompatibility nativeTimerCompatibility;
    @Inject ShieldSpecialOrb shield;
    @Inject net.runelite.client.game.ItemManager itemManager;
    final OrbTimers timers = new OrbTimers();
    private final Map<Widget,Integer> opacities=new IdentityHashMap<>();
    private final Set<Widget> coveredWidgets=Collections.newSetFromMap(new IdentityHashMap<>());
    private final CircularOrbInput input = new CircularOrbInput();
    private final OrbMinimapInput mapInput = new OrbMinimapInput();
    private final List<NativeView> nativeViews = new ArrayList<>();
    private final Runnable afterFrame = () -> { input.restoreRenderBounds(client); mapInput.resumeInput(client); };
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
    private boolean rendering;
    private final Overlay frames=new Overlay() {
        {setPosition(OverlayPosition.DYNAMIC);setLayer(OverlayLayer.ABOVE_WIDGETS);}
        @Override public Dimension render(Graphics2D g) {
            if(running && (config.hideOrbTabs() || freeMinimap())) {
                Graphics2D map=(Graphics2D)g.create();
                try {compass.clipMinimap(map);minimap.paint(map, artwork, config.orbRim());} finally {map.dispose();}
                compass.paint(g, artwork, config.orbRim());
            }
            return null;
        }
    };
    public CircularOrbs() {setPosition(OverlayPosition.DYNAMIC);setLayer(OverlayLayer.ABOVE_WIDGETS);setPriority(Overlay.PRIORITY_HIGHEST);}
    public Overlay frames() {return frames;}
    public void start() {running=true;generation++;if(drawManager!=null)drawManager.registerEveryFrameListener(afterFrame);}
    public void stop() {if(drawManager!=null)drawManager.unregisterEveryFrameListener(afterFrame);running=false;int token=++generation;thread.invoke(()->{if(!running && token==generation){scriptDepth=0;restore();compass.clear(client);timers.reset();if(soulreaper!=null)soulreaper.restore();if(nativeTimerCompatibility!=null)nativeTimerCompatibility.restore();artwork.clear();}});}
    public void releaseForLayout() {restore();}
    public void refreshAfterLayout() {refresh();}
    private void restore() {
        for (Widget widget : coveredWidgets)
            if (CircularOrbInput.current(client, widget) && widget.isSelfHidden()) widget.setHidden(false);
        coveredWidgets.clear();
        mapInput.restore(client);
        minimap.restore(client);
        compass.restore(client,manager);
        input.restore(client);
        opacities.forEach((widget,opacity)->{if(CircularOrbInput.current(client,widget) && widget.getOpacity()==255)widget.setOpacity(opacity);});
        opacities.clear();views.clear();nativeTimers.clear();nativeViews.clear();specialMeters.clear();
    }
    @Subscribe(priority=4) public void onScriptPreFired(ScriptPreFired event) {
        if(running && scriptDepth++==0)restore();
    }
    @Subscribe public void onGameStateChanged(GameStateChanged event) {
        if(event.getGameState()==GameState.LOGIN_SCREEN || event.getGameState()==GameState.HOPPING)timers.reset();
        if(event.getGameState()!=GameState.LOGGED_IN){scriptDepth=0;restore();compass.clear(client);if(soulreaper!=null)soulreaper.restore();if(nativeTimerCompatibility!=null)nativeTimerCompatibility.restore();}
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

        rendering=true;scriptDepth=0;refresh();mapInput.prepareRender(client);
    }
    @Subscribe(priority=-4) public void onClientTick(ClientTick event) {rendering=false;refresh();}
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
        if(visible && config.hideGravestone())
            for(com.interfacelayout.layout.orbs.widget.elements.Gravestone target:com.interfacelayout.layout.orbs.widget.elements.Gravestone.values()) {
                Widget grave=client.getWidget(target.getComponentId());
                if(grave!=null && !grave.isHidden()){coveredWidgets.add(grave);grave.setHidden(true);}
            }
        if(nativeTimerCompatibility!=null)nativeTimerCompatibility.refresh(visible && !config.editInterface());
        boolean enabled=visible && config.hideOrbTabs() && !config.editInterface();
        Widget nativeMap = visible && freeMinimap() ? client.getWidget(manager.isClassicResizable()
            ? OrbConstants.Widgets.Classic.MINIMAP_MASK : OrbConstants.Widgets.Modern.MINIMAP_MASK) : null;
        boolean freeMap = visible(nativeMap) && !config.hideMinimap()
            && nativeMap.getContentType() == OrbConstants.Layout.MinimapOverlay.MINIMAP_CONTENT;
        boolean customMap = freeMap && config.minimapFrame() == InterfaceLayoutConfig.MinimapFrame.CIRCULAR;
        if (freeMap) minimap.refresh(client, manager, customMap);
        if (freeMap) compass.refresh(client, manager, customMap, nativeMap);
        else compass.clear(client);
        Widget spec=client.getWidget(InterfaceID.Orbs.ORB_SPECENERGY),specBacking=client.getWidget(InterfaceID.Orbs.SPECENERGY_BACKING);
        if(visible && config.hideExtraSpecialAttackBar() && spec!=null)
            for(Widget child:CircularOrbLayers.children(spec))
                if(CircularSpecialMeter.matches(spec,child) && !child.isHidden()){coveredWidgets.add(child);child.setHidden(true);}
        if(soulreaper!=null)soulreaper.timers(timerGeneration==generation?timers:null);
        if(soulreaper!=null)soulreaper.refresh(visible && !config.editInterface() && visible(spec) && visible(specBacking)
            && !WidgetGeometry.bounds(spec).isEmpty() && !WidgetGeometry.bounds(specBacking).isEmpty());
        if(!enabled) {
            if(visible && !config.hideOrbTabs() && !config.editInterface()){refreshNativeTimers();applyInput();}
            return;
        }
        List<Rectangle> windows = InterfaceWindows.bounds(client);
        if (!freeMap) compass.refresh(client,manager,config.hideMinimap());
        for(OrbOrder type:OrbOrder.backToFront(config.orbStackOrder())) {
            int i=type.ordinal();
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
            Rectangle footprint=new Rectangle(circle);if(tagTimer)footprint.height+=orb==Orbs.PRAYER_ORB_CONTAINER && config.prayerOrbDisplay()==InterfaceLayoutConfig.PrayerDisplay.BOTH?7:4;
            if(orb==Orbs.SPEC_ORB_CONTAINER)captureSpecialMeter(parent,circle,tagTimer?7:3,windows);
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
        }
        applyInput();
    }

    private void refreshNativeTimers() {
        List<Rectangle> windows=InterfaceWindows.bounds(client);
        for(OrbOrder type:OrbOrder.backToFront(config.orbStackOrder())) {
            Orbs orb=type.target;
            Widget parent=client.getWidget(orb.getComponentId()),backing=client.getWidget(orb.getBackingId());
            Widget indicator=client.getWidget(orb.getIndicatorId()),button=client.getWidget(orb.getButtonId());
            Widget icon=client.getWidget(orb.getIconId()),number=client.getWidget(type.number);
            if(!visible(parent) || !visible(backing) || !visible(indicator))continue;
            boolean ready=sprites.getPadded(backing)!=null;
            if(ready && type.mirrored(config))input.mirror(parent);
            Rectangle fill=WidgetGeometry.bounds(indicator);
            if(fill.isEmpty() || WidgetGeometry.bounds(parent).isEmpty())continue;
            Rectangle inset=artwork.insetFill(fill,sprites.getPadded(indicator));
            Rectangle numberBounds=WidgetGeometry.bounds(number);
            Rectangle area=WidgetGeometry.bounds(backing).union(numberBounds);area.grow(5,5);
            boolean covered=windows.stream().anyMatch(window->window.intersects(area));
            if(covered) {
                if(ready){coveredWidgets.add(parent);parent.setHidden(true);}
                continue;
            }
            if(type==OrbOrder.SPECIAL) {
                if(soulreaper!=null)soulreaper.anchor(artwork.aroundFill(fill,sprites.getPadded(indicator)));
                captureSpecialMeter(parent,artwork.aroundFill(fill,sprites.getPadded(indicator)),3,windows);
            }
            NativeTimerView timer=new NativeTimerView(parent,backing,indicator,inset,numberBounds,
                client.getBoostedSkillLevel(net.runelite.api.Skill.PRAYER),client.getRealSkillLevel(net.runelite.api.Skill.PRAYER));
            NativeOrbArtwork image=ready?NativeOrbArtwork.capture(parent,backing,button,icon,number,type.mirrored(config),fill,sprites,this::hide):null;
            if(image==null)nativeTimers.add(timer);
            else nativeViews.add(new NativeView(type,parent,button,image,timer));
        }
    }

    private void captureSpecialMeter(Widget parent,Rectangle host,int gap,List<Rectangle> windows) {
        if(config.hideExtraSpecialAttackBar())return;
        CircularSpecialMeter meter=CircularSpecialMeter.capture(parent,host,gap,sprites);
        if(meter==null)return;
        coveredWidgets.add(meter.source);meter.source.setHidden(true);
        if(windows.stream().noneMatch(window->window.intersects(host) || window.intersects(meter.bounds)))specialMeters.add(meter);
    }

    int hoveredComponent() {
        net.runelite.api.Point mouse=client.getMouseCanvasPosition();
        if(mouse==null)return -1;
        for(int i=views.size()-1;i>=0;i--) {
            View v=views.get(i);
            if(!v.covered && !v.parent.isHidden() && CircularOrbArtwork.contains(v.circle,mouse.getX(),mouse.getY()))return v.parent.getId();
        }
        for(int i=nativeViews.size()-1;i>=0;i--) {
            NativeView v=nativeViews.get(i);
            if(!v.parent.isHidden() && v.image.contains(mouse.getX(),mouse.getY()))return v.parent.getId();
        }
        return -1;
    }

    private void applyInput() {
        int winner=hoveredComponent();
        net.runelite.api.Point mouse=client.getMouseCanvasPosition();
        for(View view:views)if(!view.covered) {
            OrbOrder orb=OrbOrder.forComponent(view.parent.getId());
            boolean hovered=winner==view.parent.getId();
            input.apply(view.parent,client.getWidget(orb.target.getBackingId()),client.getWidget(orb.target.getButtonId()),
                client.getWidget(orb.number),view.circle,hovered);
            if(mouse!=null && view.original.contains(mouse.getX(),mouse.getY()) && !hovered)
                hideTree(client.getWidget(InterfaceID.Orbs.TOOLTIP),Collections.newSetFromMap(new IdentityHashMap<>()));
        }
        for(NativeView view:nativeViews)input.nativeInput(view.parent,view.button,view.image.bounds,winner==view.parent.getId());
        if(winner<0 && (shield==null || !shield.hovered()) && !compass.hovered(mouse) || client.isMenuOpen())return;
        for(int id:new int[]{OrbConstants.Widgets.Fixed.MINIMAP_MASK,OrbConstants.Widgets.Classic.MINIMAP_MASK,OrbConstants.Widgets.Modern.MINIMAP_MASK}) {
            Widget map=client.getWidget(id);
            if(map!=null && mouse!=null && WidgetGeometry.bounds(map).contains(mouse.getX(),mouse.getY()))mapInput.block(map,!rendering);
        }
    }
    @Subscribe(priority=-30) public void onPostMenuSort(PostMenuSort event) {
        if(!running || config.editInterface() || client.isMenuOpen() || client.getMenu()==null)return;
        net.runelite.api.Point mouse=client.getMouseCanvasPosition();if(mouse==null)return;

        int winner=hoveredComponent();
        net.runelite.api.MenuEntry[] entries=client.getMenu().getMenuEntries();
        net.runelite.api.MenuEntry[] kept=java.util.Arrays.stream(entries).filter(e->{
            if(compass.coveredMenu(e) || minimap.coveredMenu(e))return false;
            if(winner>=0 && e.getType()!=null && e.getType()!=net.runelite.api.MenuAction.CANCEL
                && !e.getType().name().startsWith("RUNELITE")) {
                OrbOrder top=OrbOrder.forComponent(winner);
                if(!belongs(e,top,client.getWidget(winner)))return false;
            }
            for(NativeView view:nativeViews)if(view.parent.getId()!=winner && belongs(e,view.type,view.parent))return false;
            for(View view:views)if(view.covered || view.parent.getId()!=winner) {
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
    private static boolean belongs(net.runelite.api.MenuEntry entry,OrbOrder orb,Widget parent) {
        if(entry.getParam1()==parent.getId() || entry.getParam1()==orb.target.getButtonId() || entry.getParam1()==orb.target.getBackingId())return true;
        Set<Widget> visited=Collections.newSetFromMap(new IdentityHashMap<>());
        for(Widget widget=entry.getWidget();widget!=null && visited.add(widget);widget=widget.getParent())if(widget==parent)return true;
        return false;
    }
    private static boolean visible(Widget w) {return w!=null && !w.isHidden() && w.getOpacity()<255;}
    private void hide(Widget w) {if(w!=null){opacities.putIfAbsent(w,w.getOpacity());w.setOpacity(255);}}
    private void hideTree(Widget widget,Set<Widget> visited) {
        if(widget==null || !visited.add(widget))return;
        hide(widget);
        for(Widget child:CircularOrbLayers.children(widget))hideTree(child,visited);
    }
    @Override public Dimension render(Graphics2D g) {
        if(running && !config.hideOrbTabs() && !config.editInterface())
            for(NativeView view:nativeViews)if(!view.parent.isHidden()) {
                view.image.paint(g);
                if(view.type==OrbOrder.SPECIAL)for(CircularSpecialMeter meter:specialMeters)meter.paint(g);
                NativeTimerView timer=view.timer;
                Rectangle host=new Rectangle(timer.inset);host.grow(5,5);
                if(nativeTimerCompatibility!=null)nativeTimerCompatibility.paint(g,view.parent.getId(),host,!ownsTimer(view.parent.getId()));
                if(view.type==OrbOrder.SPECIAL && soulreaper!=null) {
                    Rectangle circle=new Rectangle(timer.inset);circle.grow(5,5);soulreaper.paintNativeTimer(g,circle);
                }
                if(timerGeneration==generation && ownsTimer(view.parent.getId()))timers.paintInset(g,timer.inset,timer.number,view.parent.getId(),config,System.nanoTime(),timer.currentPrayer,timer.maximumPrayer);
            }
        if(running && !config.hideOrbTabs() && !config.editInterface())
            for(NativeTimerView view:nativeTimers)if(visible(view.parent) && visible(view.backing) && visible(view.indicator)) {
                Graphics2D draw=(Graphics2D)g.create();
                try {
                    Rectangle host=new Rectangle(view.inset);host.grow(5,5);
                    if(nativeTimerCompatibility!=null)nativeTimerCompatibility.paint(draw,view.parent.getId(),host,!ownsTimer(view.parent.getId()));
                    int opacity=Math.max(view.parent.getOpacity(),Math.max(view.backing.getOpacity(),view.indicator.getOpacity()));
                    draw.setComposite(AlphaComposite.SrcOver.derive((255-opacity)/255f));
                    if(view.parent.getId()==InterfaceID.Orbs.ORB_SPECENERGY)for(CircularSpecialMeter meter:specialMeters)meter.paint(draw);
                    if(timerGeneration==generation && ownsTimer(view.parent.getId()))timers.paintInset(draw,view.inset,view.number,view.parent.getId(),config,System.nanoTime(),view.currentPrayer,view.maximumPrayer);
                } finally {draw.dispose();}
            }
        if(running && config.hideOrbTabs())for(View v:views)if(!v.covered && !v.parent.isHidden()) {
            Graphics2D draw=(Graphics2D)g.create();
            try {
                draw.setComposite(AlphaComposite.SrcOver.derive((255-v.frameOpacity)/255f));
                if(v.parent.getId()==InterfaceID.Orbs.ORB_SPECENERGY)
                    for(CircularSpecialMeter meter:specialMeters)meter.paint(draw);
                artwork.paintBackground(draw,v.circle);
                v.layers.paint(draw,v.circle);
                if(soulreaper!=null && v.parent.getId()==InterfaceID.Orbs.ORB_SPECENERGY)soulreaper.paintNativeTimer(draw,v.circle);
                artwork.paint(draw,v.circle,config.orbRim());
                paintContent(draw,v.circle,v.icon,v.number,v.color,config.orbContent(),v.iconOpacity,v.numberOpacity);
                if(nativeTimerCompatibility!=null)nativeTimerCompatibility.paint(draw,v.parent.getId(),v.circle,!ownsTimer(v.parent.getId()));
                if(timerGeneration==generation && ownsTimer(v.parent.getId()))timers.paint(draw,v.circle,v.parent.getId(),config,System.nanoTime(),v.currentPrayer,v.maximumPrayer);
            } finally {draw.dispose();}
            net.runelite.api.Point mouse = client.getMouseCanvasPosition();
            if (mouse == null || !CircularOrbArtwork.contains(v.circle, mouse.getX(), mouse.getY()))
                input.maskOutsideHover(v.parent);
        }
        rendering=false;mapInput.resumeInput(client);
        return null;
    }

    private boolean ownsTimer(int component) {
        return component!=InterfaceID.Orbs.ORB_SPECENERGY || soulreaper==null || !soulreaper.replacesSpecialTimer();
    }

    private static final class NativeView {
        final OrbOrder type;final Widget parent,button;final NativeOrbArtwork image;final NativeTimerView timer;
        NativeView(OrbOrder type,Widget parent,Widget button,NativeOrbArtwork image,NativeTimerView timer) {
            this.type=type;this.parent=parent;this.button=button;this.image=image;this.timer=timer;
        }
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
