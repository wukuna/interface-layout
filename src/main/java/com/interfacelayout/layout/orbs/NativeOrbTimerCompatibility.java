package com.interfacelayout.layout.orbs;

import com.interfacelayout.InterfaceLayoutConfig;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.geom.Ellipse2D;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Map;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayManager;

final class NativeOrbTimerCompatibility
{
    private static final String REGEN="net.runelite.client.plugins.regenmeter.RegenMeterOverlay";
    private static final String DOSE="net.runelite.client.plugins.prayer.PrayerDoseOverlay";
    @Inject Client client;
    @Inject InterfaceLayoutConfig config;
    @Inject OverlayManager overlays;
    private final Map<Overlay,OverlayLayer> owned=new IdentityHashMap<>();

    void refresh(boolean enabled)
    {
        if(!enabled){restore();return;}
        ArrayList<Overlay> found=new ArrayList<>();
        overlays.anyMatch(overlay->{
            String name=overlay.getClass().getName();
            if(REGEN.equals(name) || DOSE.equals(name))found.add(overlay);
            return false;
        });
        for(Overlay overlay:new ArrayList<>(owned.keySet()))if(!found.contains(overlay))release(overlay,false);
        for(Overlay overlay:found)if(overlay.getLayer()!=OverlayLayer.MANUAL) {
            owned.put(overlay,overlay.getLayer());overlays.remove(overlay);
            overlay.setLayer(OverlayLayer.MANUAL);overlays.add(overlay);
        }
    }

    void restore()
    {
        for(Overlay overlay:new ArrayList<>(owned.keySet()))release(overlay,true);
    }

    private void release(Overlay overlay,boolean registered)
    {
        OverlayLayer layer=owned.remove(overlay);
        if(overlay.getLayer()!=OverlayLayer.MANUAL)return;
        boolean present=registered && overlays.remove(overlay);
        overlay.setLayer(layer);
        if(present)overlays.add(overlay);
    }

    void paint(Graphics2D graphics,int component,Rectangle circle,boolean replaceSpecial)
    {
        boolean prayer=component==InterfaceID.Orbs.ORB_PRAYER;
        boolean health=component==InterfaceID.Orbs.ORB_HEALTH;
        if(!prayer && !health && component!=InterfaceID.Orbs.ORB_SPECENERGY)return;
        if(!prayer && (replaceSpecial || !config.disableOrbTimers() && (health?config.orbHitpointsTimer():config.orbSpecialTimer())))return;
        Widget widget=client.getWidget(prayer?InterfaceID.Orbs.PRAYERBUTTON:component);
        if(widget==null || widget.isHidden())return;
        Rectangle bounds=widget.getBounds();
        double diameter=prayer?bounds.height:26;
        if(diameter<=0)return;
        double x=prayer?bounds.x+24:bounds.x+27;
        double y=prayer?bounds.y-1:bounds.y+(bounds.height-26)/2.0;
        double target=prayer?circle.width+2:circle.width-8;
        for(Overlay overlay:owned.keySet())if((prayer?DOSE:REGEN).equals(overlay.getClass().getName())) {
            Graphics2D draw=(Graphics2D)graphics.create();
            try {
                double inset=(circle.width-target)/2;
                draw.clip(new Ellipse2D.Double(circle.x+inset-2,circle.y+inset-2,target+4,target+4));
                draw.translate(circle.x+inset,circle.y+inset);draw.scale(target/diameter,target/diameter);draw.translate(-x,-y);
                overlay.render(draw);
            } finally {draw.dispose();}
        }
    }
}
