package com.interfacelayout.layout.orbs;

import com.interfacelayout.InterfaceLayoutConfig.OrbRim;
import com.interfacelayout.util.WidgetGeometry;
import com.interfacelayout.util.InterfaceWindows;
import com.interfacelayout.layout.orbs.widget.elements.Compass;
import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;

final class CircularCompassFrame {
    private Widget compass, replacedFrame;
    private Rectangle circle;
    private int originalOpacity, compassOpacity;
    private boolean customFrame;
    private final java.util.Set<Widget> covered=java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());

    void restore(Client client, OrbController manager) {

        for(Widget widget:covered)
            if(CircularOrbInput.current(client,widget) && widget.isSelfHidden())widget.setHidden(false);
        covered.clear();

        if (replacedFrame != null && manager.compassFrame == replacedFrame && replacedFrame.getOpacity() == 255)
            replacedFrame.setOpacity(originalOpacity);
        replacedFrame = null;
        compass = null;
        circle = null;
    }

    void refresh(Client client, OrbController manager) {refresh(client, manager, true);}

    void refresh(Client client, OrbController manager, boolean customFrame) {
        this.customFrame = customFrame;
        if (manager.isCompassHidden() || manager.isMinimapMinimized()) return;
        int id = manager.isFixedMode() ? InterfaceID.Toplevel.MAPCONTAINER_GRAPHIC5
            : manager.isClassicResizable() ? OrbConstants.Widgets.Classic.COMPASS
            : OrbConstants.Widgets.Modern.COMPASS;
        Widget widget = client.getWidget(id);
        if (widget == null || widget.getContentType() != OrbConstants.Layout.MinimapOverlay.COMPASS_CONTENT
            || widget.isHidden() || widget.getOpacity() == 255) return;
        Rectangle bounds = WidgetGeometry.bounds(widget);
        if (bounds.width <= 0 || bounds.height <= 0) return;
        compass = widget;
        compassOpacity = widget.getOpacity();
        circle = CircularOrbArtwork.around(bounds);
        if(InterfaceWindows.covered(client,circle)) {
            cover(widget);cover(manager.compassFrame);
            for(Compass target:Compass.values()) {
                Widget part=client.getWidget(target.getComponentId());
                if(part!=null && WidgetGeometry.bounds(part).intersects(bounds))cover(part);
            }
            compass=null;circle=null;return;
        }
        if (!customFrame) return;
        Widget frame = manager.compassFrame;
        if (frame != null && !frame.isHidden() && frame.getOpacity() < 255) {
            replacedFrame = frame;
            originalOpacity = frame.getOpacity();
            frame.setOpacity(255);
        }
    }

    private void cover(Widget widget) {
        if(widget!=null && !widget.isHidden()) {covered.add(widget);widget.setHidden(true);}
    }

    boolean coveredMenu(net.runelite.api.MenuEntry entry) {
        for(Widget widget:covered)
            if(widget.getIndex()<0 && entry.getParam1()==widget.getId() || entry.getWidget()==widget)return true;
        return false;
    }

    void paint(Graphics2D g, CircularOrbArtwork artwork, OrbRim style) {
        if (!customFrame || compass == null || circle == null || compass.isHidden()) return;
        Graphics2D draw = (Graphics2D) g.create();
        try {
            draw.setComposite(AlphaComposite.SrcOver.derive((255 - compassOpacity) / 255f));
            artwork.paint(draw, circle, style);
        } finally { draw.dispose(); }
    }
}
