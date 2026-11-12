package com.interfacelayout.util;

import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.OverlayPosition;

@Singleton
public class OverlayPlacement
{
    @Inject private Client client;
    @Inject private OverlayManager overlays;
    @Inject private ConfigManager configs;

    public boolean adopt(Overlay overlay, Point saved)
    {
        if (saved == null || overlay.getPreferredPosition() != null) return false;
        Point location = overlay.getPreferredLocation();
        String name = overlay.getName();
        String assignments = configs.getConfiguration("anchorcustomizer", "overlayAssignments");
        if (assignments != null && assignments.contains("\"" + name + "\"")) return false;
        if (location == null)
        {
            overlay.setPreferredPosition(OverlayPosition.DYNAMIC);
            overlays.saveOverlay(overlay);
            return true;
        }
        if (!location.equals(saved)) return false;
        if (!defaultOrigin(name, "_origin", "AUTO") || !defaultOrigin(name, "_originX", "LEFT")
            || !defaultOrigin(name, "_originY", "TOP")) return false;
        Dimension canvas = client.getRealDimensions();
        if (canvas == null || canvas.width <= 0 || canvas.height <= 0
            || !new Rectangle(canvas).contains(new Rectangle(location, overlay.getBounds().getSize()))) return false;
        overlays.resetOverlay(overlay);
        overlay.setPreferredPosition(OverlayPosition.DYNAMIC);
        overlays.saveOverlay(overlay);
        return true;
    }

    public static boolean usesLayout(Overlay overlay)
    {
        return overlay.getPreferredLocation() == null && (overlay.getPreferredPosition() == null
            || overlay.getPreferredPosition() == OverlayPosition.DYNAMIC);
    }

    private boolean defaultOrigin(String name, String suffix, String expected)
    {
        String origin = configs.getConfiguration("runelite", name + suffix);
        return origin == null || expected.equals(origin);
    }
}
