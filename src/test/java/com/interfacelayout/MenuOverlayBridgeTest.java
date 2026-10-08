package com.interfacelayout;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.interfacelayout.layout.menu.MenuOverlayBridge;
import com.interfacelayout.layout.menu.MenuStoneController;
import com.interfacelayout.util.WidgetBoundsExpander;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.OverlayPosition;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class MenuOverlayBridgeTest
{
    @Test public void newGroupIdentityRetiresOldOverlayAndGroupingSetsNativePreference()
    {
        MenuStoneController menu = mock(MenuStoneController.class);
        when(menu.mode()).thenReturn("modern"); when(menu.individualDragging()).thenReturn(true);
        when(menu.groups()).thenReturn(Map.of(1, new Rectangle(100, 200, 33, 36)));
        when(menu.groupGeneration(1)).thenReturn(1);
        OverlayManager overlays = mock(OverlayManager.class);
        List<Overlay> added = new ArrayList<>();
        when(overlays.add(any())).thenAnswer(i -> added.add(i.getArgument(0)));
        when(overlays.remove(any())).thenAnswer(i -> added.remove(i.getArgument(0)));
        doAnswer(i -> { Overlay overlay = i.getArgument(0); overlay.setPreferredLocation(null); overlay.revalidate(); return null; })
            .when(overlays).resetOverlay(any());
        MenuOverlayBridge bridge = Guice.createInjector(new AbstractModule() {
            @Override protected void configure() {
                bind(MenuStoneController.class).toProvider(() -> menu);
                bind(OverlayManager.class).toProvider(() -> overlays);
                bind(WidgetBoundsExpander.class).toProvider(() -> mock(WidgetBoundsExpander.class));
            }
        }).getInstance(MenuOverlayBridge.class);
        bridge.start(() -> {});
        Overlay retired = added.get(0);
        bridge.moveGroupingStone(1, new Point(700, 400), true);
        assertEquals(new Point(700, 400), retired.getPreferredLocation());
        bridge.finishGroupingStone(1); verify(overlays).saveOverlay(retired);
        when(menu.groupGeneration(1)).thenReturn(2); bridge.sync();
        assertEquals(1, added.size()); assertNotEquals(retired.getName(), added.get(0).getName());
        assertTrue(retired.getBounds().isEmpty()); assertNull(added.get(0).getPreferredLocation());
        bridge.stop(); assertTrue(added.isEmpty()); assertTrue(retired.getBounds().isEmpty());
    }
    @Test public void nativeRendererPositionMovesGroupAndNativeRowsReturnOnShutdown()
    { lifecycle(true); }
    @Test public void compactingOneNativeRowKeepsOtherRowsBuiltInAltMovement()
    { lifecycle(false); }
    private void lifecycle(boolean individual)
    {
        MenuStoneController menu = mock(MenuStoneController.class);
        when(menu.mode()).thenReturn("modern"); when(menu.individualDragging()).thenReturn(individual);
        Rectangle actual = new Rectangle(400, 200, 33, 76);
        when(menu.groups()).thenAnswer(i -> Map.of(0, new Rectangle(actual)));
        doAnswer(i -> { actual.setLocation((Point)i.getArgument(1)); return null; }).when(menu).saveGroup(anyInt(), any(Point.class));
        OverlayManager overlays = mock(OverlayManager.class);
        Overlay nativeTop = mock(Overlay.class), nativeBottom = mock(Overlay.class), unrelated = mock(Overlay.class);
        when(nativeTop.getName()).thenReturn("RESIZABLE_VIEWPORT_BOTTOM_LINE_TABS1");
        when(nativeBottom.getName()).thenReturn("RESIZABLE_VIEWPORT_BOTTOM_LINE_TABS2");
        when(nativeTop.getBounds()).thenReturn(new Rectangle());
        when(nativeBottom.getBounds()).thenReturn(new Rectangle());
        when(unrelated.getName()).thenReturn("Some other plugin");
        List<Overlay> registered = new ArrayList<>(List.of(nativeTop, nativeBottom, unrelated));
        when(overlays.removeIf(any())).thenAnswer(i -> registered.removeIf(i.getArgument(0)));
        when(overlays.add(any())).thenAnswer(i -> registered.add(i.getArgument(0)));
        when(overlays.remove(any())).thenAnswer(i -> registered.remove(i.getArgument(0)));
        MenuOverlayBridge bridge = Guice.createInjector(new AbstractModule() {
            @Override protected void configure() {
                bind(MenuStoneController.class).toProvider(() -> menu);
                bind(OverlayManager.class).toProvider(() -> overlays);
                bind(WidgetBoundsExpander.class).toProvider(() -> mock(WidgetBoundsExpander.class));
            }
        }).getInstance(MenuOverlayBridge.class);
        Runnable refresh = mock(Runnable.class); bridge.start(refresh);
        assertEquals(!individual, registered.contains(nativeTop));
        assertFalse(registered.contains(nativeBottom)); assertTrue(registered.contains(unrelated));
        Overlay group = registered.stream().filter(o -> o instanceof MenuOverlayBridge.GroupOverlay).findFirst().get();
        assertTrue(group.isMovable()); assertTrue(group.isSnappable());
        group.setPreferredLocation(new Point(500, 300));
        // OverlayRenderer sets this after resolving origins, snapping and clamping.
        group.getBounds().setLocation(500, 300); group.render(null);
        verify(menu).saveGroup(0, new Point(500, 300)); verify(refresh).run();
        group.render(null); verify(refresh, times(1)).run();
        actual.setSize(33, 112); group.revalidate();
        group.getBounds().setLocation(500, 264); // Renderer may clamp after a size change.
        group.render(null);
        verify(menu, never()).resetGroup(anyInt());
        verify(menu).saveGroup(0, new Point(500, 264));
        // Custom UI Anchors normalizes origins by resetting the overlay before
        // assigning a new preferred location. This is not a user layout reset.
        group.setPreferredLocation(null); group.revalidate();
        // Crossing the canvas midpoint changes RuneLite's origin and makes the
        // stored offset negative. The stones must follow the resolved outline.
        group.setPreferredLocation(new Point(-180, -160));
        group.getBounds().setLocation(800, 600); group.render(null);
        verify(menu).saveGroup(0, new Point(800, 600));
        // The anchor/window can move without changing the relative offset.
        group.getBounds().setLocation(1000, 700); group.render(null);
        verify(menu).saveGroup(0, new Point(1000, 700));
        verify(menu, never()).resetGroup(anyInt());
        bridge.stop();
        assertTrue(registered.contains(nativeTop)); assertTrue(registered.contains(nativeBottom));
        assertFalse(registered.contains(group)); assertTrue(registered.contains(unrelated));
    }
    @Test public void manualEditingClearsNativeOverlayPreferenceWithoutResettingSavedGroup()
    {
        MenuStoneController menu = mock(MenuStoneController.class);
        when(menu.mode()).thenReturn("modern"); when(menu.individualDragging()).thenReturn(true);
        when(menu.groups()).thenReturn(Map.of(0, new Rectangle(100, 200, 33, 36)));
        OverlayManager overlays = mock(OverlayManager.class);
        List<Overlay> added = new ArrayList<>();
        when(overlays.add(any())).thenAnswer(i -> added.add(i.getArgument(0)));
        doAnswer(i -> { Overlay overlay = i.getArgument(0); overlay.setPreferredLocation(null); overlay.revalidate(); return null; })
            .when(overlays).resetOverlay(any());
        MenuOverlayBridge bridge = Guice.createInjector(new AbstractModule() {
            @Override protected void configure() {
                bind(MenuStoneController.class).toProvider(() -> menu);
                bind(OverlayManager.class).toProvider(() -> overlays);
                bind(WidgetBoundsExpander.class).toProvider(() -> mock(WidgetBoundsExpander.class));
            }
        }).getInstance(MenuOverlayBridge.class);
        bridge.start(() -> {}); bridge.clearPreference(0); added.get(0).render(null);
        verify(menu, never()).resetGroup(anyInt());
        assertEquals(new Point(100, 200), added.get(0).getBounds().getLocation());
    }

    @Test public void expandedMapClippingDoesNotBecomeFullCanvasOverlayOrSnapCorner()
    {
        for (boolean classic : new boolean[]{true, false})
        {
            MenuStoneController menu = mock(MenuStoneController.class);
            when(menu.mode()).thenReturn(classic ? "classic" : "modern");
            when(menu.groups()).thenReturn(Map.of());
            WidgetBoundsExpander expander = mock(WidgetBoundsExpander.class);
            int container = classic ? InterfaceID.ToplevelOsrsStretch.MAP_CONTAINER
                : InterfaceID.ToplevelPreEoc.MAP_CONTAINER;
            when(expander.isExpanded(container)).thenReturn(true);
            Overlay map = mock(Overlay.class), unrelated = mock(Overlay.class);
            when(map.getName()).thenReturn(classic ? "RESIZABLE_MINIMAP_STONES_WIDGET" : "RESIZABLE_MINIMAP_WIDGET");
            Rectangle mapBounds = new Rectangle(0, 0, 1200, 800);
            when(map.getBounds()).thenReturn(mapBounds);
            when(unrelated.getName()).thenReturn("Some other plugin");
            List<Overlay> registered = new ArrayList<>(List.of(map, unrelated));
            OverlayManager overlays = mock(OverlayManager.class);
            when(overlays.removeIf(any())).thenAnswer(i -> registered.removeIf(i.getArgument(0)));
            when(overlays.add(any())).thenAnswer(i -> registered.add(i.getArgument(0)));
            MenuOverlayBridge bridge = Guice.createInjector(new AbstractModule() {
                @Override protected void configure() {
                    bind(MenuStoneController.class).toProvider(() -> menu);
                    bind(OverlayManager.class).toProvider(() -> overlays);
                    bind(WidgetBoundsExpander.class).toProvider(() -> expander);
                }
            }).getInstance(MenuOverlayBridge.class);
            bridge.start(() -> {});
            assertEquals(List.of(unrelated), registered);
            assertTrue(mapBounds.isEmpty());
            when(expander.isExpanded(container)).thenReturn(false);
            bridge.sync(); assertTrue(registered.contains(map));
            when(expander.isExpanded(container)).thenReturn(true);
            bridge.sync(); assertFalse(registered.contains(map));
            bridge.stop(); assertTrue(registered.contains(map));
            verify(overlays, never()).resetOverlay(map);
        }
    }

    @Test public void detachedCustomBarsFollowResolvedOutlineAcrossQuadrantsAndSnapping()
    {
        for (boolean column : new boolean[]{true, false})
        {
            MenuStoneController menu = mock(MenuStoneController.class);
            when(menu.mode()).thenReturn("modern"); when(menu.individualDragging()).thenReturn(true);
            when(menu.groups()).thenReturn(Map.of(7, new Rectangle(20, 20, column ? 33 : 99, column ? 108 : 36)));
            List<Overlay> registered = new ArrayList<>();
            OverlayManager overlays = mock(OverlayManager.class);
            when(overlays.add(any())).thenAnswer(i -> registered.add(i.getArgument(0)));
            MenuOverlayBridge bridge = Guice.createInjector(new AbstractModule() {
                @Override protected void configure() {
                    bind(MenuStoneController.class).toProvider(() -> menu);
                    bind(OverlayManager.class).toProvider(() -> overlays);
                    bind(WidgetBoundsExpander.class).toProvider(() -> mock(WidgetBoundsExpander.class));
                }
            }).getInstance(MenuOverlayBridge.class);
            bridge.start(() -> {});
            Overlay group = registered.get(0);
            Point[] positions = {new Point(50, 50), new Point(900, 50), new Point(900, 600), new Point(50, 600)};
            for (Point canvas : positions)
            {
                group.setPreferredLocation(new Point(canvas.x > 500 ? -100 : 50, canvas.y > 300 ? -100 : 50));
                group.getBounds().setLocation(canvas); group.render(null);
                verify(menu).saveGroup(7, canvas);
            }
            group.setPreferredLocation(null); group.setPreferredPosition(OverlayPosition.BOTTOM_RIGHT);
            group.getBounds().setLocation(800, 500); group.render(null);
            verify(menu).saveGroup(7, new Point(800, 500));
            verify(menu, never()).resetGroup(anyInt());
        }
    }
}
