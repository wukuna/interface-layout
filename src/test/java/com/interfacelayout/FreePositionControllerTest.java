package com.interfacelayout;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.interfacelayout.layout.orbs.FreePositionController;
import com.interfacelayout.layout.orbs.OrbController;
import com.interfacelayout.layout.orbs.OrbLayout;
import com.interfacelayout.layout.orbs.widget.TargetWidget;
import com.interfacelayout.layout.orbs.widget.elements.Compass;
import com.interfacelayout.layout.orbs.widget.elements.Minimap;
import com.interfacelayout.layout.orbs.widget.WidgetManager;
import com.interfacelayout.layout.orbs.widget.elements.Orbs;
import com.interfacelayout.layout.orbs.widget.layout.edit.EditManager;
import com.interfacelayout.util.WidgetGeometry;
import com.interfacelayout.util.WidgetStateStore;
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

public class FreePositionControllerTest
{
    @Test public void compassMovesWithClickAreaAndPersistsWithoutMovingMinimapInBothResizableModes()
    {
        for (boolean classic : new boolean[]{false, true})
        {
            Client client = mock(Client.class);
            when(client.getCanvasWidth()).thenReturn(1000); when(client.getCanvasHeight()).thenReturn(700);
            Widget root = restorableWidget(0, 0, 1000, 700);
            Widget parent = restorableWidget(790, 0, 210, 207);
            Widget map = restorableWidget(30, 30, 150, 150);
            Widget compass = restorableWidget(5, 5, 33, 33);
            Widget options = restorableWidget(4, 4, 35, 35);
            when(parent.getParent()).thenReturn(root);
            for (Widget widget : new Widget[]{map, compass, options})
            {
                when(widget.getParent()).thenReturn(parent);
                boolean[] hidden = {false};
                when(widget.isHidden()).thenAnswer(i -> hidden[0]);
                when(widget.isSelfHidden()).thenAnswer(i -> hidden[0]);
                doAnswer(i -> { hidden[0] = i.getArgument(0); return null; }).when(widget).setHidden(anyBoolean());
            }
            when(parent.getStaticChildren()).thenReturn(new Widget[]{map, compass, options});
            TargetWidget target = classic ? Compass.CLASSIC_COMPASS : Compass.MODERN_COMPASS;
            TargetWidget click = classic ? Compass.CLASSIC_COMPASS_OPTIONS : Compass.MODERN_COMPASS_OPTIONS;
            WidgetManager widgets = mock(WidgetManager.class);
            when(widgets.getTargetWidget(target)).thenReturn(compass);
            when(widgets.getTargetWidget(click)).thenReturn(options);
            when(widgets.getTargetWidget(classic ? Minimap.CLASSIC_MINIMAP : Minimap.MODERN_MINIMAP)).thenReturn(map);
            OrbController manager = mock(OrbController.class);
            when(manager.isLoggedIn()).thenReturn(true); when(manager.isClassicResizable()).thenReturn(classic);
            InterfaceLayoutConfig config = mock(InterfaceLayoutConfig.class, CALLS_REAL_METHODS);
            when(config.layout()).thenReturn(OrbLayout.FREE_POSITION);
            ConfigManager configs = mock(ConfigManager.class);
            Map<String, Object> saved = new HashMap<>();
            when(configs.getConfiguration(anyString(), anyString(), any(Type.class))).thenAnswer(i -> saved.get(i.getArgument(1)));
            doAnswer(i -> { saved.put(i.getArgument(1), i.getArgument(2)); return null; })
                .when(configs).setConfiguration(anyString(), anyString(), any(Object.class));
            doAnswer(i -> { saved.remove(i.getArgument(1)); return null; }).when(configs).unsetConfiguration(anyString(), anyString());
            WidgetStateStore states = new WidgetStateStore();
            FreePositionController free = Guice.createInjector(new AbstractModule() {
                @Override protected void configure() {
                    bind(Client.class).toInstance(client); bind(InterfaceLayoutConfig.class).toInstance(config);
                    bind(OrbController.class).toProvider(() -> manager); bind(ConfigManager.class).toInstance(configs);
                    bind(WidgetManager.class).toProvider(() -> widgets); bind(EditManager.class).toProvider(() -> mock(EditManager.class));
                    bind(WidgetStateStore.class).toInstance(states);
                }
            }).getInstance(FreePositionController.class);
            assertTrue(free.bounds().containsKey(target));
            free.save(target, new Point(100, 200)); free.apply();
            assertEquals(new Point(100, 200), WidgetGeometry.location(compass));
            assertEquals(new Point(99, 199), WidgetGeometry.location(options));
            assertEquals(new Point(820, 30), WidgetGeometry.location(map));
            states.restore(); free.apply();
            assertEquals(new Point(100, 200), WidgetGeometry.location(compass));
            assertEquals(new Point(99, 199), WidgetGeometry.location(options));
            when(config.hideMinimap()).thenReturn(true); states.restore(); free.apply();
            assertTrue(map.isHidden()); assertFalse(compass.isHidden()); assertTrue(free.bounds().containsKey(target));
            when(config.hideCompass()).thenReturn(true); states.restore(); free.apply();
            assertFalse(free.bounds().containsKey(target));
            when(config.hideCompass()).thenReturn(false); states.restore(); free.apply();
            assertEquals(new Point(100, 200), WidgetGeometry.location(compass));
            free.resetAll(); assertTrue(saved.isEmpty());
        }
    }
    private static Widget restorableWidget(int x, int y, int width, int height)
    {
        Widget widget = WidgetGeometryTest.widget(x, y, width, height);
        int[] position = {x, y};
        when(widget.getOriginalX()).thenReturn(x); when(widget.getOriginalY()).thenReturn(y);
        when(widget.getRelativeX()).thenAnswer(i -> position[0]);
        when(widget.getRelativeY()).thenAnswer(i -> position[1]);
        doAnswer(i -> {
            int px = i.getArgument(0), py = i.getArgument(1);
            position[0] = px == -1 ? x : px; position[1] = py == -1 ? y : py;
            return null;
        }).when(widget).setForcedPosition(anyInt(), anyInt());
        return widget;
    }
    @Test public void repeatedMovesDoNotAlternateWithLastRenderedParentPosition()
    {
        Client client = mock(Client.class);
        when(client.getCanvasWidth()).thenReturn(1000); when(client.getCanvasHeight()).thenReturn(700);
        Widget root = WidgetGeometryTest.widget(0, 0, 1000, 700);
        Widget map = WidgetGeometryTest.widget(790, 0, 210, 207);
        Widget minimap = WidgetGeometryTest.widget(0, 0, 210, 180);
        Widget hp = WidgetGeometryTest.widget(5, 10, 56, 36);
        when(map.getParent()).thenReturn(root); when(minimap.getParent()).thenReturn(map); when(hp.getParent()).thenReturn(map);
        when(map.getStaticChildren()).thenReturn(new Widget[]{minimap, hp});
        WidgetManager widgets = mock(WidgetManager.class);
        when(widgets.getTargetWidget(Orbs.HP_ORB_CONTAINER)).thenReturn(hp);
        FreePositionController free = Guice.createInjector(new AbstractModule() {
            @Override protected void configure() {
                bind(Client.class).toInstance(client);
                bind(InterfaceLayoutConfig.class).toInstance(mock(InterfaceLayoutConfig.class, CALLS_REAL_METHODS));
                bind(OrbController.class).toProvider(() -> mock(OrbController.class));
                bind(ConfigManager.class).toProvider(() -> mock(ConfigManager.class));
                bind(WidgetManager.class).toProvider(() -> widgets);
                bind(EditManager.class).toProvider(() -> mock(EditManager.class));
                bind(WidgetStateStore.class).toInstance(new WidgetStateStore());
            }
        }).getInstance(FreePositionController.class);
        for (int frame = 0; frame < 6; frame++)
        {
            free.move(Orbs.HP_ORB_CONTAINER, new Point(100 + frame, 200));
            assertEquals(new Point(100 + frame, 200), WidgetGeometry.location(hp));
            assertEquals(new Point(790, 0), WidgetGeometry.location(minimap));
            // RuneLite's drawing cache still contains the old parent position.
            assertEquals(new Rectangle(790, 0, 210, 207), map.getBounds());
        }
    }
}
