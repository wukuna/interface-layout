package com.interfacelayout.layout.sizing;

import com.interfacelayout.util.WidgetStateStore;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetSizeMode;
import net.runelite.api.widgets.WidgetType;

/** Shrinks native widgets without replacing their actions, listeners or visibility. */
@Singleton
public class WidgetSizing
{
    @Inject private WidgetStateStore states;
    public void size(Widget widget, int width, int height)
    {
        if (widget == null) return;
        states.capture(widget);
        widget.setOriginalWidth(width); widget.setOriginalHeight(height);
        widget.setWidthMode(WidgetSizeMode.ABSOLUTE); widget.setHeightMode(WidgetSizeMode.ABSOLUTE);
        if (widget.getType() == WidgetType.GRAPHIC)
        {
            states.spriteTiling(widget, false);
        }
        widget.revalidate();
    }
}
