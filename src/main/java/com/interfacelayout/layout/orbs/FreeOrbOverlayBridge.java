package com.interfacelayout.layout.orbs;

import com.interfacelayout.layout.orbs.widget.TargetWidget;
import com.interfacelayout.layout.orbs.widget.layout.edit.EditManager;
import com.interfacelayout.util.OverlayPlacement;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.HashMap;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.OverlayPosition;

@Singleton
public class FreeOrbOverlayBridge
{
    @Inject private OverlayManager overlays;
    @Inject private FreePositionController free;
    @Inject private OrbController manager;
    @Inject private OverlayPlacement placement;
    private final Map<String, ElementOverlay> elements = new HashMap<>();
    private boolean running;
    private Runnable refresh = () -> {};

    public void start(Runnable refresh) { this.refresh = refresh; running = true; sync(); }
    public void stop()
    {
        running = false;
        elements.values().forEach(overlay -> { overlay.getBounds().setSize(0, 0); overlays.remove(overlay); });
        elements.clear();
        free.resetSession();
    }
    private String name(TargetWidget target)
    {
        String mode = manager.isFixedMode() ? "fixed" : manager.isClassicResizable() ? "classic" : "modern";
        return name(mode, target);
    }
    private String name(String mode, TargetWidget target)
    { return "INTERFACE_LAYOUT_ORB_" + mode + "_" + target.getComponentId() + "_" + target.getArrayId(); }
    public void sync()
    {
        if (!running) return;
        Map<TargetWidget, Rectangle> current = free.bounds();
        current.forEach((target, bounds) -> {
            String key = name(target);
            if (!elements.containsKey(key))
            {
                ElementOverlay overlay = new ElementOverlay(key, target);
                overlay.getBounds().setBounds(bounds);
                elements.put(key, overlay); overlays.add(overlay);
            }
            ElementOverlay overlay = elements.get(key);
            overlay.layoutBounds = new Rectangle(bounds);
            overlay.getBounds().setSize(bounds.getSize());
            if (!overlay.reset && placement.adopt(overlay, free.savedPosition(target)))
            { free.clearPosition(target); overlay.lastApplied = null; overlay.reset = false; }
            if (OverlayPlacement.usesLayout(overlay))
                overlay.getBounds().setLocation(bounds.getLocation());
        });

        elements.values().forEach(overlay -> {
            if (!overlay.name.equals(name(overlay.target)) || !current.containsKey(overlay.target))
                overlay.getBounds().setSize(0, 0);
        });
    }
    public void profileChanged()
    {
        free.resetSession();
        elements.values().forEach(overlay -> { overlay.lastApplied = null; overlay.reset = false; });
    }
    public void resetAll()
    {
        for (String mode : new String[]{"fixed", "classic", "modern"})
        {
            for (TargetWidget[] choices : EditManager.EDIT_TARGETS)
                overlays.resetOverlay(new ElementOverlay(name(mode, choices[0]), choices[0]));
            for (TargetWidget target : FreePositionController.EXTRA_TARGETS)
                overlays.resetOverlay(new ElementOverlay(name(mode, target), target));
        }
        elements.values().forEach(overlay -> { overlays.resetOverlay(overlay); overlay.lastApplied = null; overlay.reset = false; });
    }
    public final class ElementOverlay extends Overlay
    {
        private final String name;
        private final TargetWidget target;
        private Rectangle layoutBounds;
        private Point lastApplied;
        private boolean reset;
        private ElementOverlay(String name, TargetWidget target)
        {
            this.name = name; this.target = target;
            setPosition(OverlayPosition.DYNAMIC); setLayer(OverlayLayer.UNDER_WIDGETS);
            setPriority(Overlay.PRIORITY_HIGHEST); setMovable(true); setSnappable(true);
        }
        @Override public String getName() { return name; }
        @Override public void revalidate()
        {
            reset = lastApplied != null && getPreferredLocation() == null && getPreferredPosition() == null;
        }
        @Override public Dimension render(Graphics2D graphics)
        {
            if (!running || !name.equals(FreeOrbOverlayBridge.this.name(target))) return null;
            if (!free.bounds().containsKey(target)) return null;
            Rectangle actual = layoutBounds;
            if (reset && getPreferredLocation() == null && getPreferredPosition() == null)
            {
                reset = false; lastApplied = null; free.reset(target); refresh.run();
                if (!free.bounds().containsKey(target)) return null;
                actual = layoutBounds;
            }
            reset = false;
            if (!OverlayPlacement.usesLayout(this))
            {
                Point canvas = getBounds().getLocation();
                if (!canvas.equals(lastApplied) || !canvas.equals(actual.getLocation()))
                {
                    lastApplied = new Point(canvas); free.applyPosition(target, canvas); refresh.run();
                }
            }
            else
            {
                getBounds().setLocation(actual.getLocation());
                if (getPreferredPosition() == OverlayPosition.DYNAMIC) lastApplied = actual.getLocation();
            }
            return actual.getSize();
        }
    }
}
