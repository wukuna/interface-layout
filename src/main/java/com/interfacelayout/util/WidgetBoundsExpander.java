package com.interfacelayout.util;

import java.awt.Point;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetSizeMode;

/** Expands clipping ancestors, preserving the canvas positions of their native children. */
@Singleton
public class WidgetBoundsExpander
{
    @Inject private Client client;
    @Inject private WidgetStateStore states;
    private final Map<Widget, Boolean> expanded = new IdentityHashMap<>();

    public void clear() { expanded.clear(); }

    /** Only layers whose clipping bounds were actually enlarged in this layout. */
    public boolean isExpanded(int componentId)
    {
        return expanded.keySet().stream().anyMatch(widget -> widget.getId() == componentId);
    }

    public void allowCanvasPosition(Widget widget)
    {
        List<Widget> ancestors = new ArrayList<>();
        for (Widget parent = widget.getParent(); parent != null && ancestors.size() < 16; parent = parent.getParent())
        {
            ancestors.add(parent);
            Rectangle bounds = WidgetGeometry.bounds(parent);
            if (bounds.x == 0 && bounds.y == 0 && bounds.width >= client.getCanvasWidth()
                && bounds.height >= client.getCanvasHeight()) break;
        }
        Collections.reverse(ancestors);
        for (Widget parent : ancestors) expand(parent);
    }

    public void allowChildrenAcrossCanvas(Widget parent)
    {
        allowCanvasPosition(parent);
        expand(parent);
    }

    private void expand(Widget parent)
    {
        if (expanded.containsKey(parent)) return;
        Rectangle bounds = WidgetGeometry.bounds(parent);
        if (bounds.x == 0 && bounds.y == 0 && bounds.width >= client.getCanvasWidth()
            && bounds.height >= client.getCanvasHeight()) return;
        expanded.put(parent, true);
        Map<Widget, Point> positions = new IdentityHashMap<>();
        collect(parent.getStaticChildren(), positions);
        collect(parent.getDynamicChildren(), positions);
        collect(parent.getNestedChildren(), positions);
        // Freeze sibling sizes before changing their parent; MINUS-sized minimap layers
        // would otherwise grow to canvas size together with the clipping ancestor.
        positions.keySet().forEach(child -> {
            states.capture(child);
            int width = child.getWidth(), height = child.getHeight();
            child.setOriginalWidth(width); child.setOriginalHeight(height);
            child.setWidthMode(WidgetSizeMode.ABSOLUTE); child.setHeightMode(WidgetSizeMode.ABSOLUTE);
        });
        states.capture(parent);
        Widget grandparent = parent.getParent();
        Rectangle outer = WidgetGeometry.bounds(grandparent);
        states.position(parent, -outer.x, -outer.y);
        parent.setOriginalWidth(client.getCanvasWidth()); parent.setOriginalHeight(client.getCanvasHeight());
        parent.setWidthMode(WidgetSizeMode.ABSOLUTE); parent.setHeightMode(WidgetSizeMode.ABSOLUTE);
        // Expanding a layer must not create an invisible canvas-sized input blocker.
        parent.setNoClickThrough(false);
        parent.revalidate();
        Rectangle now = WidgetGeometry.bounds(parent);
        positions.forEach((child, canvas) -> {
            states.position(child, canvas.x - now.x, canvas.y - now.y);
            child.revalidate();
        });
    }

    private static void collect(Widget[] children, Map<Widget, Point> positions)
    {
        if (children == null) return;
        for (Widget child : children)
            if (child != null) positions.put(child, WidgetGeometry.location(child));
    }
}
