package com.interfacelayout;

import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.OverlayPosition;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.interfacelayout.util.LayoutGeometry;
import com.interfacelayout.util.LayoutPositionStore;
import com.interfacelayout.util.OverlayPlacement;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Point;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import net.runelite.api.Client;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.RuneLiteConfig;
import net.runelite.client.ui.overlay.tooltip.TooltipOverlay;
import net.runelite.client.ui.overlay.worldmap.WorldMapOverlay;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class OverlayPlacementTest
{
    @Test public void customUiAnchorAssignmentsKeepTheirAbsolutePreferredLocation()
    {
        Fixture f = new Fixture(1920, 1080);
        Overlay overlay = f.overlay();
        Point point = new Point(700, 500);
        overlay.setPreferredLocation(point);
        f.manager.saveOverlay(overlay);
        f.saved.put("anchorcustomizer.overlayAssignments", "{\"" + overlay.getName() + "\":2}");
        Map<String, Object> saved = new HashMap<>(f.saved);
        assertFalse(f.placement.adopt(overlay, point));
        assertEquals(point, overlay.getPreferredLocation());
        assertEquals(saved, f.saved);
    }
    @Test public void legacyAbsoluteOverlayKeepsItsLocationAndThenFollowsCanvasEdges()
    {
        Fixture f = new Fixture(5120, 1440);
        Overlay overlay = f.overlay();
        Point original = new Point(5000, 1370);
        overlay.setPreferredLocation(original);
        f.manager.saveOverlay(overlay);
        f.manager.add(overlay);
        f.positions.save("menuPosition_modern", original);
        assertTrue(f.placement.adopt(overlay, original));
        assertNull(overlay.getPreferredLocation());
        assertEquals(OverlayPosition.DYNAMIC, overlay.getPreferredPosition());
        assertEquals(original, f.position());
        Map<String, Object> saved = new HashMap<>(f.saved);
        f.resize(1920, 1080);
        assertEquals(new Point(1800, 1010), f.position());
        f.resize(765, 504);
        assertEquals(new Point(645, 434), f.position());
        f.resize(5120, 1440);
        assertEquals(original, f.position());
        assertEquals(saved, f.saved);
        Overlay reopened = f.overlay();
        f.manager.add(reopened);
        assertNull(reopened.getPreferredLocation());
        assertEquals(OverlayPosition.DYNAMIC, reopened.getPreferredPosition());
        f.resize(1920, 1080);
        assertEquals(new Point(1800, 1010), f.position());
        f.manager.resetOverlay(overlay);
        assertNull(overlay.getPreferredPosition());
    }

    @Test public void manualWidgetAndSnapAnchorsRemainUntouched()
    {
        Fixture f = new Fixture(1920, 1080);
        Overlay overlay = f.overlay();
        Point point = new Point(200, 120);
        for (String origin : new String[]{"MANUAL", "SIDEPANEL"})
        {
            f.saved.put("runelite." + overlay.getName() + "_origin", origin);
            f.saved.put("runelite." + overlay.getName() + "_originX", "LEFT");
            f.saved.put("runelite." + overlay.getName() + "_originY", "TOP");
            f.saved.put("runelite." + overlay.getName() + "_preferredLocation", point);
            f.manager.remove(overlay); f.manager.add(overlay);
            assertFalse(f.placement.adopt(overlay, point));
            assertEquals(origin, f.saved.get("runelite." + overlay.getName() + "_origin"));
            assertEquals(point, overlay.getPreferredLocation());
        }
        
        overlay.setPreferredPosition(OverlayPosition.BOTTOM_RIGHT);
        f.manager.saveOverlay(overlay);
        assertFalse(f.placement.adopt(overlay, point));
        assertEquals(OverlayPosition.BOTTOM_RIGHT, overlay.getPreferredPosition());
    }

    @Test public void nativeRelativeOriginsAndUnsavedDragsAreNotAdopted()
    {
        Fixture f = new Fixture(1920, 1080);
        Overlay overlay = f.overlay();
        Point point = new Point(-200, -120);
        f.saved.put("runelite." + overlay.getName() + "_origin", "AUTO");
        f.saved.put("runelite." + overlay.getName() + "_originX", "RIGHT");
        f.saved.put("runelite." + overlay.getName() + "_originY", "BOTTOM");
        f.saved.put("runelite." + overlay.getName() + "_preferredLocation", point);
        f.manager.add(overlay);
        assertFalse(f.placement.adopt(overlay, point));
        f.saved.put("runelite." + overlay.getName() + "_originX", "LEFT");
        f.saved.put("runelite." + overlay.getName() + "_originY", "TOP");
        f.manager.remove(overlay); f.manager.add(overlay);
        overlay.setPreferredLocation(new Point(400, 300));
        f.manager.saveOverlay(overlay);
        assertFalse(f.placement.adopt(overlay, new Point(300, 200)));
    }

    @Test public void invalidCanvasOrOffscreenLegacyDataAreNotRebased()
    {
        Fixture f = new Fixture(0, 0);
        Overlay overlay = f.overlay();
        Point point = new Point(5000, 1370);
        overlay.setPreferredLocation(point);
        assertFalse(f.placement.adopt(overlay, point));
        f.resize(1920, 1080);
        assertFalse(f.placement.adopt(overlay, point));
        assertEquals(point, overlay.getPreferredLocation());
        assertTrue(f.saved.isEmpty());
    }

    private static final class Fixture
    {
        final Client client = mock(Client.class);
        final Map<String, Object> saved = new HashMap<>();
        final OverlayManager manager;
        final OverlayPlacement placement;
        final LayoutPositionStore positions;
        Fixture(int width, int height)
        {
            resize(width, height);
            ConfigManager configs = mock(ConfigManager.class);
            when(configs.getConfiguration(anyString(), anyString(), any(Type.class)))
                .thenAnswer(i -> {
                    Object value = saved.get(i.getArgument(0) + "." + i.getArgument(1));
                    Type type = i.getArgument(2);
                    if (value instanceof String && type instanceof Class && ((Class<?>) type).isEnum())
                        return Enum.valueOf((Class) type, (String) value);
                    return value;
                });
            when(configs.getConfiguration(anyString(), anyString())).thenAnswer(i -> {
                Object value = saved.get(i.getArgument(0) + "." + i.getArgument(1));
                return value == null ? null : value.toString();
            });
            doAnswer(i -> { saved.put(i.getArgument(0) + "." + i.getArgument(1), i.getArgument(2)); return null; })
                .when(configs).setConfiguration(anyString(), anyString(), any(Object.class));
            doAnswer(i -> { saved.remove(i.getArgument(0) + "." + i.getArgument(1)); return null; })
                .when(configs).unsetConfiguration(anyString(), anyString());
            Injector injector = Guice.createInjector(new AbstractModule() {
                @Override protected void configure()
                {
                    bind(Client.class).toInstance(client);
                    bind(ConfigManager.class).toInstance(configs);
                    bind(RuneLiteConfig.class).toInstance(mock(RuneLiteConfig.class));
                    bind(ChatMessageManager.class).toInstance(mock(ChatMessageManager.class));
                    
                    bind(TooltipOverlay.class).toInstance(mock(TooltipOverlay.class));
                    bind(WorldMapOverlay.class).toInstance(mock(WorldMapOverlay.class));
                }
            });
            manager = injector.getInstance(OverlayManager.class);
            placement = injector.getInstance(OverlayPlacement.class);
            positions = injector.getInstance(LayoutPositionStore.class);
        }
        void resize(int width, int height)
        {
            when(client.getRealDimensions()).thenReturn(new Dimension(width, height));
            when(client.getCanvasWidth()).thenReturn(width);
            when(client.getCanvasHeight()).thenReturn(height);
        }
        Overlay overlay()
        {
            Overlay overlay = new Overlay() {
                { setPosition(OverlayPosition.DYNAMIC); setMovable(true); setSnappable(true); }
                @Override public String getName() { return "INTERFACE_LAYOUT_RESIZE_TEST"; }
                @Override public Dimension render(Graphics2D graphics) { return new Dimension(33, 36); }
            };

            overlay.getBounds().setSize(33, 36);
            return overlay;
        }
        Point position()
        {
            Point point = positions.load("menuPosition_modern", 33, 36);
            return LayoutGeometry.clamp(point, 33, 36, client.getCanvasWidth(), client.getCanvasHeight());
        }
    }
}
