package com.interfacelayout.layout.orbs;

import com.interfacelayout.layout.orbs.OrbLayout;
import com.interfacelayout.layout.orbs.OrbController;
import com.interfacelayout.InterfaceLayoutConfig;
import com.interfacelayout.layout.orbs.widget.TargetWidget;
import com.interfacelayout.layout.orbs.widget.WidgetManager;
import com.interfacelayout.layout.orbs.widget.elements.Orbs;
import com.interfacelayout.layout.orbs.widget.elements.Minimap;
import com.interfacelayout.layout.orbs.widget.elements.Compass;
import com.interfacelayout.layout.orbs.widget.elements.Gravestone;
import com.interfacelayout.layout.orbs.widget.layout.edit.EditManager;
import com.interfacelayout.util.LayoutGeometry;
import com.interfacelayout.util.WidgetBoundsExpander;
import com.interfacelayout.util.WidgetGeometry;
import com.interfacelayout.util.WidgetStateStore;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.widgets.Widget;
import net.runelite.client.config.ConfigManager;

@Singleton
public class FreePositionController
{
    public static final List<TargetWidget> EXTRA_TARGETS = List.of(Compass.CLASSIC_COMPASS,
        Compass.MODERN_COMPASS, Minimap.CLASSIC_MINIMAP_MASK, Minimap.MODERN_MINIMAP_MASK,
        Gravestone.FIXED, Gravestone.CLASSIC, Gravestone.MODERN);
    @Inject private Client client;
    @Inject private InterfaceLayoutConfig config;
    @Inject private OrbController manager;
    @Inject private ConfigManager configs;
    @Inject private WidgetManager widgets;
    @Inject private EditManager editor;
    @Inject private WidgetStateStore states;
    @Inject private WidgetBoundsExpander expander;
    @Inject private com.interfacelayout.util.LayoutPositionStore savedPositions;
    @Inject private com.interfacelayout.layout.menu.MenuButtonSprites sprites;
    private final CircularOrbArtwork artwork=new CircularOrbArtwork();
    private final Map<String, Point> resolvedPositions = new LinkedHashMap<>();

    private final Map<TargetWidget,Rectangle> overlayBounds=new LinkedHashMap<>();
    private final Map<TargetWidget,Point> overlayOffsets=new LinkedHashMap<>();

    public boolean enabled() { return config.layout() == OrbLayout.FREE_POSITION; }
    private List<TargetWidget> targets()
    {
        List<TargetWidget> result = new ArrayList<>();
        for (TargetWidget[] choices : EditManager.EDIT_TARGETS)
        {
            TargetWidget target = choices[0];
            if (target.isLogoutX() && com.interfacelayout.layout.menu.MenuStoneController.managesModernLogout(config) && !manager.isFixedMode() && !manager.isClassicResizable()) continue;
            if (!target.isMinimapButton() && !target.isCompass() && !editor.blockEditing(target)) result.add(target);
        }
        if (!manager.isFixedMode())
        {
            if (!config.hideMinimap()) result.add(manager.isClassicResizable()
                ? Minimap.CLASSIC_MINIMAP_MASK : Minimap.MODERN_MINIMAP_MASK);
            result.add(compassTarget());
        }
        if(!config.hideGravestone())result.add(manager.isFixedMode()?Gravestone.FIXED:
            manager.isClassicResizable()?Gravestone.CLASSIC:Gravestone.MODERN);
        return result;
    }
    private TargetWidget compassTarget()
    {
        return manager.isClassicResizable() ? Compass.CLASSIC_COMPASS : Compass.MODERN_COMPASS;
    }
    private static boolean isMinimap(TargetWidget target)
    {
        return target == Minimap.CLASSIC_MINIMAP_MASK || target == Minimap.MODERN_MINIMAP_MASK;
    }
    private static Rectangle bounds(TargetWidget target, Widget widget)
    {
        Rectangle bounds = WidgetGeometry.bounds(widget);
        return isMinimap(target) && !bounds.isEmpty() ? CircularOrbArtwork.around(bounds) : bounds;
    }
    private String key(TargetWidget target)
    {
        String mode = manager.isFixedMode() ? "fixed" : manager.isClassicResizable() ? "classic" : "modern";
        return "free_" + mode + "_" + target.getComponentId() + "_" + target.getArrayId();
    }
    public void apply()
    {
        overlayBounds.clear();overlayOffsets.clear();
        if (!enabled() || !manager.isLoggedIn() || manager.isMinimapMinimized()) return;
        if (config.hideMinimap() && !manager.isFixedMode())
            for (TargetWidget component : Minimap.COMPONENTS) states.hide(widgets.getTargetWidget(component), true);
        if (config.hideCompass()) for (TargetWidget component : Compass.values()) states.hide(widgets.getTargetWidget(component), true);
        Map<TargetWidget, Point> positions = new LinkedHashMap<>();
        boolean hasSavedPosition = false;

        for (TargetWidget target : targets())
        {
            Widget widget = widgets.getTargetWidget(target);
            if (widget == null || widget.isHidden()) continue;
            Rectangle area = bounds(target, widget);
            Point saved = resolvedPositions.get(key(target));
            if (saved == null) saved = savedPositions.load(key(target), area.width, area.height);
            boolean aligned=alignment(target)!=null;
            hasSavedPosition |= saved != null || aligned;
            if (saved != null || aligned || target == Orbs.WORLD_MAP_CONTAINER || target == Orbs.WIKI_ICON_CONTAINER)
                positions.put(target, saved == null ? area.getLocation() : saved);
        }

        if (hasSavedPosition) positions.forEach(this::move);
        for(Map.Entry<TargetWidget,Rectangle> entry:bounds().entrySet()) {
            TargetWidget target=entry.getKey();Rectangle nativeBounds=entry.getValue();
            Rectangle visible=config.hideOrbTabs() && !config.editInterface() && OrbOrder.forComponent(target.getComponentId())!=null
                ? visibleBounds(target,widgets.getTargetWidget(target)):nativeBounds;
            overlayBounds.put(target,new Rectangle(visible));
            overlayOffsets.put(target,new Point(nativeBounds.x-visible.x,nativeBounds.y-visible.y));
        }
    }
    public Map<TargetWidget, Rectangle> bounds()
    {
        Map<TargetWidget, Rectangle> result = new LinkedHashMap<>();
        if (!enabled() || !manager.isLoggedIn() || manager.isMinimapMinimized()) return result;
        for (TargetWidget target : targets())
        {
            Widget widget = widgets.getTargetWidget(target);
            if (widget != null && !widget.isHidden()) result.put(target, bounds(target, widget));
        }
        return result;
    }
    public Map<TargetWidget,Rectangle> overlayBounds() { return new LinkedHashMap<>(overlayBounds); }
    public void applyOverlayPosition(TargetWidget target,Point point) {
        Point nativePoint=new Point(point),offset=overlayOffsets.get(target);
        if(offset!=null)nativePoint.translate(offset.x,offset.y);
        applyPosition(target,nativePoint);
    }
    public void move(TargetWidget target, Point desired)
    {
        if (target.isLogoutX() && com.interfacelayout.layout.menu.MenuStoneController.managesModernLogout(config) && !manager.isFixedMode() && !manager.isClassicResizable()) return;
        Widget widget = widgets.getTargetWidget(target);
        if (widget == null || widget.isHidden()) return;
        Rectangle bounds = bounds(target, widget);
        Point original = WidgetGeometry.location(widget);
        Rectangle visible=visibleBounds(target,widget);
        Point visibleDesired=new Point(desired.x+visible.x-bounds.x,desired.y+visible.y-bounds.y);
        Point location=LayoutGeometry.clamp(visibleDesired,visible.width,visible.height,client.getCanvasWidth(),client.getCanvasHeight());
        location.translate(bounds.x-visible.x,bounds.y-visible.y);
        EdgeAlignment edge=alignment(target);
        if(edge!=null)location=edge.place(bounds,visibleBounds(target,widget),desired,client.getCanvasWidth(),client.getCanvasHeight());
        location.translate(original.x - bounds.x, original.y - bounds.y);
        Widget attachedCompass = isMinimap(target)
            && !resolvedPositions.containsKey(key(compassTarget())) && savedPosition(compassTarget()) == null
            ? widgets.getTargetWidget(compassTarget()) : null;
        Point compassLocation = attachedCompass == null || attachedCompass.isHidden() ? null
            : WidgetGeometry.location(attachedCompass);
        Widget related = target.isLogoutX() ? widgets.getTargetWidget(Orbs.LOGOUT_X_STONE)
            : target == Compass.MODERN_COMPASS ? widgets.getTargetWidget(Compass.MODERN_COMPASS_OPTIONS)
            : target == Compass.CLASSIC_COMPASS ? widgets.getTargetWidget(Compass.CLASSIC_COMPASS_OPTIONS) : null;
        Point delta = related == null ? new Point() : new Point(WidgetGeometry.location(related).x - WidgetGeometry.location(widget).x,
            WidgetGeometry.location(related).y - WidgetGeometry.location(widget).y);
        Map<Widget, Point> surround = new LinkedHashMap<>();
        if (isMinimap(target) && config.minimapFrame() == InterfaceLayoutConfig.MinimapFrame.NATIVE)
            for (TargetWidget part : Minimap.COMPONENTS)
            {
                if (isMinimap(part) || (part.getComponentId() >>> 16) != (target.getComponentId() >>> 16)) continue;
                Widget decoration = widgets.getTargetWidget(part);
                if (decoration == null || decoration.isHidden()) continue;
                Point point = WidgetGeometry.location(decoration);
                point.translate(location.x - original.x, location.y - original.y);
                surround.put(decoration, point);
            }
        moveWidget(widget, location);
        surround.forEach(this::positionWidget);
        if (related != null) moveWidget(related, new Point(location.x + delta.x, location.y + delta.y));
        if (compassLocation != null)
        {
            compassLocation.translate(location.x - original.x, location.y - original.y);
            move(compassTarget(), compassLocation);
        }
    }
    private void moveWidget(Widget widget, Point location)
    {
        positionWidget(widget, location);
        widget.setNoClickThrough(true);
    }
    private void positionWidget(Widget widget, Point location)
    {
        expander.allowCanvasPosition(widget);
        Point relative = LayoutGeometry.relative(location, WidgetGeometry.bounds(widget.getParent()));
        if(config.hideOrbTabs() && OrbOrder.forComponent(widget.getId())!=null)
            states.positionUnclipped(widget,relative.x,relative.y);
        else states.position(widget, relative.x, relative.y);
    }
    public Point savedPosition(TargetWidget target) { return configs.getConfiguration("interfacelayout", key(target), Point.class); }
    public EdgeAlignment alignment(TargetWidget target) { return EdgeAlignment.parse(configs.getConfiguration("interfacelayout",key(target)+"_edge")); }
    public void align(TargetWidget target,EdgeAlignment edge)
    { resolvedPositions.remove(key(target));configs.setConfiguration("interfacelayout",key(target)+"_edge",edge); }
    public void clearAlignment(TargetWidget target) { configs.unsetConfiguration("interfacelayout",key(target)+"_edge"); }
    private Rectangle visibleBounds(TargetWidget target,Widget widget)
    {
        Rectangle area=bounds(target,widget);
        if(isMinimap(target) && config.minimapFrame()==InterfaceLayoutConfig.MinimapFrame.NATIVE && sprites!=null)
        {
            for(TargetWidget part:Minimap.COMPONENTS)
            {
                if(isMinimap(part) || (part.getComponentId()>>>16)!=(target.getComponentId()>>>16))continue;
                Widget decoration=widgets.getTargetWidget(part);
                if(decoration==null || decoration.isHidden())continue;
                java.awt.image.BufferedImage image=sprites.getPadded(decoration);
                if(image!=null)area=area.union(spriteBounds(decoration,image,false));
            }
            return area;
        }
        if(target==Compass.CLASSIC_COMPASS || target==Compass.MODERN_COMPASS)
            return config.minimapFrame()==InterfaceLayoutConfig.MinimapFrame.CIRCULAR?CircularOrbArtwork.around(area):area;
        OrbOrder orb=OrbOrder.forComponent(target.getComponentId());
        if(orb==null || sprites==null)return area;
        Widget fill=client.getWidget(target.getIndicatorId());
        if(config.hideOrbTabs() && fill!=null)
        {
            Rectangle circle=artwork.aroundFill(WidgetGeometry.bounds(fill),sprites.getPadded(fill));
            boolean timer=!config.disableOrbTimers() && (orb==OrbOrder.HITPOINTS && config.orbHitpointsTimer()
                || orb==OrbOrder.PRAYER && config.orbPrayerTimer() || orb==OrbOrder.SPECIAL && config.orbSpecialTimer());
            boolean tag=timer && OrbTimerStyles.resolve(config,target.getComponentId())==InterfaceLayoutConfig.NativeTimerStyle.TAG_METERS;
            Rectangle visible=new Rectangle(circle);if(tag)visible.height+=orb==OrbOrder.PRAYER && config.prayerOrbDisplay()==InterfaceLayoutConfig.PrayerDisplay.BOTH?7:4;
            if(orb==OrbOrder.SPECIAL && !config.hideExtraSpecialAttackBar())
            {
                CircularSpecialMeter meter=CircularSpecialMeter.capture(widget,circle,tag?7:3,sprites);
                if(meter!=null)visible=visible.union(meter.bounds);
            }
            return visible;
        }
        Widget backing=client.getWidget(target.getBackingId());
        java.awt.image.BufferedImage image=sprites.getPadded(backing);
        if(backing==null || image==null)return area;
        Rectangle visible=spriteBounds(backing,image,orb.mirrored(config));
        if(!config.disableOrbTimers() && OrbTimerStyles.resolve(config,target.getComponentId())==InterfaceLayoutConfig.NativeTimerStyle.QUIET_RIMS && fill!=null)
        {
            Rectangle timer=artwork.aroundFill(WidgetGeometry.bounds(fill),sprites.getPadded(fill));
            if(orb.mirrored(config))timer=CircularOrbInput.reflected(WidgetGeometry.bounds(widget),timer);
            visible=visible.union(timer);
        }
        if(orb==OrbOrder.SPECIAL && !config.hideExtraSpecialAttackBar() && fill!=null) {
            Rectangle circle=artwork.aroundFill(WidgetGeometry.bounds(fill),sprites.getPadded(fill));
            if(orb.mirrored(config))circle=CircularOrbInput.reflected(WidgetGeometry.bounds(widget),circle);
            CircularSpecialMeter meter=CircularSpecialMeter.capture(widget,circle,3,sprites);
            if(meter!=null)visible=visible.union(meter.bounds);
        }
        return visible;
    }
    private Rectangle spriteBounds(Widget widget,java.awt.image.BufferedImage image,boolean mirrored)
    {
        Rectangle frame=WidgetGeometry.bounds(widget),pixels=artwork.visibleFill(image);
        int x=mirrored?image.getWidth()-pixels.x-pixels.width:pixels.x;
        return new Rectangle(frame.x+x*frame.width/image.getWidth(),frame.y+pixels.y*frame.height/image.getHeight(),
            (pixels.width*frame.width+image.getWidth()-1)/image.getWidth(),(pixels.height*frame.height+image.getHeight()-1)/image.getHeight());
    }
    public void applyPosition(TargetWidget target, Point point) { resolvedPositions.put(key(target), new Point(point)); }
    public void clearPosition(TargetWidget target) { resolvedPositions.remove(key(target)); }
    public void resetSession() { resolvedPositions.clear();overlayBounds.clear();overlayOffsets.clear(); }
    public void save(TargetWidget target, Point point) { resolvedPositions.remove(key(target)); savedPositions.save(key(target), point); }
    public void reset(TargetWidget target) { clearAlignment(target);resolvedPositions.remove(key(target)); savedPositions.reset(key(target)); }
    public void resetAll()
    {
        resetSession();
        for (TargetWidget[] choices : EditManager.EDIT_TARGETS)
            for (String mode : new String[]{"fixed", "classic", "modern"})
                savedPositions.reset("free_" + mode + "_" + choices[0].getComponentId() + "_" + choices[0].getArrayId());
        for (TargetWidget target : EXTRA_TARGETS)
            for (String mode : new String[]{"fixed", "classic", "modern"})
                savedPositions.reset("free_" + mode + "_" + target.getComponentId() + "_" + target.getArrayId());
        List<TargetWidget> all=new ArrayList<>(EXTRA_TARGETS);
        for(TargetWidget[] choices:EditManager.EDIT_TARGETS)all.add(choices[0]);
        for(TargetWidget target:all)for(String mode:new String[]{"fixed","classic","modern"})
            configs.unsetConfiguration("interfacelayout","free_"+mode+"_"+target.getComponentId()+"_"+target.getArrayId()+"_edge");
    }
}
