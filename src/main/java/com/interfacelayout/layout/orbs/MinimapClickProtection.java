package com.interfacelayout.layout.orbs;

import com.interfacelayout.InterfaceLayoutConfig;
import com.interfacelayout.layout.orbs.widget.WidgetManager;
import java.util.IdentityHashMap;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.widgets.Widget;

/** Keep native minimap navigation while preventing a second click into the scene. */
@Singleton
public class MinimapClickProtection
{
    @Inject private InterfaceLayoutConfig config;
    @Inject private WidgetManager widgets;
    private final Map<Widget, Boolean> original = new IdentityHashMap<>();

    public void apply()
    {
        Widget minimap = widgets.getMinimapMask();
        if (!config.enableNoClickthrough() || minimap == null || minimap.isHidden()
            || minimap.getContentType() != OrbConstants.Layout.MinimapOverlay.MINIMAP_CONTENT) return;
        // Protect only the map widget, never its expanded canvas-sized ancestor.
        // The native content handler still processes the user's minimap click.
        original.putIfAbsent(minimap, minimap.getNoClickThrough());
        minimap.setNoClickThrough(true);
    }

    public void restore()
    {
        original.forEach(Widget::setNoClickThrough);
        original.clear();
    }
}
