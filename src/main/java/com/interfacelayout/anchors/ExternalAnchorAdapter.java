package com.interfacelayout.anchors;

import com.google.common.hash.Hashing;
import com.google.gson.Gson;
import java.awt.Component;
import java.awt.Point;
import java.awt.Rectangle;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.*;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.ui.overlay.*;

@Singleton
public final class ExternalAnchorAdapter implements AnchorParticipant.Provider
{
    private static final String KEY="anchorExternalOverlaysV1";
    @Inject private OverlayManager overlays;
    @Inject private EventBus events;
    @Inject private ClientThread thread;
    @Inject private ConfigManager configs;
    @Inject private AnchorCoordinator coordinator;
    @Inject private Gson gson;
    private AnchorParticipant participant;
    private final Map<String,Target> targets=new LinkedHashMap<>();
    private String raw;
    private long nextDiscovery;
    private Map<String,Overlay> discovered=java.util.Collections.emptyMap();
    private java.util.Set<String> selected=java.util.Collections.emptySet();
    public void start(){participant=new AnchorParticipant("Other overlays (compatibility)",events,thread,this);participant.start();}
    public void stop(){if(participant!=null)participant.stop();participant=null;}
    private static String id(Overlay overlay){return "external:"+Hashing.sha256().hashString(overlay.getClass().getName()+"\n"+overlay.getName(),StandardCharsets.UTF_8).toString();}
    static boolean supported(Overlay overlay)
    {
        String type=overlay.getClass().getName();
        if(type.startsWith("com.interfacelayout.") || type.startsWith("com.improvedchat.") || type.startsWith("com.improvedlooting.") || type.startsWith("com.windowmarkers.") || type.startsWith("com.improvedoverheads.") || type.startsWith("com.improvedrightclicks.") || type.toLowerCase(java.util.Locale.ROOT).contains("raidchecks"))return false;
        return overlay.isMovable() && !type.startsWith("net.runelite.client.ui.overlay.WidgetOverlays$") && overlay.getPosition()!=OverlayPosition.DYNAMIC
            && overlay.getPosition()!=OverlayPosition.TOOLTIP && overlay.getLayer()!=OverlayLayer.MANUAL
            && overlay.getName()!=null && !overlay.getName().isEmpty() && overlay.getName().length()<=180;
    }
    private Map<String,Overlay> candidates()
    {
        Map<String,Overlay> result=new LinkedHashMap<>();java.util.Set<String> duplicates=new java.util.HashSet<>();
        List<Overlay> visible=new ArrayList<>();overlays.anyMatch(overlay->{visible.add(overlay);return false;});
        for(Overlay overlay:visible) {
            String id=id(overlay);Target owned=targets.get(id);
            if(!supported(overlay) && (owned==null || owned.overlay!=overlay || owned.lease==null))continue;
            if(result.putIfAbsent(id,overlay)!=null)duplicates.add(id);
        }
        duplicates.forEach(result::remove);return result;
    }
    private void selection()
    {
        String value=configs.getConfiguration(AnchorLayoutStore.GROUP,KEY);
        if(java.util.Objects.equals(value,raw))return;
        raw=value;selected=new java.util.HashSet<>();
        try {var array=AnchorTransfers.parse(value,"[]").getAsJsonArray();if(array.size()>64)return;for(var entry:array){String id=entry.getAsString();if(id.matches("external:[a-f0-9]{64}"))selected.add(id);}}
        catch(RuntimeException invalid){selected.clear();}
    }
    @Override public Collection<AnchorParticipant.Target> targets()
    {
        selection();List<AnchorParticipant.Target> result=new ArrayList<>();
        java.util.Set<String> wanted=new java.util.LinkedHashSet<>(selected);
        coordinator.snapshot().layout.anchors.forEach(anchor->anchor.members.forEach(member->{if(member.target.startsWith("external:"))wanted.add(member.target);}));
        boolean editing=coordinator.editing();
        if(wanted.isEmpty() && !editing)return result;
        long now=System.nanoTime();
        if(now>=nextDiscovery){discovered=candidates();nextDiscovery=now+1000000000L;}
        Map<String,Overlay> live=discovered;
        if(editing)wanted.addAll(live.keySet());
        for(String id:wanted) {
            Overlay overlay=live.get(id);if(overlay==null)continue;
            Target target=targets.get(id);if(target==null || target.overlay!=overlay){target=new Target(id,overlay);targets.put(id,target);}result.add(target);
        }
        return result;
    }
    @Override public void reset(){targets.clear();raw=null;selected=java.util.Collections.emptySet();discovered=java.util.Collections.emptyMap();nextDiscovery=0;}
    public void choose(Component parent)
    {
        String session=coordinator.snapshot().session;
        thread.invokeLater(()-> {
            selection();Map<String,Overlay> candidates=candidates();Map<String,String> names=new LinkedHashMap<>();candidates.forEach((id,overlay)->names.put(id,overlay.getName()));
            java.util.Set<String> active=new java.util.HashSet<>(selected);
            SwingUtilities.invokeLater(()-> {
                JPanel panel=new JPanel();panel.setLayout(new BoxLayout(panel,BoxLayout.Y_AXIS));
                JLabel hint=new JLabel("<html>Edit anchors lets you drag overlays directly.<br>Choose extras for the picker here.<br>Use Members to detach attached overlays.</html>");panel.add(hint);
                Map<String,JCheckBox> checks=new LinkedHashMap<>();names.forEach((id,name)->{JCheckBox box=new JCheckBox(name,active.contains(id));checks.put(id,box);panel.add(box);});
                JScrollPane scroll=new JScrollPane(panel);scroll.setPreferredSize(new java.awt.Dimension(330,350));
                if(JOptionPane.showConfirmDialog(parent,scroll,"Other overlays · compatibility",JOptionPane.OK_CANCEL_OPTION)!=JOptionPane.OK_OPTION)return;
                List<String> choice=new ArrayList<>();checks.forEach((id,box)->{if(box.isSelected())choice.add(id);});
                if(coordinator.snapshot().session.equals(session))coordinator.command(()->configs.setConfiguration(AnchorLayoutStore.GROUP,KEY,gson.toJson(choice)));
            });
        });
    }
    private final class Target implements AnchorParticipant.Target
    {
        private final String id;
        private final Overlay overlay;
        private OverlayLease lease;
        private Rectangle last=new Rectangle(0,0,100,30);
        Target(String id,Overlay overlay){this.id=id;this.overlay=overlay;}
        public String id(){return id;}
        public String name(){return overlay.getName()+" · compatibility";}
        public Rectangle bounds(){if(!overlay.getBounds().isEmpty())last=new Rectangle(overlay.getBounds());return new Rectangle(last);}
        public String state(){return overlay.getBounds().isEmpty()?"OCCLUDED":"VISIBLE";}
        public List<String> aliases(){return List.of(overlay.getName());}
        public boolean claim(){if(lease==null)lease=new OverlayLease(overlay,null,overlays);return true;}
        public void move(Point point){lease.move(point);}
        public void release(boolean keep){if(lease!=null){lease.release(keep,overlays);lease=null;if(keep)overlays.saveOverlay(overlay);}}
    }
}
