package com.interfacelayout.util;

import java.awt.Point;
import java.awt.Rectangle;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import net.runelite.api.widgets.Widget;

public final class WidgetGeometry
{
    private static final ThreadLocal<Set<Widget>> VISITED = ThreadLocal.withInitial(
        () -> Collections.newSetFromMap(new IdentityHashMap<>()));

    private WidgetGeometry() {}
    public static Point location(Widget widget)
    {
        long position = position(widget);
        return new Point((int) (position >> 32), (int) position);
    }

    private static long position(Widget widget)
    {
        int x = 0, y = 0;
        Set<Widget> visited = VISITED.get();
        if (!visited.isEmpty()) visited = Collections.newSetFromMap(new IdentityHashMap<>());
        try
        {
            for (Widget current = widget; current != null && visited.add(current);)
            {
                x += current.getRelativeX(); y += current.getRelativeY();
                Widget parent = current.getParent();
                if (parent != null) { x -= parent.getScrollX(); y -= parent.getScrollY(); }
                current = parent;
            }
            return ((long) x << 32) | (y & 0xffffffffL);
        }
        finally { visited.clear(); }
    }

    public static Rectangle bounds(Widget widget)
    {
        if (widget == null) return new Rectangle();
        long position = position(widget);
        return new Rectangle((int) (position >> 32), (int) position, widget.getWidth(), widget.getHeight());
    }
}
