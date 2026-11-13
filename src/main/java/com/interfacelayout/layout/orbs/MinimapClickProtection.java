package com.interfacelayout.layout.orbs;

import com.interfacelayout.layout.orbs.widget.WidgetManager;
import java.util.IdentityHashMap;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.widgets.Widget;

@Singleton
public class MinimapClickProtection
{
    @Inject private WidgetManager widgets;
    private final Map<Widget, Boolean> original = new IdentityHashMap<>();

    public void apply()
    {
        Widget minimap = widgets.getMinimapMask();
        if (minimap == null || minimap.isHidden()
            || minimap.getContentType() != OrbConstants.Layout.MinimapOverlay.MINIMAP_CONTENT) return;

        original.putIfAbsent(minimap, minimap.getNoClickThrough());
        minimap.setNoClickThrough(true);
    }

    public void restore()
    {
        original.forEach(Widget::setNoClickThrough);
        original.clear();
    }
}
