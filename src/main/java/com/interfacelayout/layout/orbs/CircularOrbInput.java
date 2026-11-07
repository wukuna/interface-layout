package com.interfacelayout.layout.orbs;

import com.interfacelayout.util.WidgetGeometry;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.Client;
import net.runelite.api.widgets.Widget;

final class CircularOrbInput {
    private final Map<Widget, Geometry> geometry = new IdentityHashMap<>();
    private final List<Widget> order = new ArrayList<>();
    private final Map<Widget, Boolean> blocking = new IdentityHashMap<>();

    void apply(Widget parent, Widget backing, Widget button, Widget number, Rectangle circle, boolean hovered) {
        Rectangle active = hovered ? circle : new Rectangle(circle.x + circle.width / 2, circle.y + circle.height / 2, 0, 0);
        List<Widget> children = CircularOrbLayers.children(parent);
        for (Widget widget : new Widget[]{backing, button, number})
            if (widget != null && widget.getParent() == parent && !children.contains(widget)) children.add(widget);
        Map<Widget, Rectangle> positions = new IdentityHashMap<>();
        for (Widget child : children) positions.put(child, WidgetGeometry.bounds(child));
        setBounds(parent, active);
        for (Widget child : children) setBounds(child, positions.get(child));
        clip(backing, active);
        clip(button, active);
        clip(number, active);
        if (parent.getNoClickThrough()) {
            block(parent, false);
            if (backing != null) block(backing, hovered);
        }
        for (Widget child : children) if (child.getNoClickThrough()) {
            clip(child, active);
            if (!hovered) block(child, false);
        }
    }

    private void block(Widget widget, boolean value) {
        if (widget.getNoClickThrough() == value) return;
        blocking.putIfAbsent(widget, widget.getNoClickThrough());
        widget.setNoClickThrough(value);
    }

    private void clip(Widget widget, Rectangle circle) {
        if (widget == null) return;
        Rectangle clipped = WidgetGeometry.bounds(widget).intersection(circle);
        clipped.width = Math.max(0, clipped.width); clipped.height = Math.max(0, clipped.height);
        setBounds(widget, clipped);
    }

    private void setBounds(Widget widget, Rectangle bounds) {
        Rectangle parent = WidgetGeometry.bounds(widget.getParent());
        int x = bounds.x - parent.x + (widget.getParent() == null ? 0 : widget.getParent().getScrollX());
        int y = bounds.y - parent.y + (widget.getParent() == null ? 0 : widget.getParent().getScrollY());
        Geometry state = geometry.get(widget);
        if (state == null) {
            state = new Geometry(widget);
            geometry.put(widget, state); order.add(widget);
        }
        state.apply(widget, x, y, bounds.width, bounds.height);
    }

    void restore(Client client) {
        for (Widget widget : order) if (current(client, widget)) geometry.get(widget).restore(widget);
        blocking.forEach((widget, before) -> {
            if (current(client, widget) && widget.getNoClickThrough() != before) widget.setNoClickThrough(before);
        });
        geometry.clear(); order.clear(); blocking.clear();
    }

    static boolean current(Client client, Widget widget) {
        Widget current = client.getWidget(widget.getId());
        return current == widget || current != null && widget.getIndex() >= 0 && current.getChild(widget.getIndex()) == widget;
    }

    private static final class Geometry {
        final int x, y, width, height, xMode, yMode, widthMode, heightMode;
        int nextX, nextY, nextWidth, nextHeight;
        Geometry(Widget w) {
            x=w.getOriginalX();y=w.getOriginalY();width=w.getOriginalWidth();height=w.getOriginalHeight();
            xMode=w.getXPositionMode();yMode=w.getYPositionMode();widthMode=w.getWidthMode();heightMode=w.getHeightMode();
        }
        void apply(Widget w, int nx, int ny, int nw, int nh) {
            nextX=nx;nextY=ny;nextWidth=nw;nextHeight=nh;
            w.setXPositionMode(0);w.setYPositionMode(0);w.setWidthMode(0);w.setHeightMode(0);
            w.setOriginalX(nx);w.setOriginalY(ny);w.setOriginalWidth(nw);w.setOriginalHeight(nh);w.revalidate();
        }
        void restore(Widget w) {
            if(w.getOriginalX()==nextX && w.getXPositionMode()==0) { w.setOriginalX(x);w.setXPositionMode(xMode); }
            if(w.getOriginalY()==nextY && w.getYPositionMode()==0) { w.setOriginalY(y);w.setYPositionMode(yMode); }
            if(w.getOriginalWidth()==nextWidth && w.getWidthMode()==0) { w.setOriginalWidth(width);w.setWidthMode(widthMode); }
            if(w.getOriginalHeight()==nextHeight && w.getHeightMode()==0) { w.setOriginalHeight(height);w.setHeightMode(heightMode); }
            w.revalidate();
        }
    }
}
