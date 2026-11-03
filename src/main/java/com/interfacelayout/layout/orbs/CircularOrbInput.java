package com.interfacelayout.layout.orbs;

import com.interfacelayout.util.WidgetGeometry;
import java.awt.Rectangle;
import java.util.IdentityHashMap;
import java.util.Map;
import net.runelite.api.Client;
import net.runelite.api.widgets.Widget;

final class CircularOrbInput {
    private final Map<Widget, Geometry> geometry = new IdentityHashMap<>();
    private final Map<Widget, Boolean> blocking = new IdentityHashMap<>();

    void apply(Widget parent, Widget backing, Widget button, Widget number, Rectangle circle) {

        clip(backing, circle);
        clip(button, circle);
        clip(number, circle);
        if (parent.getNoClickThrough()) {
            block(parent, false);

            block(backing, true);
        }
        Widget[] children = parent.getChildren();
        if (children != null) for (Widget child : children)
            if (child != null && child.getNoClickThrough()) clip(child, circle);
    }

    private void block(Widget widget, boolean value) {
        if (widget.getNoClickThrough() == value) return;
        blocking.putIfAbsent(widget, widget.getNoClickThrough());
        widget.setNoClickThrough(value);
    }

    private void clip(Widget widget, Rectangle circle) {
        if (widget == null || widget.getParent() == null || geometry.containsKey(widget)) return;

        Rectangle original = WidgetGeometry.bounds(widget), parent = WidgetGeometry.bounds(widget.getParent());
        Rectangle clipped = original.intersection(circle);
        if (clipped.equals(original)) return;
        int x = clipped.x - parent.x + widget.getParent().getScrollX();
        int y = clipped.y - parent.y + widget.getParent().getScrollY();
        Geometry state = new Geometry(widget, x, y, Math.max(0, clipped.width), Math.max(0, clipped.height));
        geometry.put(widget, state);
        state.apply(widget);
    }

    void restore(Client client) {
        geometry.forEach((widget, state) -> { if (current(client, widget)) state.restore(widget); });
        blocking.forEach((widget, before) -> {
            if (current(client, widget) && widget.getNoClickThrough() != before) widget.setNoClickThrough(before);
        });
        geometry.clear(); blocking.clear();
    }

    private static boolean current(Client client, Widget widget) {
        Widget current = client.getWidget(widget.getId());
        return current == widget || current != null && widget.getIndex() >= 0 && current.getChild(widget.getIndex()) == widget;
    }

    private static final class Geometry {
        final int x, y, width, height, xMode, yMode, widthMode, heightMode;
        final int nextX, nextY, nextWidth, nextHeight;
        Geometry(Widget w, int nx, int ny, int nw, int nh) {
            x=w.getOriginalX();y=w.getOriginalY();width=w.getOriginalWidth();height=w.getOriginalHeight();
            xMode=w.getXPositionMode();yMode=w.getYPositionMode();widthMode=w.getWidthMode();heightMode=w.getHeightMode();
            nextX=nx;nextY=ny;nextWidth=nw;nextHeight=nh;
        }
        void apply(Widget w) {
            w.setXPositionMode(0);w.setYPositionMode(0);w.setWidthMode(0);w.setHeightMode(0);
            w.setOriginalX(nextX);w.setOriginalY(nextY);w.setOriginalWidth(nextWidth);w.setOriginalHeight(nextHeight);w.revalidate();
        }
        void restore(Widget w) {

            if(w.getOriginalX()==nextX && w.getXPositionMode()==0) {
                w.setOriginalX(x);w.setXPositionMode(xMode);
            }
            if(w.getOriginalY()==nextY && w.getYPositionMode()==0) {
                w.setOriginalY(y);w.setYPositionMode(yMode);
            }
            if(w.getOriginalWidth()==nextWidth && w.getWidthMode()==0) {
                w.setOriginalWidth(width);w.setWidthMode(widthMode);
            }
            if(w.getOriginalHeight()==nextHeight && w.getHeightMode()==0) {
                w.setOriginalHeight(height);w.setHeightMode(heightMode);
            }
            w.revalidate();
        }
    }
}
