package com.interfacelayout.util;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Comparator;
import javax.inject.Singleton;
import net.runelite.api.widgets.Widget;
import net.runelite.api.Client;

/** Client-thread-only ownership journal. Never stores widgets in persistent config. */
@Singleton
public class WidgetStateStore
{
    private final Map<Widget, State> states = new IdentityHashMap<>();

    public void capture(Widget widget)
    {
        if (widget != null) states.computeIfAbsent(widget, State::new);
    }
    public void discardRetired(Client client)
    {
        states.keySet().removeIf(widget -> {
            Widget current = client.getWidget(widget.getId());
            if (current != null && widget.getIndex() >= 0) current = current.getChild(widget.getIndex());
            return current != widget;
        });
    }
    public void captureRendering(Widget widget)
    {
        capture(widget);
        if (widget != null) states.get(widget).captureRendering(widget);
    }
    public void captureOpacity(Widget widget)
    {
        capture(widget);
        if (widget != null && states.get(widget).opacity == null) states.get(widget).opacity = widget.getOpacity();
    }

    public void hide(Widget widget, boolean hidden)
    {
        if (widget == null || !hidden) return;
        visibility(widget, true);
    }

    /** Undo only our own hiding; never reveal a component hidden by the game. */
    public void visibility(Widget widget, boolean hidden)
    {
        if (widget == null) return;
        if (!hidden)
        {
            State state = states.get(widget);
            if (state != null) state.restoreVisibility(widget);
            return;
        }
        capture(widget);
        State state = states.get(widget);
        if (state.hidden == null) state.hidden = widget.isSelfHidden();
        widget.setHidden(true);
    }

    /** Release visibility before native scripts, which may change the game's flags. */
    public void restoreVisibility()
    {
        states.forEach((widget, state) -> state.restoreVisibility(widget));
    }

    public boolean isHiddenByGame(Widget widget)
    {
        return isHiddenByGame(widget, null);
    }

    public boolean isHiddenByGame(Widget widget, WidgetStateStore other)
    {
        for (int depth = 0; widget != null && depth < 16; depth++, widget = widget.getParent())
        {
            State state = states.get(widget);
            State second = other == null ? null : other.states.get(widget);
            // Either journal may own our hiding (compact presets or Free Position).
            if (state != null && Boolean.FALSE.equals(state.hidden)
                || second != null && Boolean.FALSE.equals(second.hidden)) continue;
            boolean hidden = state != null && state.hidden != null ? state.hidden
                : second != null && second.hidden != null ? second.hidden : widget.isSelfHidden();
            if (hidden) return true;
        }
        return false;
    }

    public void position(Widget widget, int x, int y)
    {
        if (widget == null) return;
        capture(widget);
        widget.setForcedPosition(x, y);
    }

    public void restore()
    {
        // Restore dimensions first; then clear forced positions and revalidate descendants.
        states.forEach((widget, state) -> state.restore(widget));
        states.keySet().forEach(widget -> widget.setForcedPosition(-1, -1));
        states.keySet().stream().sorted(Comparator.comparingInt(WidgetStateStore::depth)).forEach(Widget::revalidate);
        states.clear();
    }

    private static int depth(Widget widget)
    {
        int depth = 0;
        for (Widget parent = widget.getParent(); parent != null && depth < 32; parent = parent.getParent()) depth++;
        return depth;
    }

    private static final class State
    {
        private final int x, y, xMode, yMode, width, height, widthMode, heightMode;
        private Integer opacity, type, contentType, sprite;
        private Boolean hidden;
        private final boolean noClickThrough;
        State(Widget widget)
        {
            width = widget.getOriginalWidth(); height = widget.getOriginalHeight();
            x = widget.getOriginalX(); y = widget.getOriginalY();
            xMode = widget.getXPositionMode(); yMode = widget.getYPositionMode();
            widthMode = widget.getWidthMode(); heightMode = widget.getHeightMode();
            noClickThrough = widget.getNoClickThrough();
        }
        void captureRendering(Widget widget)
        {
            if (type != null) return;
            type = widget.getType(); contentType = widget.getContentType(); sprite = widget.getSpriteId();
        }
        void restore(Widget widget)
        {
            widget.setOriginalWidth(width); widget.setOriginalHeight(height);
            widget.setOriginalX(x); widget.setOriginalY(y);
            widget.setXPositionMode(xMode); widget.setYPositionMode(yMode);
            widget.setWidthMode(widthMode); widget.setHeightMode(heightMode);
            if (opacity != null) widget.setOpacity(opacity);
            if (type != null) { widget.setType(type); widget.setContentType(contentType); widget.setSpriteId(sprite); }
            restoreVisibility(widget); widget.setNoClickThrough(noClickThrough);
        }
        void restoreVisibility(Widget widget)
        {
            if (hidden == null) return;
            widget.setHidden(hidden);
            hidden = null;
        }
    }
}
