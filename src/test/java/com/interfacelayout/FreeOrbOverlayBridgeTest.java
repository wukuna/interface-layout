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
    @Test public void responsiveOrbRemainsResettableWithoutResetsFromOtherOverlays()
    {
        TargetWidget target = Orbs.HP_ORB_CONTAINER;
        FreePositionController free = mock(FreePositionController.class);
        when(free.bounds()).thenReturn(Map.of(target, new Rectangle(1500, 700, 56, 36)));
        when(free.savedPosition(target)).thenReturn(new Point(1500, 700));
        OverlayManager overlays = mock(OverlayManager.class);
        List<Overlay> added = new ArrayList<>();
        when(overlays.add(any())).thenAnswer(i -> added.add(i.getArgument(0)));
        doAnswer(i -> {
            Overlay overlay = i.getArgument(0);
            overlay.setPreferredLocation(null); overlay.setPreferredPosition(null); overlay.revalidate(); return null;
        }).when(overlays).resetOverlay(any());
        FreeOrbOverlayBridge bridge = Guice.createInjector(new AbstractModule() {
            @Override protected void configure() {
                bind(FreePositionController.class).toProvider(() -> free);
                bind(OverlayManager.class).toProvider(() -> overlays);
                bind(OrbController.class).toProvider(() -> mock(OrbController.class));
                bind(net.runelite.api.Client.class).toInstance(client());
                bind(net.runelite.client.config.ConfigManager.class).toInstance(mock(net.runelite.client.config.ConfigManager.class));
            }
        }).getInstance(FreeOrbOverlayBridge.class);
        bridge.start(() -> {});
        Overlay orb = added.get(0);
        assertEquals(net.runelite.client.ui.overlay.OverlayPosition.DYNAMIC, orb.getPreferredPosition());
        orb.render(null); orb.revalidate(); orb.render(null);
        verify(free, never()).reset(any());
        overlays.resetOverlay(orb); orb.render(null);
        verify(free).reset(target);
        bridge.profileChanged(); orb.render(null);
        verify(free, times(1)).reset(target);
        verify(free).resetSession();
    }
    private static net.runelite.api.Client client()
    {
        net.runelite.api.Client client = mock(net.runelite.api.Client.class);
        when(client.getRealDimensions()).thenReturn(new java.awt.Dimension(2000, 2000));
        return client;
    }

    @Test public void minimapAnchorsFollowCanvasUpdatesAndResetWithTheLayout()
    {
        TargetWidget map = com.interfacelayout.layout.orbs.widget.elements.Minimap.MODERN_MINIMAP_MASK;
        Map<TargetWidget, Rectangle> visible = new HashMap<>();
        visible.put(map, new Rectangle(800, 20, 160, 160));
        FreePositionController free = mock(FreePositionController.class);
        when(free.bounds()).thenAnswer(i -> new HashMap<>(visible));
        OverlayManager overlays = mock(OverlayManager.class);
        List<Overlay> registered = new ArrayList<>();
        when(overlays.add(any())).thenAnswer(i -> registered.add(i.getArgument(0)));
        FreeOrbOverlayBridge bridge = Guice.createInjector(new AbstractModule() {
            @Override protected void configure() {
                bind(FreePositionController.class).toProvider(() -> free);
                bind(OverlayManager.class).toProvider(() -> overlays);
                bind(net.runelite.api.Client.class).toInstance(client());
                bind(net.runelite.client.config.ConfigManager.class).toInstance(mock(net.runelite.client.config.ConfigManager.class));
                bind(OrbController.class).toProvider(() -> mock(OrbController.class));
            }
        }).getInstance(FreeOrbOverlayBridge.class);
        bridge.start(() -> {});
        Overlay overlay = registered.get(0);
        assertTrue(overlay.isMovable());
        assertTrue(overlay.isSnappable());
        overlay.setPreferredPosition(net.runelite.client.ui.overlay.OverlayPosition.BOTTOM_LEFT);
        overlay.getBounds().setLocation(100, 400);
        assertEquals(new java.awt.Dimension(160, 160), overlay.render(null));
        verify(free).applyPosition(map, new Point(100, 400));
        overlay.getBounds().setLocation(100, 500);
        overlay.render(null);
        verify(free).applyPosition(map, new Point(100, 500));
        visible.clear();
        bridge.sync();
        assertTrue(overlay.getBounds().isEmpty());
        visible.put(map, new Rectangle(100, 500, 160, 160));
        bridge.sync();
        assertEquals(1, registered.size());
        assertEquals(net.runelite.client.ui.overlay.OverlayPosition.BOTTOM_LEFT, overlay.getPreferredPosition());
        bridge.resetAll();
        org.mockito.ArgumentCaptor<Overlay> resets = org.mockito.ArgumentCaptor.forClass(Overlay.class);
        verify(overlays, atLeastOnce()).resetOverlay(resets.capture());
        for (String mode : new String[]{"classic", "modern"})
            for (TargetWidget target : FreePositionController.EXTRA_TARGETS)
                assertTrue(resets.getAllValues().stream().anyMatch(o -> o.getName().equals(
                    "INTERFACE_LAYOUT_ORB_" + mode + "_" + target.getComponentId() + "_" + target.getArrayId())));
    }

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
                bind(net.runelite.api.Client.class).toInstance(client());
                bind(net.runelite.client.config.ConfigManager.class).toInstance(mock(net.runelite.client.config.ConfigManager.class));
                bind(OrbController.class).toProvider(() -> mock(OrbController.class));
            }
        }).getInstance(FreeOrbOverlayBridge.class);
        Runnable refresh = mock(Runnable.class); bridge.start(refresh);
        assertEquals(2, registered.size());
        Overlay hpOverlay = registered.stream().filter(o -> o.getName().contains("_" + hp.getComponentId() + "_")).findFirst().get();
        assertTrue(hpOverlay.isMovable()); assertTrue(hpOverlay.isSnappable());
        hpOverlay.setPreferredLocation(new Point(-100, -100));
        hpOverlay.getBounds().setLocation(800, 500); hpOverlay.render(null);
        verify(free).applyPosition(hp, new Point(800, 500));
        verify(free, never()).applyPosition(eq(prayer), any());
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
