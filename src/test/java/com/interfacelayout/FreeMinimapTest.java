package com.interfacelayout;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.interfacelayout.layout.orbs.FreePositionController;
import com.interfacelayout.layout.orbs.OrbController;
import com.interfacelayout.layout.orbs.OrbLayout;
import com.interfacelayout.layout.orbs.widget.TargetWidget;
import com.interfacelayout.layout.orbs.widget.WidgetManager;
import com.interfacelayout.layout.orbs.widget.elements.Compass;
import com.interfacelayout.layout.orbs.widget.elements.Minimap;
import com.interfacelayout.layout.orbs.widget.elements.Orbs;
import com.interfacelayout.layout.orbs.widget.layout.edit.EditManager;
import com.interfacelayout.util.WidgetGeometry;
import com.interfacelayout.util.WidgetStateStore;
import com.interfacelayout.util.WidgetBoundsExpander;
import java.awt.Point;
import java.awt.Rectangle;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import net.runelite.api.Client;
import net.runelite.api.widgets.Widget;
import net.runelite.client.config.ConfigManager;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class FreeMinimapTest {
    @Test public void movingNativeMapCarriesUnsavedCompassAndClickAreaWithoutMovingOrbs() {
        for (boolean classic : new boolean[]{true, false}) {
            Fixture f = new Fixture(classic);
            assertEquals(new Rectangle(826, 26, 160, 160), f.free.bounds().get(f.mapTarget));
            f.free.save(f.mapTarget, new Point(200, 300));
            f.free.apply();
            assertEquals(new Rectangle(200, 300, 160, 160), f.free.bounds().get(f.mapTarget));
            assertEquals(new Point(204, 304), WidgetGeometry.location(f.map));
            assertEquals(new Point(169, 279), WidgetGeometry.location(f.compass));
            assertEquals(new Point(168, 278), WidgetGeometry.location(f.options));
            assertEquals(new Point(795, 80), WidgetGeometry.location(f.hp));
            assertEquals(1, f.saved.size());
            verify(f.map, never()).setContentType(anyInt());
            verify(f.map, never()).setSpriteId(anyInt());
            verify(f.compass, never()).setAction(anyInt(), anyString());
            f.restore();
            f.free.apply();
            assertEquals(new Point(204, 304), WidgetGeometry.location(f.map));
            assertEquals(new Point(169, 279), WidgetGeometry.location(f.compass));
        }
    }

    @Test public void independentCompassKeepsItsSavedAnchorWhileMapMovesAndClamps() {
        Fixture f = new Fixture(false);
        f.free.save(f.compassTarget, new Point(60, 90));
        f.free.save(f.mapTarget, new Point(5000, 5000));
        f.free.apply();
        assertEquals(new Rectangle(840, 540, 160, 160), f.free.bounds().get(f.mapTarget));
        assertEquals(new Point(60, 90), WidgetGeometry.location(f.compass));
        assertEquals(new Point(59, 89), WidgetGeometry.location(f.options));
        f.free.move(f.mapTarget, new Point(100, 100));
        assertEquals(new Point(60, 90), WidgetGeometry.location(f.compass));
    }

    @Test public void mapPositionSurvivesHideAndShowButResetRestoresNativeLayout() {
        Fixture f = new Fixture(true);
        f.free.save(f.mapTarget, new Point(100, 200));
        f.free.apply();
        f.restore();
        when(f.config.hideMinimap()).thenReturn(true);
        f.free.apply();
        assertTrue(f.map.isHidden());
        assertFalse(f.free.bounds().containsKey(f.mapTarget));
        assertTrue(f.free.bounds().containsKey(f.compassTarget));
        assertEquals(1, f.saved.size());
        f.restore();
        when(f.config.hideMinimap()).thenReturn(false);
        f.free.apply();
        assertEquals(new Point(104, 204), WidgetGeometry.location(f.map));
        f.free.resetAll();
        f.restore();
        f.free.apply();
        assertTrue(f.saved.isEmpty());
        assertEquals(new Point(830, 30), WidgetGeometry.location(f.map));
        assertEquals(new Point(795, 5), WidgetGeometry.location(f.compass));
    }

    @Test public void hiddenMinimizedFixedAndLoggedOutMapsAreNotExposedForDragging() {
        Fixture f = new Fixture(false);
        f.map.setHidden(true);
        assertFalse(f.free.bounds().containsKey(f.mapTarget));
        f.free.save(f.mapTarget, new Point(100, 200));
        f.free.apply();
        verify(f.map, never()).setForcedPosition(anyInt(), anyInt());
        f.map.setHidden(false);
        when(f.manager.isMinimapMinimized()).thenReturn(true);
        assertTrue(f.free.bounds().isEmpty());
        when(f.manager.isMinimapMinimized()).thenReturn(false);
        when(f.manager.isFixedMode()).thenReturn(true);
        assertFalse(f.free.bounds().containsKey(f.mapTarget));
        when(f.manager.isFixedMode()).thenReturn(false);
        when(f.manager.isLoggedIn()).thenReturn(false);
        assertTrue(f.free.bounds().isEmpty());
    }

    private static final class Fixture {
        final Client client = mock(Client.class);
        final InterfaceLayoutConfig config = mock(InterfaceLayoutConfig.class, CALLS_REAL_METHODS);
        final OrbController manager = mock(OrbController.class);
        final WidgetStateStore states = new WidgetStateStore();
        final Map<String, Object> saved = new HashMap<>();
        final Widget root = widget(0, 0, 1000, 700), parent = widget(790, 0, 210, 207);
        final Widget map = widget(40, 30, 152, 152), compass = widget(5, 5, 33, 33);
        final Widget options = widget(4, 4, 35, 35), hp = widget(5, 80, 56, 36);
        final TargetWidget mapTarget, compassTarget;
        final FreePositionController free;
        final WidgetBoundsExpander expander;
        Fixture(boolean classic) {
            mapTarget = classic ? Minimap.CLASSIC_MINIMAP_MASK : Minimap.MODERN_MINIMAP_MASK;
            compassTarget = classic ? Compass.CLASSIC_COMPASS : Compass.MODERN_COMPASS;
            when(client.getCanvasWidth()).thenReturn(1000);
            when(client.getCanvasHeight()).thenReturn(700);
            when(manager.isLoggedIn()).thenReturn(true);
            when(manager.isClassicResizable()).thenReturn(classic);
            when(config.layout()).thenReturn(OrbLayout.FREE_POSITION);
            when(parent.getParent()).thenReturn(root);
            for (Widget child : new Widget[]{map, compass, options, hp}) when(child.getParent()).thenReturn(parent);
            when(parent.getStaticChildren()).thenReturn(new Widget[]{map, compass, options, hp});
            WidgetManager widgets = mock(WidgetManager.class);
            when(widgets.getTargetWidget(mapTarget)).thenReturn(map);
            when(widgets.getTargetWidget(compassTarget)).thenReturn(compass);
            when(widgets.getTargetWidget(classic ? Compass.CLASSIC_COMPASS_OPTIONS : Compass.MODERN_COMPASS_OPTIONS)).thenReturn(options);
            when(widgets.getTargetWidget(Orbs.HP_ORB_CONTAINER)).thenReturn(hp);
            ConfigManager configs = mock(ConfigManager.class);
            when(configs.getConfiguration(anyString(), anyString(), any(Type.class))).thenAnswer(i -> saved.get(i.getArgument(1)));
            doAnswer(i -> { saved.put(i.getArgument(1), i.getArgument(2)); return null; })
                .when(configs).setConfiguration(anyString(), anyString(), any(Object.class));
            doAnswer(i -> { saved.remove(i.getArgument(1)); return null; }).when(configs).unsetConfiguration(anyString(), anyString());
            com.google.inject.Injector injector = Guice.createInjector(new AbstractModule() {
                @Override protected void configure() {
                    bind(Client.class).toInstance(client);
                    bind(InterfaceLayoutConfig.class).toInstance(config);
                    bind(OrbController.class).toProvider(() -> manager);
                    bind(ConfigManager.class).toInstance(configs);
                    bind(WidgetManager.class).toProvider(() -> widgets);
                    bind(EditManager.class).toProvider(() -> mock(EditManager.class));
                    bind(WidgetStateStore.class).toInstance(states);
                }
            });
            free = injector.getInstance(FreePositionController.class);
            expander = injector.getInstance(WidgetBoundsExpander.class);
        }
        void restore() { states.restore(); expander.clear(); }
    }

    private static Widget widget(int x, int y, int width, int height) {
        Widget widget = WidgetGeometryTest.widget(x, y, width, height);
        int[] position = {x, y};
        when(widget.getOriginalX()).thenReturn(x);
        when(widget.getOriginalY()).thenReturn(y);
        when(widget.getRelativeX()).thenAnswer(i -> position[0]);
        when(widget.getRelativeY()).thenAnswer(i -> position[1]);
        doAnswer(i -> {
            int px = i.getArgument(0), py = i.getArgument(1);
            position[0] = px == -1 ? x : px;
            position[1] = py == -1 ? y : py;
            return null;
        }).when(widget).setForcedPosition(anyInt(), anyInt());
        boolean[] hidden = {false};
        when(widget.isHidden()).thenAnswer(i -> hidden[0]);
        when(widget.isSelfHidden()).thenAnswer(i -> hidden[0]);
        doAnswer(i -> { hidden[0] = i.getArgument(0); return null; }).when(widget).setHidden(anyBoolean());
        return widget;
    }
}
