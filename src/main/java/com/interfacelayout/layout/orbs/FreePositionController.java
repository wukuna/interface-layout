package com.interfacelayout.layout.orbs;

import com.interfacelayout.layout.orbs.OrbLayout;
import com.interfacelayout.layout.orbs.OrbController;
import com.interfacelayout.InterfaceLayoutConfig;
import com.interfacelayout.layout.orbs.widget.TargetWidget;
import com.interfacelayout.layout.orbs.widget.WidgetManager;
import com.interfacelayout.layout.orbs.widget.elements.Orbs;
import com.interfacelayout.layout.orbs.widget.elements.Minimap;
import com.interfacelayout.layout.orbs.widget.elements.Compass;
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
        Compass.MODERN_COMPASS, Minimap.CLASSIC_MINIMAP_MASK, Minimap.MODERN_MINIMAP_MASK);
    @Inject private Client client;
    @Inject private InterfaceLayoutConfig config;
    @Inject private OrbController manager;
    @Inject private ConfigManager configs;
    @Inject private WidgetManager widgets;
    @Inject private EditManager editor;
    @Inject private WidgetStateStore states;
    @Inject private WidgetBoundsExpander expander;

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
            Point saved = configs.getConfiguration("interfacelayout", key(target), Point.class);
            hasSavedPosition |= saved != null;
            if (saved != null || target == Orbs.WORLD_MAP_CONTAINER || target == Orbs.WIKI_ICON_CONTAINER)
                positions.put(target, saved == null ? WidgetGeometry.location(widget) : saved);
        }

        if (hasSavedPosition) positions.forEach(this::move);
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
    public void move(TargetWidget target, Point desired)
    {
        if (target.isLogoutX() && com.interfacelayout.layout.menu.MenuStoneController.managesModernLogout(config) && !manager.isFixedMode() && !manager.isClassicResizable()) return;
        Widget widget = widgets.getTargetWidget(target);
        if (widget == null || widget.isHidden()) return;
        Rectangle bounds = bounds(target, widget);
        Point original = WidgetGeometry.location(widget);
        Point location = LayoutGeometry.clamp(desired, bounds.width, bounds.height,
            client.getCanvasWidth(), client.getCanvasHeight());
        location.translate(original.x - bounds.x, original.y - bounds.y);
        Widget attachedCompass = isMinimap(target)
            && configs.getConfiguration("interfacelayout", key(compassTarget()), Point.class) == null
            ? widgets.getTargetWidget(compassTarget()) : null;
        Point compassLocation = attachedCompass == null || attachedCompass.isHidden() ? null
            : WidgetGeometry.location(attachedCompass);
        Widget related = target.isLogoutX() ? widgets.getTargetWidget(Orbs.LOGOUT_X_STONE)
            : target == Compass.MODERN_COMPASS ? widgets.getTargetWidget(Compass.MODERN_COMPASS_OPTIONS)
            : target == Compass.CLASSIC_COMPASS ? widgets.getTargetWidget(Compass.CLASSIC_COMPASS_OPTIONS) : null;
        Point delta = related == null ? new Point() : new Point(WidgetGeometry.location(related).x - WidgetGeometry.location(widget).x,
            WidgetGeometry.location(related).y - WidgetGeometry.location(widget).y);
        moveWidget(widget, location);
        if (related != null) moveWidget(related, new Point(location.x + delta.x, location.y + delta.y));
        if (compassLocation != null)
        {
            compassLocation.translate(location.x - original.x, location.y - original.y);
            move(compassTarget(), compassLocation);
        }
    }
    private void moveWidget(Widget widget, Point location)
    {
        expander.allowCanvasPosition(widget);
        Point relative = LayoutGeometry.relative(location, WidgetGeometry.bounds(widget.getParent()));
        states.position(widget, relative.x, relative.y);
        states.capture(widget);
        widget.setNoClickThrough(config.enableNoClickthrough());
    }
    public void save(TargetWidget target, Point point) { configs.setConfiguration("interfacelayout", key(target), point); }
    public void reset(TargetWidget target) { configs.unsetConfiguration("interfacelayout", key(target)); }
    public void resetAll()
    {
        for (TargetWidget[] choices : EditManager.EDIT_TARGETS)
            for (String mode : new String[]{"fixed", "classic", "modern"})
                configs.unsetConfiguration("interfacelayout", "free_" + mode + "_" + choices[0].getComponentId() + "_" + choices[0].getArrayId());
        for (TargetWidget target : EXTRA_TARGETS)
            for (String mode : new String[]{"fixed", "classic", "modern"})
                configs.unsetConfiguration("interfacelayout", "free_" + mode + "_" + target.getComponentId() + "_" + target.getArrayId());
    }
}
