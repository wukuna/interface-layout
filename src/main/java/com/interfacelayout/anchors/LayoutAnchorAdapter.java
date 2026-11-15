package com.interfacelayout.anchors;

import com.interfacelayout.layout.GameframeCoordinator;
import com.interfacelayout.layout.menu.MenuStoneController;
import com.interfacelayout.layout.orbs.FreePositionController;
import com.interfacelayout.layout.orbs.ShieldSpecialOrb;
import com.interfacelayout.layout.orbs.widget.TargetWidget;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.ui.overlay.OverlayManager;

@Singleton
public final class LayoutAnchorAdapter implements AnchorParticipant.Provider
{
    @Inject private FreePositionController free;
    @Inject private com.interfacelayout.layout.orbs.CircularOrbs circular;
    @Inject private MenuStoneController menu;
    @Inject private GameframeCoordinator frame;
    @Inject private ShieldSpecialOrb shield;
    @Inject private EventBus events;
    @Inject private ClientThread thread;
    @Inject private OverlayManager overlays;
    @Inject private com.interfacelayout.layout.orbs.FreeOrbOverlayBridge orbBridge;
    @Inject private com.interfacelayout.layout.menu.MenuOverlayBridge menuBridge;
    private final Map<String,OrbTarget> orbs=new LinkedHashMap<>();
    private final Map<String,MenuTarget> menus=new LinkedHashMap<>();
    private AnchorParticipant participant;
    private final ShieldTarget shieldTarget=new ShieldTarget();
    private boolean dirty;
    public void start() { participant=new AnchorParticipant("Interface Layout",events,thread,this);participant.start(); }
    public void stop() { if(participant!=null)participant.stop();participant=null; }
    @Override public Collection<AnchorParticipant.Target> targets()
    {
        List<AnchorParticipant.Target> result=new ArrayList<>();
        for(TargetWidget target:free.anchorTargets()) {
            String id=FreePositionController.anchorId(target);
            OrbTarget wrapper=orbs.computeIfAbsent(id,OrbTarget::new);wrapper.target=target;result.add(wrapper);
        }
        menu.groups().forEach((group,bounds) -> {
            String id=menu.anchorGroupId(group);
            MenuTarget target=menus.computeIfAbsent(id,MenuTarget::new);target.group=group;target.last=new Rectangle(bounds);
        });
        result.addAll(menus.values());
        result.add(shieldTarget);return result;
    }
    @Override public void commit() { if(dirty) { dirty=false;frame.refresh(); } }
    @Override public void settle() {frame.refresh();}
    @Override public void reset() {menus.clear();orbs.clear();}
    private final class OrbTarget implements AnchorParticipant.Target
    {
        private final String id;
        private TargetWidget target;
        OrbTarget(String id) { this.id=id; }
        public String id() { return id; }
        public String name() { return id.substring(id.lastIndexOf('.')+1).replace('_',' '); }
        public Rectangle bounds() { return free.anchorBounds(target); }
        public String state() { return free.anchorState(target,circular::covers); }
        public boolean available() { String state=state();return state.equals("VISIBLE") || state.equals("OCCLUDED"); }
        public List<String> aliases() {
            List<String> names=new ArrayList<>();List<TargetWidget> candidates=new ArrayList<>(FreePositionController.EXTRA_TARGETS);candidates.add(target);
            for(TargetWidget candidate:candidates)if(FreePositionController.anchorId(candidate).equals(id))
                for(String mode:List.of("fixed","classic","modern"))names.add("INTERFACE_LAYOUT_ORB_"+mode+"_"+candidate.getComponentId()+"_"+candidate.getArrayId());
            return names;
        }
        public boolean claim() { return available(); }
        public void move(Point point) { dirty|=free.anchorMove(target,point); }
        public void release(boolean keep) { orbBridge.releaseAnchor(target,keep);free.anchorRelease(target,keep);dirty=true; }
    }
    private final class MenuTarget implements AnchorParticipant.Target
    {
        private final String id;
        private int group;
        private Rectangle last=new Rectangle();
        MenuTarget(String id) { this.id=id; }
        public String id() { return id; }
        public String name() { return "Menu group "+(group+1); }
        public Rectangle bounds() { return new Rectangle(menu.groups().getOrDefault(group,last)); }
        public String state() {return menu.anchorGroupId(group).equals(id)?menu.anchorState(group):"UNAVAILABLE";}
        public boolean available() { return menu.groups().containsKey(group) && menu.anchorGroupId(group).equals(id); }
        public List<String> aliases() {
            List<String> result=new ArrayList<>();
            for(String mode:List.of("fixed","classic","modern","fixed_overlay","classic_overlay","modern_overlay"))
                for(String kind:List.of("group_","row_")) {
                    String name="INTERFACE_LAYOUT_MENU_"+mode+"_"+kind+group;result.add(name);
                    int generation=menu.groupGeneration(mode,group);if(generation>0)result.add(name+"_"+generation);
                }
            return result;
        }
        public boolean claim() { return available(); }
        public void move(Point point) { dirty|=menu.anchorMove(group,point); }
        public void release(boolean keep) { menuBridge.releaseAnchor(group,keep);menu.anchorRelease(id,keep);dirty=true; }
    }
    private final class ShieldTarget implements AnchorParticipant.Target
    {
        private OverlayLease lease;
        public String id() { return "layout.shield-special"; }
        public String name() { return "Shield special"; }
        public Rectangle bounds() { return shield.anchorBounds(); }
        public String state() { return shield.anchorState(); }
        public boolean available() { return state().equals("VISIBLE") || state().equals("OCCLUDED"); }
        public List<String> aliases() { return List.of(shield.getName()); }
        public boolean claim() { if(lease==null)lease=new OverlayLease(shield,shield::anchorMobility,overlays);return true; }
        public void move(Point point) { lease.move(point); }
        public void release(boolean keep) { if(lease!=null) {lease.release(keep,overlays);lease=null;if(keep)overlays.saveOverlay(shield);} }
    }
}
