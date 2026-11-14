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
    @Inject private net.runelite.client.eventbus.EventBus events;
    @Inject private net.runelite.client.config.ConfigManager configs;
    @Inject private net.runelite.api.Client client;
    private final Map<String, ElementOverlay> elements = new HashMap<>();
    private boolean running;
    private Runnable refresh = () -> {};

    public void start(Runnable refresh) { this.refresh = refresh; running = true; if(events!=null)events.register(this);sync(); }
    public void stop()
    {
        running = false;
        if(events!=null)events.unregister(this);
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
        Map<TargetWidget, Rectangle> current = free.overlayBounds();
        current.forEach((target, bounds) -> {
            String key = name(target);
            if (!elements.containsKey(key))
            {
                ElementOverlay overlay = new ElementOverlay(key, target);
                overlay.getBounds().setBounds(bounds);
                elements.put(key, overlay); overlays.add(overlay);
            }
            ElementOverlay overlay = elements.get(key);
            float priority=priority(target);
            if(Float.compare(overlay.getPriority(),priority)!=0)
            { overlays.remove(overlay);overlay.setPriority(priority);overlays.add(overlay); }
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
    private float priority(TargetWidget target)
    {
        OrbOrder orb=OrbOrder.forComponent(target.getComponentId());
        return orb==null?Overlay.PRIORITY_LOW:0.5f+0.1f*orb.ordinal();
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
    @net.runelite.client.eventbus.Subscribe
    public void onOverlayMenuClicked(net.runelite.client.events.OverlayMenuClicked event)
    {
        if(!running || !(event.getOverlay() instanceof ElementOverlay))return;
        ElementOverlay overlay=(ElementOverlay)event.getOverlay();
        if(elements.get(overlay.name)!=overlay)return;
        String option=event.getEntry().getOption();
        OrbOrder orb=OrbOrder.forComponent(overlay.target.getComponentId());
        if(orb!=null && (option.equals("Bring forward") || option.equals("Send backward")))
            configs.setConfiguration("interfacelayout","orbStackOrder",OrbOrder.move(configs.getConfiguration("interfacelayout","orbStackOrder"),orb,option.equals("Bring forward")?1:-1));
        for(EdgeAlignment edge:EdgeAlignment.values())if(edge.label.equals(option))
        {
            if(placement.customAnchored(overlay))
            {
                client.addChatMessage(net.runelite.api.ChatMessageType.GAMEMESSAGE,"","Detach this control from Custom UI Anchors before choosing a screen edge.",null);
                return;
            }
            free.align(overlay.target,edge);
            overlay.setPreferredLocation(null);overlay.setPreferredPosition(OverlayPosition.DYNAMIC);
            overlay.lastApplied=null;overlays.saveOverlay(overlay);refresh.run();
        }
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
            setPriority(priority(target)); setMovable(true); setSnappable(true);
            OrbOrder orb=OrbOrder.forComponent(target.getComponentId());
            String label=orb==null?target instanceof com.interfacelayout.layout.orbs.widget.elements.Gravestone?"Grave indicator":"Interface Layout":orb.toString();
            if(orb!=null)for(String action:new String[]{"Bring forward","Send backward"})
                getMenuEntries().add(new net.runelite.client.ui.overlay.OverlayMenuEntry(net.runelite.api.MenuAction.RUNELITE_OVERLAY,action,label));
            for(EdgeAlignment edge:EdgeAlignment.values())
                getMenuEntries().add(new net.runelite.client.ui.overlay.OverlayMenuEntry(net.runelite.api.MenuAction.RUNELITE_OVERLAY,edge.label,label));
        }
        @Override public String getName() { return name; }
        @Override public void revalidate()
        {
            reset = lastApplied != null && getPreferredLocation() == null && getPreferredPosition() == null;
        }
        @Override public Dimension render(Graphics2D graphics)
        {
            if (!running || !name.equals(FreeOrbOverlayBridge.this.name(target))) return null;
            if (!free.overlayBounds().containsKey(target)) return null;
            Rectangle actual = layoutBounds;
            if (reset && getPreferredLocation() == null && getPreferredPosition() == null)
            {
                reset = false; lastApplied = null; free.reset(target);
                if (!free.overlayBounds().containsKey(target)) return null;
                actual = layoutBounds;
            }
            reset = false;
            if (!OverlayPlacement.usesLayout(this))
            {
                if(free.alignment(target)!=null)free.clearAlignment(target);
                Point canvas = getBounds().getLocation();
                if (!canvas.equals(lastApplied) || !canvas.equals(actual.getLocation()))
                {
                    lastApplied = new Point(canvas); free.applyOverlayPosition(target, canvas);
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
