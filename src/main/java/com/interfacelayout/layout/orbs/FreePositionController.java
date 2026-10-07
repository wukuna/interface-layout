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
            if (!target.isMinimapButton() && !target.isCompass() && !editor.blockEditing(target)) result.add(target);
        }
        if (!manager.isFixedMode()) result.add(manager.isClassicResizable()
            ? Compass.CLASSIC_COMPASS : Compass.MODERN_COMPASS);
        return result;
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
        for (TargetWidget target : targets())
        {
            Widget widget = widgets.getTargetWidget(target);
            if (widget == null || widget.isHidden()) continue;
            Point saved = configs.getConfiguration("interfacelayout", key(target), Point.class);
            if (saved != null) move(target, saved);
        }
    }
    public Map<TargetWidget, Rectangle> bounds()
    {
        Map<TargetWidget, Rectangle> result = new LinkedHashMap<>();
        if (!enabled() || !manager.isLoggedIn()) return result;
        for (TargetWidget target : targets())
        {
            Widget widget = widgets.getTargetWidget(target);
            if (widget != null && !widget.isHidden()) result.put(target, WidgetGeometry.bounds(widget));
        }
        return result;
    }
    public void move(TargetWidget target, Point desired)
    {
        Widget widget = widgets.getTargetWidget(target);
        if (widget == null || widget.isHidden()) return;
        Point location = LayoutGeometry.clamp(desired, widget.getWidth(), widget.getHeight(),
            client.getCanvasWidth(), client.getCanvasHeight());
        Widget related = target.isLogoutX() ? widgets.getTargetWidget(Orbs.LOGOUT_X_STONE)
            : target == Compass.MODERN_COMPASS ? widgets.getTargetWidget(Compass.MODERN_COMPASS_OPTIONS)
            : target == Compass.CLASSIC_COMPASS ? widgets.getTargetWidget(Compass.CLASSIC_COMPASS_OPTIONS) : null;
        Point delta = related == null ? new Point() : new Point(WidgetGeometry.location(related).x - WidgetGeometry.location(widget).x,
            WidgetGeometry.location(related).y - WidgetGeometry.location(widget).y);
        moveWidget(widget, location);
        if (related != null) moveWidget(related, new Point(location.x + delta.x, location.y + delta.y));
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
        for (TargetWidget target : new TargetWidget[]{Compass.MODERN_COMPASS, Compass.CLASSIC_COMPASS})
            for (String mode : new String[]{"fixed", "classic", "modern"})
                configs.unsetConfiguration("interfacelayout", "free_" + mode + "_" + target.getComponentId() + "_" + target.getArrayId());
    }
}
