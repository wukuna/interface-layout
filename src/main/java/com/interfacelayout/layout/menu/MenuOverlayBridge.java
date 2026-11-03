package com.interfacelayout.layout.menu;

import com.interfacelayout.util.WidgetBoundsExpander;
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
import net.runelite.api.gameval.InterfaceID;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.OverlayPosition;

@Singleton
public class MenuOverlayBridge
{
    @Inject private OverlayManager overlays;
    @Inject private MenuStoneController menu;
    @Inject private WidgetBoundsExpander expander;
    private final Map<String, GroupOverlay> groups = new HashMap<>();
    private final List<Overlay> nativeOverlays = new ArrayList<>();
    private Runnable refresh = () -> {};
    private boolean running;

    public void start(Runnable refresh) { this.refresh = refresh; running = true; sync(); }
    public void stop()
    {
        running = false;
        groups.values().forEach(overlay -> { overlay.getBounds().setSize(0, 0); overlays.remove(overlay); }); groups.clear();
        nativeOverlays.forEach(overlays::add); nativeOverlays.clear();
    }
    public void sync()
    {
        if (!running) return;

        String prefix = "INTERFACE_LAYOUT_MENU_" + menu.layoutMode() + "_"
            + (menu.individualDragging() ? "group_" : "row_");
        groups.values().removeIf(overlay -> {
            if (!overlay.name.startsWith(prefix) || overlay.name.equals(name(overlay.id))) return false;
            overlay.getBounds().setSize(0, 0); overlays.remove(overlay); return true;
        });

        nativeOverlays.removeIf(overlay -> {
            if (replacesNativeOverlay(overlay.getName())) return false;
            overlays.add(overlay); return true;
        });
        overlays.removeIf(overlay -> {
            if (!replacesNativeOverlay(overlay.getName())) return false;

            overlay.getBounds().setSize(0, 0);
            nativeOverlays.add(overlay); return true;
        });
        menu.groups().forEach((id, bounds) -> {
            String key = name(id);
            if (!groups.containsKey(key))
            {
                GroupOverlay overlay = new GroupOverlay(key, id);
                overlay.getBounds().setBounds(bounds);
                groups.put(key, overlay); overlays.add(overlay);
            }
            GroupOverlay overlay = groups.get(key);
            overlay.getBounds().setSize(bounds.getSize());
            if (overlay.getPreferredLocation() == null && overlay.getPreferredPosition() == null)
                overlay.getBounds().setLocation(bounds.getLocation());
        });
        groups.values().forEach(overlay -> {
            if (!overlay.name.equals(name(overlay.id)) || !menu.groups().containsKey(overlay.id))
                overlay.getBounds().setSize(0, 0);
        });
    }
    private boolean replacesNativeOverlay(String name)
    {

        if (name.equals("RESIZABLE_MINIMAP_STONES_WIDGET"))
            return expander.isExpanded(InterfaceID.ToplevelOsrsStretch.MAP_CONTAINER);
        if (name.equals("RESIZABLE_MINIMAP_WIDGET"))
            return expander.isExpanded(InterfaceID.ToplevelPreEoc.MAP_CONTAINER);
        if (!menu.mode().equals("modern") || menu.groups().isEmpty()) return false;
        int row;
        if (name.equals("RESIZABLE_VIEWPORT_BOTTOM_LINE_TABS1")) row = 1;
        else if (name.equals("RESIZABLE_VIEWPORT_BOTTOM_LINE_TABS2")) row = 0;
        else return false;
        return menu.individualDragging() || menu.groups().containsKey(row);
    }
    private String name(int id)
    {
        return "INTERFACE_LAYOUT_MENU_" + menu.layoutMode() + "_"
            + (menu.individualDragging() ? "group_" : "row_") + id
            + (menu.individualDragging() && menu.groupGeneration(id) > 0 ? "_" + menu.groupGeneration(id) : "");
    }
    public void moveGroupingStone(int id, Point canvas, boolean beginning)
    {
        GroupOverlay overlay = groups.get(name(id));
        if (overlay == null) return;
        if (beginning) clearPreference(id);
        overlay.setPreferredPosition(null);
        overlay.setPreferredLocation(new Point(canvas));
    }
    public void finishGroupingStone(int id)
    {
        GroupOverlay overlay = groups.get(name(id));
        if (overlay != null) overlays.saveOverlay(overlay);
    }
    public void clearPreference(int id)
    {
        GroupOverlay overlay = groups.get(name(id));
        if (overlay != null) { overlays.resetOverlay(overlay); overlay.lastApplied = null; overlay.reset = false; }
    }
    public void resetAll()
    {

        for (String mode : new String[]{"fixed", "classic", "modern", "fixed_overlay", "classic_overlay", "modern_overlay"})
            for (String kind : new String[]{"group_", "row_"})
                for (int id = 0; id <= 14; id++)
                {
                    overlays.resetOverlay(new GroupOverlay("INTERFACE_LAYOUT_MENU_" + mode + "_" + kind + id, id));
                    int version = menu.groupGeneration(mode, id);
                    if (kind.equals("group_") && version > 0)
                        overlays.resetOverlay(new GroupOverlay("INTERFACE_LAYOUT_MENU_" + mode + "_" + kind + id + "_" + version, id));
                }
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

            reset = getPreferredLocation() == null && getPreferredPosition() == null && lastApplied != null;
        }
        @Override public Dimension render(Graphics2D graphics)
        {
            Rectangle actual = menu.groups().get(id);
            if (!running || !name.equals(MenuOverlayBridge.this.name(id)) || actual == null) return null;
            if (reset && getPreferredLocation() == null && getPreferredPosition() == null)
            {
                reset = false; lastApplied = null; menu.resetGroup(id); refresh.run();
                actual = menu.groups().get(id);
                if (actual == null) return null;
            }
            reset = false;
            if (getPreferredLocation() != null || getPreferredPosition() != null)
            {

                Point position = getBounds().getLocation();
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
