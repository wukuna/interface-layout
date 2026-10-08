package com.interfacelayout;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.interfacelayout.layout.orbs.FreeOrbOverlayBridge;
import com.interfacelayout.layout.orbs.FreePositionController;
import com.interfacelayout.layout.orbs.OrbController;
import com.interfacelayout.layout.orbs.widget.TargetWidget;
import com.interfacelayout.layout.orbs.widget.elements.Orbs;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayManager;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class FreeOrbOverlayBridgeTest
{
    @Test public void freeOrbsExposeIndependentOriginsAndPreserveAnchorsAcrossHideAndRestore()
    {
        TargetWidget hp = Orbs.HP_ORB_CONTAINER, prayer = Orbs.PRAYER_ORB_CONTAINER;
        Map<TargetWidget, Rectangle> visible = new HashMap<>();
        visible.put(hp, new Rectangle(700, 30, 50, 34));
        visible.put(prayer, new Rectangle(700, 70, 50, 34));
        FreePositionController free = mock(FreePositionController.class);
        when(free.bounds()).thenAnswer(i -> new HashMap<>(visible));
        OverlayManager overlays = mock(OverlayManager.class);
        List<Overlay> registered = new ArrayList<>();
        when(overlays.add(any())).thenAnswer(i -> registered.add(i.getArgument(0)));
        when(overlays.remove(any())).thenAnswer(i -> registered.remove(i.getArgument(0)));
        FreeOrbOverlayBridge bridge = Guice.createInjector(new AbstractModule() {
            @Override protected void configure() {
                bind(FreePositionController.class).toProvider(() -> free);
                bind(OverlayManager.class).toProvider(() -> overlays);
                bind(OrbController.class).toProvider(() -> mock(OrbController.class));
            }
        }).getInstance(FreeOrbOverlayBridge.class);
        Runnable refresh = mock(Runnable.class); bridge.start(refresh);
        assertEquals(2, registered.size());
        Overlay hpOverlay = registered.stream().filter(o -> o.getName().contains("_" + hp.getComponentId() + "_")).findFirst().get();
        assertTrue(hpOverlay.isMovable()); assertTrue(hpOverlay.isSnappable());
        hpOverlay.setPreferredLocation(new Point(-100, -100));
        hpOverlay.getBounds().setLocation(800, 500); hpOverlay.render(null);
        verify(free).save(hp, new Point(800, 500));
        verify(free, never()).save(eq(prayer), any());
        hpOverlay.setPreferredLocation(null); hpOverlay.revalidate();
        hpOverlay.setPreferredLocation(new Point(600, 300)); hpOverlay.getBounds().setLocation(600, 300); hpOverlay.render(null);
        verify(free, never()).reset(any());
        visible.remove(hp); bridge.sync(); assertTrue(hpOverlay.getBounds().isEmpty()); assertNull(hpOverlay.render(null));
        visible.put(hp, new Rectangle(600, 300, 50, 34)); bridge.sync();
        assertEquals(2, registered.size()); assertFalse(hpOverlay.getBounds().isEmpty());
        assertEquals(new Point(600, 300), hpOverlay.getPreferredLocation());
        hpOverlay.setPreferredLocation(null); hpOverlay.revalidate(); hpOverlay.render(null);
        verify(free).reset(hp);
        bridge.stop(); assertTrue(registered.isEmpty()); assertTrue(hpOverlay.getBounds().isEmpty());
    }
}
