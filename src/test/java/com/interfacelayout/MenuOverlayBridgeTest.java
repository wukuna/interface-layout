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
    @Test public void responsiveGroupOnlyResetsWhenItsOwnNativePreferenceIsReset()
    {
        MenuStoneController menu = mock(MenuStoneController.class);
        when(menu.mode()).thenReturn("modern"); when(menu.layoutMode()).thenReturn("modern");
        when(menu.individualDragging()).thenReturn(true);
        when(menu.groups()).thenReturn(Map.of(0, new Rectangle(1500, 700, 33, 36)));
        when(menu.savedGroup(0)).thenReturn(new Point(1500, 700));
        OverlayManager overlays = mock(OverlayManager.class);
        List<Overlay> added = new ArrayList<>();
        when(overlays.add(any())).thenAnswer(i -> added.add(i.getArgument(0)));
        doAnswer(i -> {
            Overlay overlay = i.getArgument(0);
            overlay.setPreferredLocation(null); overlay.setPreferredPosition(null); overlay.revalidate(); return null;
        }).when(overlays).resetOverlay(any());
        MenuOverlayBridge bridge = Guice.createInjector(new AbstractModule() {
            @Override protected void configure() {
                bind(MenuStoneController.class).toProvider(() -> menu);
                bind(OverlayManager.class).toProvider(() -> overlays);
                bind(net.runelite.api.Client.class).toInstance(client());
                bind(net.runelite.client.config.ConfigManager.class).toInstance(mock(net.runelite.client.config.ConfigManager.class));
                bind(WidgetBoundsExpander.class).toProvider(() -> mock(WidgetBoundsExpander.class));
            }
        }).getInstance(MenuOverlayBridge.class);
        bridge.start(() -> {});
        Overlay group = added.get(0);
        assertEquals(OverlayPosition.DYNAMIC, group.getPreferredPosition());
        group.render(null); group.revalidate(); group.render(null);
        verify(menu, never()).resetGroup(anyInt());
        overlays.resetOverlay(group); group.render(null);
        verify(menu).resetGroup(0);
        bridge.profileChanged(); group.render(null);
        verify(menu, times(1)).resetGroup(0);
    }
    private static net.runelite.api.Client client()
    {
        net.runelite.api.Client client = mock(net.runelite.api.Client.class);
        when(client.getRealDimensions()).thenReturn(new java.awt.Dimension(2000, 2000));
        return client;
    }

    @Test public void newGroupIdentityRetiresOldOverlayAndGroupingSetsNativePreference()
    {
        MenuStoneController menu = mock(MenuStoneController.class);
        when(menu.mode()).thenReturn("modern"); when(menu.layoutMode()).thenReturn("modern"); when(menu.individualDragging()).thenReturn(true);
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
                bind(net.runelite.api.Client.class).toInstance(client());
                bind(net.runelite.client.config.ConfigManager.class).toInstance(mock(net.runelite.client.config.ConfigManager.class));
                bind(WidgetBoundsExpander.class).toProvider(() -> mock(WidgetBoundsExpander.class));
            }
        }).getInstance(MenuOverlayBridge.class);
        bridge.start(() -> {});
        Overlay retired = added.get(0);
        bridge.moveGroupingStone(1, new Point(700, 400), true);
        assertNull(retired.getPreferredLocation());
        verify(menu).saveGroup(1, new Point(700, 400));
        bridge.finishGroupingStone(1); verify(overlays, never()).saveOverlay(retired);
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
        when(menu.mode()).thenReturn("modern"); when(menu.layoutMode()).thenReturn("modern"); when(menu.individualDragging()).thenReturn(individual);
        Rectangle actual = new Rectangle(400, 200, 33, 76);
        when(menu.groups()).thenAnswer(i -> Map.of(0, new Rectangle(actual)));
        doAnswer(i -> { actual.setLocation((Point)i.getArgument(1)); return null; }).when(menu).applyGroup(anyInt(), any(Point.class));
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
                bind(net.runelite.api.Client.class).toInstance(client());
                bind(net.runelite.client.config.ConfigManager.class).toInstance(mock(net.runelite.client.config.ConfigManager.class));
                bind(WidgetBoundsExpander.class).toProvider(() -> mock(WidgetBoundsExpander.class));
            }
        }).getInstance(MenuOverlayBridge.class);
        Runnable refresh = mock(Runnable.class); bridge.start(refresh);
        assertEquals(!individual, registered.contains(nativeTop));
        assertFalse(registered.contains(nativeBottom)); assertTrue(registered.contains(unrelated));
        Overlay group = registered.stream().filter(o -> o instanceof MenuOverlayBridge.GroupOverlay).findFirst().get();
        assertTrue(group.isMovable()); assertTrue(group.isSnappable());
        group.setPreferredLocation(new Point(500, 300));

        group.getBounds().setLocation(500, 300); group.render(null);
        verify(menu).applyGroup(0, new Point(500, 300)); verify(refresh).run();
        group.render(null); verify(refresh, times(1)).run();
        actual.setSize(33, 112); group.revalidate();
        group.getBounds().setLocation(500, 264);
        group.render(null);
        verify(menu, never()).resetGroup(anyInt());
        verify(menu).applyGroup(0, new Point(500, 264));

        group.setPreferredLocation(null); group.revalidate();

        group.setPreferredLocation(new Point(-180, -160));
        group.getBounds().setLocation(800, 600); group.render(null);
        verify(menu).applyGroup(0, new Point(800, 600));

        group.getBounds().setLocation(1000, 700); group.render(null);
        verify(menu).applyGroup(0, new Point(1000, 700));
        verify(menu, never()).resetGroup(anyInt());
        bridge.stop();
        assertTrue(registered.contains(nativeTop)); assertTrue(registered.contains(nativeBottom));
        assertFalse(registered.contains(group)); assertTrue(registered.contains(unrelated));
    }
    @Test public void manualEditingClearsNativeOverlayPreferenceWithoutResettingSavedGroup()
    {
        MenuStoneController menu = mock(MenuStoneController.class);
        when(menu.mode()).thenReturn("modern"); when(menu.layoutMode()).thenReturn("modern"); when(menu.individualDragging()).thenReturn(true);
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
                bind(net.runelite.api.Client.class).toInstance(client());
                bind(net.runelite.client.config.ConfigManager.class).toInstance(mock(net.runelite.client.config.ConfigManager.class));
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
            when(menu.mode()).thenReturn(classic ? "classic" : "modern"); when(menu.layoutMode()).thenReturn(classic ? "classic" : "modern");
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
                bind(net.runelite.api.Client.class).toInstance(client());
                bind(net.runelite.client.config.ConfigManager.class).toInstance(mock(net.runelite.client.config.ConfigManager.class));
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
            when(menu.mode()).thenReturn("modern"); when(menu.layoutMode()).thenReturn("modern"); when(menu.individualDragging()).thenReturn(true);
            when(menu.groups()).thenReturn(Map.of(7, new Rectangle(20, 20, column ? 33 : 99, column ? 108 : 36)));
            List<Overlay> registered = new ArrayList<>();
            OverlayManager overlays = mock(OverlayManager.class);
            when(overlays.add(any())).thenAnswer(i -> registered.add(i.getArgument(0)));
            MenuOverlayBridge bridge = Guice.createInjector(new AbstractModule() {
                @Override protected void configure() {
                    bind(MenuStoneController.class).toProvider(() -> menu);
                    bind(OverlayManager.class).toProvider(() -> overlays);
                bind(net.runelite.api.Client.class).toInstance(client());
                bind(net.runelite.client.config.ConfigManager.class).toInstance(mock(net.runelite.client.config.ConfigManager.class));
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
                verify(menu).applyGroup(7, canvas);
            }
            group.setPreferredLocation(null); group.setPreferredPosition(OverlayPosition.BOTTOM_RIGHT);
            group.getBounds().setLocation(800, 500); group.render(null);
            verify(menu).applyGroup(7, new Point(800, 500));
            verify(menu, never()).resetGroup(anyInt());
        }
    }
}
