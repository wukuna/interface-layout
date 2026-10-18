package com.interfacelayout.layout.sizing;

import com.interfacelayout.InterfaceLayoutConfig;
import com.interfacelayout.layout.orbs.OrbController;
import com.interfacelayout.layout.orbs.widget.elements.Orbs;
import com.interfacelayout.util.WidgetGeometry;
import java.awt.*;
import java.awt.geom.Arc2D;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/** Our own passive rings, drawn with the native HUD; never manipulates another overlay. */
@Singleton
public class OrbRingOverlay extends Overlay
{
    @Inject private Client client;
    @Inject private InterfaceLayoutConfig config;
    @Inject private OrbController manager;
    @Inject private OrbRingTimers timers;

    public OrbRingOverlay()
    {
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.MANUAL);
        drawAfterInterface(InterfaceID.Orbs.ORB_HEALTH >>> 16);
    }

    @Override public Dimension render(Graphics2D graphics)
    {
        if (!config.orbTimerCompatibility() || !manager.active || !manager.isLoggedIn() || manager.isMinimapMinimized()) return null;
        Graphics2D copy = (Graphics2D) graphics.create();
        try
        {
            copy.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            copy.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            ring(copy, Orbs.HP_ORB_CONTAINER, timers.hp(), new Color(255, 12, 5));
            ring(copy, Orbs.SPEC_ORB_CONTAINER, timers.spec(), timers.specColor());
        }
        finally { copy.dispose(); }
        return null;
    }

    private void ring(Graphics2D graphics, Orbs orb, double progress, Color color)
    {
        if (!Double.isFinite(progress) || progress <= 0) return;
        Widget root = client.getWidget(orb.getComponentId());
        Widget indicator = client.getWidget(orb.getIndicatorId());
        if (root == null || root.isHidden() || indicator == null || indicator.isHidden()
            || indicator.getParentId() != root.getId()) return;
        Rectangle bounds = WidgetGeometry.bounds(indicator);
        if (bounds.isEmpty()) return;
        float alpha = (256 - Math.max(0, Math.min(255, root.getOpacity()))) / 256f;
        graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
        graphics.setColor(color);
        graphics.setStroke(new BasicStroke(2f * Math.min(bounds.width, bounds.height) / 26f));
        graphics.draw(new Arc2D.Double(bounds.x, bounds.y, bounds.width, bounds.height, 90,
            -360 * Math.min(1, progress), Arc2D.OPEN));
    }
}
