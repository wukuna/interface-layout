package com.interfacelayout.layout.orbs;

import com.interfacelayout.InterfaceLayoutConfig.OrbRim;
import com.interfacelayout.util.WidgetGeometry;
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

    void restore(OrbController manager) {

        if (replacedFrame != null && manager.compassFrame == replacedFrame && replacedFrame.getOpacity() == 255)
            replacedFrame.setOpacity(originalOpacity);
        replacedFrame = null;
        compass = null;
        circle = null;
    }

    void refresh(Client client, OrbController manager) {
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
        Widget frame = manager.compassFrame;
        if (frame != null && !frame.isHidden() && frame.getOpacity() < 255) {
            replacedFrame = frame;
            originalOpacity = frame.getOpacity();
            frame.setOpacity(255);
        }
    }

    void paint(Graphics2D g, CircularOrbArtwork artwork, OrbRim style) {
        if (compass == null || circle == null || compass.isHidden()) return;
        Graphics2D draw = (Graphics2D) g.create();
        try {
            draw.setComposite(AlphaComposite.SrcOver.derive((255 - compassOpacity) / 255f));
            artwork.paint(draw, circle, style);
        } finally { draw.dispose(); }
    }
}
