package com.interfacelayout;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.interfacelayout.layout.menu.MenuOverlayBridge;
import com.interfacelayout.layout.menu.MenuStoneController;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayManager;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class MenuOverlayBridgeTest
{
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
        when(unrelated.getName()).thenReturn("Some other plugin");
        List<Overlay> registered = new ArrayList<>(List.of(nativeTop, nativeBottom, unrelated));
        when(overlays.removeIf(any())).thenAnswer(i -> registered.removeIf(i.getArgument(0)));
        when(overlays.add(any())).thenAnswer(i -> registered.add(i.getArgument(0)));
        when(overlays.remove(any())).thenAnswer(i -> registered.remove(i.getArgument(0)));
        MenuOverlayBridge bridge = Guice.createInjector(new AbstractModule() {
            @Override protected void configure() {
                bind(MenuStoneController.class).toProvider(() -> menu);
                bind(OverlayManager.class).toProvider(() -> overlays);
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
        verify(menu, times(1)).saveGroup(0, new Point(500, 300));
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
            }
        }).getInstance(MenuOverlayBridge.class);
        bridge.start(() -> {}); bridge.clearPreference(0); added.get(0).render(null);
        verify(menu, never()).resetGroup(anyInt());
        assertEquals(new Point(100, 200), added.get(0).getBounds().getLocation());
    }
}
