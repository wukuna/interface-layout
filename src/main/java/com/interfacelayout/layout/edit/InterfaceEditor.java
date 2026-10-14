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
import net.runelite.api.Client;
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
    @Inject private Client client;
    private volatile List<Handle> handles = List.of();
    private volatile Handle dragged;
    private Point grab;
    private Point press;
    private volatile Point preview;
    private boolean blockedGapClick;
    private Dimension dragCanvas, dragStretch;
    private boolean dragStretched;
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
        dragged = null; preview = null; blockedGapClick = false; handles = List.of();
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
        Handle moving = dragged;
        Point proposed = preview;
        if (moving != null && proposed != null) {
            graphics.drawRect(proposed.x, proposed.y, moving.bounds.width, moving.bounds.height);
        }
        return null;
    }
    @Override public MouseEvent mousePressed(MouseEvent event)
    {
        blockedGapClick = false;
        // Whole bars and free orbs always use RuneLite's configured drag hotkey.
        // This listener owns only the Shift gesture for changing stone membership.
        if (inBlockedGap(event.getPoint()) && (event.getButton() == MouseEvent.BUTTON1 || event.getButton() == MouseEvent.BUTTON3)) {
            blockedGapClick = true; event.consume(); return event;
        }
        if (!manager.isEditingLayout || !event.isShiftDown() || event.isAltDown() || !menu.individualDragging()
            || (event.getButton() != MouseEvent.BUTTON1 && event.getButton() != MouseEvent.BUTTON3)) return event;
        List<Handle> current = handles;
        for (int i = current.size() - 1; i >= 0; i--)
        {
            Handle handle = current.get(i);
            if (handle.stone < 0) continue;
            if (!handle.bounds.contains(event.getPoint())) continue;
            generation++;
            if (event.getButton() == MouseEvent.BUTTON3)
            {
                int epoch = generation;
                clientThread.invokeLater(() -> {
                    if (generation != epoch || !manager.isEditingLayout || !menu.stones().containsKey(handle.stone)) return;
                    menu.reattach(handle.stone);
                    refresh.run();
                });
                event.consume(); return event;
            }
            dragged = handle;
            preview = null;
            press = event.getPoint();
            dragCanvas = new Dimension(client.getCanvasWidth(), client.getCanvasHeight());
            dragStretched = client.isStretchedEnabled();
            Dimension stretched = dragStretched ? client.getStretchedDimensions() : null;
            dragStretch = stretched == null ? null : new Dimension(stretched);
            grab = new Point(event.getX() - handle.bounds.x, event.getY() - handle.bounds.y);
            event.consume(); break;
        }
        return event;
    }
    @Override public MouseEvent mouseDragged(MouseEvent event)
    {
        Handle handle = dragged;
        if (blockedGapClick) { event.consume(); return event; }
        if (handle == null) return event;
        if (geometryChanged()) { cancel(); event.consume(); return event; }
        if (press.distanceSq(event.getPoint()) < 25 && preview == null) { event.consume(); return event; }
        Point point = LayoutGeometry.snap(new Point(event.getX() - grab.x, event.getY() - grab.y), config.editGrid());
        // Do not persist membership or move the source bar while a gesture is incomplete.
        preview = point;
        event.consume(); return event;
    }
    @Override public MouseEvent mouseReleased(MouseEvent event)
    {
        Handle handle = dragged;
        if (blockedGapClick) { event.consume(); return event; }
        if (handle != null)
        {
            if (geometryChanged()) { cancel(); event.consume(); return event; }
            if (handle.stone >= 0 && preview != null)
            {
                int epoch = generation;
                Point cursor = event.getPoint();
                Point destination = LayoutGeometry.snap(new Point(cursor.x - grab.x, cursor.y - grab.y), config.editGrid());
                clientThread.invokeLater(() -> {
                    if (generation != epoch || !manager.isEditingLayout || !menu.stones().containsKey(handle.stone)) return;
                    menu.detach(handle.stone, destination);
                    int detachedGroup = menu.groupForStone(handle.stone);
                    menu.dropStone(handle.stone, cursor); refresh.run();
                    if (menu.groupForStone(handle.stone) == detachedGroup) {
                        menuOverlays.moveGroupingStone(detachedGroup, destination, true);
                        menuOverlays.finishGroupingStone(detachedGroup);
                    }
                });
            }
            dragged = null; preview = null; event.consume();
        }
        return event;
    }
    private boolean inBlockedGap(Point point) {
        return !config.allowClicksThroughGaps() && manager.isLoggedIn() && !client.isMenuOpen()
            && menu.groups().values().stream().anyMatch(bounds -> bounds.contains(point))
            && menu.stones().values().stream().noneMatch(bounds -> bounds.contains(point));
    }
    private boolean geometryChanged() {
        return !dragCanvas.equals(new Dimension(client.getCanvasWidth(), client.getCanvasHeight()))
            || dragStretched != client.isStretchedEnabled()
            || dragStretched && !java.util.Objects.equals(dragStretch, client.getStretchedDimensions());
    }
    @Override public MouseEvent mouseClicked(MouseEvent event) {
        if (blockedGapClick || inBlockedGap(event.getPoint())) event.consume();
        blockedGapClick = false;
        return event;
    }
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
