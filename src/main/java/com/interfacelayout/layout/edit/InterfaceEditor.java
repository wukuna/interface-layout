package com.interfacelayout.layout.edit;

import com.interfacelayout.layout.orbs.OrbController;
import com.interfacelayout.InterfaceLayoutConfig;
import com.interfacelayout.layout.menu.MenuStoneController;
import com.interfacelayout.layout.orbs.FreePositionController;
import com.interfacelayout.layout.orbs.widget.TargetWidget;
import com.interfacelayout.util.LayoutGeometry;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.input.MouseListener;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/** Overlay is an editing indicator only; all gameplay controls remain native widgets. */
@Singleton
public class InterfaceEditor extends Overlay implements MouseListener
{
    @Inject private OrbController manager;
    @Inject private InterfaceLayoutConfig config;
    @Inject private MenuStoneController menu;
    @Inject private com.interfacelayout.layout.menu.MenuOverlayBridge menuOverlays;
    @Inject private FreePositionController orbs;
    @Inject private ClientThread clientThread;
    private volatile List<Handle> handles = List.of();
    private volatile Handle dragged;
    private Point grab;
    private boolean detached;
    private volatile int generation;
    private Runnable refresh = () -> {};

    public void setRefresh(Runnable refresh) { this.refresh = refresh; }

    public InterfaceEditor()
    {
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
    }
    public void cancel()
    {
        generation++;
        dragged = null; handles = List.of();
    }
    @Override public Dimension render(Graphics2D graphics)
    {
        List<Handle> current = new ArrayList<>();
        if (manager.isEditingLayout) orbs.bounds().forEach((target, bounds) -> current.add(new Handle(target, bounds)));
        menu.groups().forEach((id, bounds) -> current.add(new Handle(null, bounds, id, -1)));
        if (menu.individualDragging()) menu.stones().forEach((id, bounds) -> current.add(new Handle(null, bounds, -1, id)));
        handles = List.copyOf(current);
        if (!manager.isEditingLayout) return null;
        graphics.setColor(new Color(70, 220, 255));
        for (Handle handle : current)
        {
            if (handle.stone >= 0) continue;
            graphics.draw(handle.bounds);
            graphics.drawString(handle.target == null ? "Alt-drag bar; Shift-drag stone" : "Alt-drag " + handle.target,
                handle.bounds.x, Math.max(12, handle.bounds.y));
        }
        handles = List.copyOf(current);
        return null;
    }
    @Override public MouseEvent mousePressed(MouseEvent event)
    {
        // Whole bars and free orbs always use RuneLite's configured drag hotkey.
        // This listener owns only the Shift gesture for changing stone membership.
        if (!event.isShiftDown() || !menu.individualDragging()
            || (event.getButton() != MouseEvent.BUTTON1 && event.getButton() != MouseEvent.BUTTON3)) return event;
        List<Handle> current = handles;
        for (int i = current.size() - 1; i >= 0; i--)
        {
            Handle handle = current.get(i);
            if (handle.stone < 0) continue;
            if (!handle.bounds.contains(event.getPoint())) continue;
            if (event.getButton() == MouseEvent.BUTTON3)
            {
                int epoch = generation;
                clientThread.invokeLater(() -> {
                    if (generation != epoch) return;
                    menu.reattach(handle.stone);
                    refresh.run();
                });
                event.consume(); return event;
            }
            dragged = handle;
            detached = false;
            grab = new Point(event.getX() - handle.bounds.x, event.getY() - handle.bounds.y);
            event.consume(); break;
        }
        return event;
    }
    @Override public MouseEvent mouseDragged(MouseEvent event)
    {
        Handle handle = dragged;
        if (handle == null) return event;
        Point point = LayoutGeometry.snap(new Point(event.getX() - grab.x, event.getY() - grab.y), config.editGrid());
        int epoch = generation;
        boolean detachNow = handle.stone >= 0 && !detached;
        if (detachNow) detached = true;
        clientThread.invokeLater(() -> {
            if (generation != epoch) return;
            if (detachNow) menu.detach(handle.stone, point);
            else menu.moveStone(handle.stone, point);
            refresh.run();
            menuOverlays.moveGroupingStone(menu.groupForStone(handle.stone), point, detachNow);
        });
        event.consume(); return event;
    }
    @Override public MouseEvent mouseReleased(MouseEvent event)
    {
        Handle handle = dragged;
        if (handle != null)
        {
            if (handle.stone >= 0 && detached)
            {
                int epoch = generation;
                Point cursor = event.getPoint();
                clientThread.invokeLater(() -> {
                    if (generation != epoch) return;
                    menuOverlays.finishGroupingStone(menu.groupForStone(handle.stone));
                    menu.dropStone(handle.stone, cursor); refresh.run();
                });
            }
            dragged = null; event.consume();
        }
        return event;
    }
    @Override public MouseEvent mouseClicked(MouseEvent event) { return event; }
    @Override public MouseEvent mouseEntered(MouseEvent event) { return event; }
    @Override public MouseEvent mouseExited(MouseEvent event) { return event; }
    @Override public MouseEvent mouseMoved(MouseEvent event) { return event; }
    private static final class Handle
    {
        private final TargetWidget target;
        private final Rectangle bounds;
        private final int group, stone;
        Handle(TargetWidget target, Rectangle bounds) { this(target, bounds, -1, -1); }
        Handle(TargetWidget target, Rectangle bounds, int group, int stone)
        { this.target = target; this.bounds = new Rectangle(bounds); this.group = group; this.stone = stone; }
    }
}
