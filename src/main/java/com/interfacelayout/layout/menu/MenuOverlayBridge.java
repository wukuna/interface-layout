package com.interfacelayout.layout.menu;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.OverlayPosition;

/** Delegate whole-bar Alt dragging, origins and snapping to RuneLite's overlay renderer. */
@Singleton
public class MenuOverlayBridge
{
    @Inject private OverlayManager overlays;
    @Inject private MenuStoneController menu;
    private final Map<String, GroupOverlay> groups = new HashMap<>();
    private final List<Overlay> nativeRows = new ArrayList<>();
    private Runnable refresh = () -> {};
    private boolean running;

    public void start(Runnable refresh) { this.refresh = refresh; running = true; sync(); }
    public void stop()
    {
        running = false;
        groups.values().forEach(overlays::remove); groups.clear();
        nativeRows.forEach(overlays::add); nativeRows.clear();
    }
    public void sync()
    {
        if (!running) return;
        // Modern's two native row wrappers would otherwise force expanded parents
        // back to their old overlay origins. Retain and restore the exact objects.
        nativeRows.removeIf(overlay -> {
            if (replacesNativeRow(overlay.getName())) return false;
            overlays.add(overlay); return true;
        });
        overlays.removeIf(overlay -> {
            if (!replacesNativeRow(overlay.getName())) return false;
            nativeRows.add(overlay); return true;
        });
        menu.groups().forEach((id, bounds) -> {
            String key = name(id);
            if (!groups.containsKey(key))
            {
                GroupOverlay overlay = new GroupOverlay(key, id);
                overlay.getBounds().setBounds(bounds);
                groups.put(key, overlay); overlays.add(overlay);
            }
        });
    }
    private boolean replacesNativeRow(String name)
    {
        if (!menu.mode().equals("modern") || menu.groups().isEmpty()) return false;
        int row;
        if (name.equals("RESIZABLE_VIEWPORT_BOTTOM_LINE_TABS1")) row = 1;
        else if (name.equals("RESIZABLE_VIEWPORT_BOTTOM_LINE_TABS2")) row = 0;
        else return false;
        return menu.individualDragging() || menu.groups().containsKey(row);
    }
    private String name(int id)
    {
        return "INTERFACE_LAYOUT_MENU_" + menu.mode() + "_"
            + (menu.individualDragging() ? "group_" : "row_") + id;
    }
    public void clearPreference(int id)
    {
        GroupOverlay overlay = groups.get(name(id));
        if (overlay != null) { overlays.resetOverlay(overlay); overlay.lastApplied = null; overlay.reset = false; }
    }
    public void resetAll()
    {
        // Clear preferences for modes/groups not instantiated in this session too.
        for (String mode : new String[]{"fixed", "classic", "modern"})
            for (String kind : new String[]{"group_", "row_"})
                for (int id = 0; id <= 14; id++)
                    overlays.resetOverlay(new GroupOverlay("INTERFACE_LAYOUT_MENU_" + mode + "_" + kind + id, id));
        groups.values().forEach(overlay -> { overlays.resetOverlay(overlay); overlay.lastApplied = null; });
    }
    public final class GroupOverlay extends Overlay
    {
        private final String name;
        private final int id;
        private Point lastApplied;
        private boolean reset;
        GroupOverlay(String name, int id)
        {
            this.name = name; this.id = id;
            setPosition(OverlayPosition.DYNAMIC);
            setLayer(OverlayLayer.UNDER_WIDGETS);
            setPriority(Overlay.PRIORITY_HIGHEST);
            setMovable(true); setSnappable(true);
        }
        @Override public String getName() { return name; }
        @Override public void revalidate()
        {
            // RuneLite also revalidates overlays when their size/settings change.
            // Only an actual cleared preference represents an overlay reset.
            reset = getPreferredLocation() == null && getPreferredPosition() == null && lastApplied != null;
        }
        @Override public Dimension render(Graphics2D graphics)
        {
            Rectangle actual = menu.groups().get(id);
            if (!running || !name.equals(MenuOverlayBridge.this.name(id)) || actual == null) return null;
            if (reset)
            {
                reset = false; lastApplied = null; menu.resetGroup(id); refresh.run();
                actual = menu.groups().get(id);
                if (actual == null) return null;
            }
            if (getPreferredLocation() != null || getPreferredPosition() != null)
            {
                // OverlayRenderer has already resolved origins, snapping and clamping.
                Point position = getPreferredLocation() != null
                    ? new Point(getPreferredLocation()) : getBounds().getLocation();
                if (!position.equals(lastApplied))
                {
                    lastApplied = new Point(position);
                    menu.saveGroup(id, position); refresh.run();
                }
            }
            else getBounds().setLocation(actual.getLocation());
            return actual.getSize();
        }
    }
}
