package com.interfacelayout.layout.sizing;

import com.interfacelayout.layout.orbs.OrbController;
import com.interfacelayout.InterfaceLayoutConfig;
import com.interfacelayout.layout.orbs.widget.WidgetManager;
import com.interfacelayout.layout.orbs.widget.elements.Orbs;
import com.interfacelayout.util.WidgetGeometry;
import com.interfacelayout.util.WidgetStateStore;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetType;

/** Explicit HUD allowlist: never resizes the minimap, side-panel contents or models. */
@Singleton
public class HudSizingController
{
    private static final Orbs[] ROOTS = {Orbs.HP_ORB_CONTAINER, Orbs.PRAYER_ORB_CONTAINER,
        Orbs.RUN_ORB_CONTAINER, Orbs.SPEC_ORB_CONTAINER, Orbs.XP_DROPS_CONTAINER,
        Orbs.STORE_ORB_CONTAINER, Orbs.ACTIVITY_ORB_CONTAINER, Orbs.WORLD_MAP_CONTAINER,
        Orbs.WIKI_ICON_CONTAINER};
    @Inject private SizingSettings sizing;
    @Inject private WidgetSizing resize;
    @Inject private WidgetManager widgets;
    @Inject private OrbController manager;
    @Inject private WidgetStateStore states;
    @Inject private InterfaceLayoutConfig config;
    private final Set<Integer> nativeScripts = new HashSet<>();
    private final List<OrbTextOverlay.Label> labels = new ArrayList<>();
    private boolean windowOccluded;

    public List<OrbTextOverlay.Label> labels() { return Collections.unmodifiableList(labels); }
    public void clearText() { labels.clear(); }

    public boolean active() {
        if (windowOccluded) return true;
        for (int i = 0; i < ROOTS.length; i++) if (sizing.orb(i) != 100) return true;
        return false;
    }
    public boolean nativeScript(int script) { return nativeScripts.contains(script); }
    public void resetSession() { nativeScripts.clear(); clearText(); windowOccluded = false; }
    public void apply()
    {
        clearText();
        windowOccluded = false;
        if (!manager.isLoggedIn() || manager.isMinimapMinimized()) return;
        for (int i = 0; i < ROOTS.length; i++)
        {
            int percent = sizing.orb(i);
            if (percent == 100) continue;
            Widget root = widgets.getTargetWidget(ROOTS[i]);
            if (root == null || root.isHidden() || root.getWidth() <= 0 || root.getHeight() <= 0) continue;
            List<Part> parts = new ArrayList<>();
            if (!collect(root, root.getId() >>> 16, parts, Collections.newSetFromMap(new IdentityHashMap<>()))) continue;
            // Scrolling content needs its own renderer; these HUD controls normally have none.
            // Leave a native control intact rather than partially scaling an unknown shape.
            if (parts.isEmpty() || parts.stream().anyMatch(part -> part.widget.getScrollX() != 0
                || part.widget.getScrollY() != 0)) continue;
            Point canvas = WidgetGeometry.location(root);
            for (Part part : parts)
            {
                Widget widget = part.widget;
                remember(widget.getOnVarTransmitListener());
                remember(widget.getOnLoadListener());
                remember(widget.getOnOpListener());
                resize.size(widget, SizingSettings.pixels(part.bounds.width, percent), SizingSettings.pixels(part.bounds.height, percent));
                if (widget != root)
                    states.position(widget, SizingSettings.offset(part.bounds.x, percent), SizingSettings.offset(part.bounds.y, percent));
                // Only replace passive, single-line numeric labels. Unknown text stays native.
                if (config.scaleOrbText() && i < 4 && !widget.isHidden() && OrbTextOverlay.supports(widget))
                {
                    labels.add(new OrbTextOverlay.Label(root, widget, percent));
                    states.hide(widget, true);
                }
            }
            Point parent = WidgetGeometry.location(root.getParent());
            states.position(root, canvas.x - parent.x, canvas.y - parent.y);
        }
    }
    /** Apply after all positioning, so native art, labels, rings and input hide together. */
    public void occlude(List<Rectangle> windows)
    {
        if (!manager.isLoggedIn() || manager.isMinimapMinimized() || windows.isEmpty()) return;
        for (Orbs target : ROOTS)
        {
            Widget root = widgets.getTargetWidget(target);
            if (root == null || root.isHidden()) continue;
            Rectangle bounds = WidgetGeometry.bounds(root);
            if (bounds.isEmpty() || windows.stream().noneMatch(window -> window.intersects(bounds))) continue;
            // Release our hiding around native callbacks before sampling game visibility again.
            List<Part> parts = new ArrayList<>();
            if (collect(root, root.getId() >>> 16, parts, Collections.newSetFromMap(new IdentityHashMap<>())))
                for (Part part : parts)
                {
                    remember(part.widget.getOnVarTransmitListener());
                    remember(part.widget.getOnLoadListener());
                    remember(part.widget.getOnOpListener());
                }
            states.hide(root, true);
            windowOccluded = true;
        }
    }
    private void remember(Object[] listener)
    {
        if (listener != null && listener.length > 0 && listener[0] instanceof Integer)
            nativeScripts.add((Integer) listener[0]);
    }
    private static boolean collect(Widget widget, int group, List<Part> parts, Set<Widget> seen)
    {
        if (widget == null || !seen.add(widget)) return true;
        if (widget.getId() >>> 16 != group || seen.size() > 256) return false;
        // Reject the whole control when an unexpected model or foreign nested interface is present.
        if (widget.getType() == WidgetType.MODEL) return false;
        parts.add(new Part(widget));
        for (Widget[] children : new Widget[][]{widget.getStaticChildren(), widget.getDynamicChildren(), widget.getNestedChildren()})
            if (children != null) for (Widget child : children) if (!collect(child, group, parts, seen)) return false;
        return true;
    }
    private static final class Part
    {
        final Widget widget;
        final Rectangle bounds;
        Part(Widget widget) {
            this.widget = widget;
            bounds = new Rectangle(widget.getRelativeX(), widget.getRelativeY(), widget.getWidth(), widget.getHeight());
        }
    }
}
