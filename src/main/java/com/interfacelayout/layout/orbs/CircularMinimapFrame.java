package com.interfacelayout.layout.orbs;

import com.interfacelayout.InterfaceLayoutConfig.OrbRim;
import com.interfacelayout.layout.orbs.widget.TargetWidget;
import com.interfacelayout.layout.orbs.widget.elements.Minimap;
import com.interfacelayout.util.WidgetGeometry;
import com.interfacelayout.util.InterfaceWindows;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.util.IdentityHashMap;
import java.util.Map;
import net.runelite.api.Client;
import net.runelite.api.widgets.Widget;

final class CircularMinimapFrame {
    private final Map<Widget, Boolean> hidden = new IdentityHashMap<>();
    private Widget minimap;
    private boolean customFrame;

    void restore(Client client) {
        hidden.forEach((widget, original) -> {
            if (CircularOrbInput.current(client, widget) && widget.isSelfHidden()) widget.setHidden(original);
        });
        hidden.clear();
        minimap = null;
    }

    boolean refresh(Client client, OrbController manager) {
        return refresh(client,manager,true);
    }

    boolean refresh(Client client, OrbController manager, boolean customFrame) {
        this.customFrame=customFrame;
        if (manager.isFixedMode() || manager.isMinimapMinimized()) return false;
        int mask = manager.isClassicResizable() ? OrbConstants.Widgets.Classic.MINIMAP_MASK
            : OrbConstants.Widgets.Modern.MINIMAP_MASK;
        Widget widget = client.getWidget(mask);
        if (widget == null || widget.isHidden()
            || widget.getContentType() != OrbConstants.Layout.MinimapOverlay.MINIMAP_CONTENT
            || WidgetGeometry.bounds(widget).isEmpty()) return false;
        minimap = widget;
        boolean covered=InterfaceWindows.covered(client,CircularOrbArtwork.around(WidgetGeometry.bounds(widget)));
        if(!covered && !customFrame)return true;
        if(covered)hide(widget);
        for (TargetWidget target : Minimap.COMPONENTS) {
            if (target == Minimap.CLASSIC_MINIMAP_MASK || target == Minimap.MODERN_MINIMAP_MASK) continue;
            if ((target.getComponentId() >>> 16) != (mask >>> 16)) continue;
            Widget decoration = client.getWidget(target.getComponentId());
            hide(decoration);
        }
        return true;
    }

    private void hide(Widget widget) {
        if(widget!=null && !widget.isHidden()) {hidden.put(widget,false);widget.setHidden(true);}
    }

    boolean coveredMenu(net.runelite.api.MenuEntry entry) {
        for(Widget widget:hidden.keySet())
            if(widget.getIndex()<0 && entry.getParam1()==widget.getId() || entry.getWidget()==widget)return true;
        return false;
    }

    void paint(Graphics2D graphics, CircularOrbArtwork artwork, OrbRim rim) {
        if (!customFrame || minimap == null || minimap.isHidden()) return;
        Rectangle bounds = WidgetGeometry.bounds(minimap);
        if (!bounds.isEmpty()) artwork.paint(graphics, CircularOrbArtwork.around(bounds), rim);
    }
}
