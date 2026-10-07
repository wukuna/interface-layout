package com.interfacelayout;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.interfacelayout.layout.menu.MenuStoneController;
import com.interfacelayout.layout.menu.MenuStoneRegistry;
import com.interfacelayout.layout.orbs.OrbController;
import com.interfacelayout.util.WidgetBoundsExpander;
import com.interfacelayout.util.WidgetStateStore;
import com.interfacelayout.layout.GameframeCoordinator;
import com.interfacelayout.layout.orbs.FreePositionController;
import com.interfacelayout.layout.edit.InterfaceEditor;
import java.awt.Rectangle;
import java.awt.Point;
import net.runelite.api.Client;
import net.runelite.api.widgets.Widget;
import net.runelite.client.config.ConfigManager;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class MenuStoneControllerTest
{
    @Test public void nativeRowsCompactWithoutExpandingParentsOrMovingModernLogout()
    {
        int[][][] registries = {MenuStoneRegistry.FIXED, MenuStoneRegistry.CLASSIC, MenuStoneRegistry.MODERN};
        for (int display = 0; display < 3; display++)
        {
            Client client = mock(Client.class);
            when(client.getCanvasWidth()).thenReturn(1200); when(client.getCanvasHeight()).thenReturn(800);
            OrbController mode = mock(OrbController.class);
            when(mode.isLoggedIn()).thenReturn(true); when(mode.isFixedMode()).thenReturn(display == 0);
            when(mode.isClassicResizable()).thenReturn(display == 1);
            ConfigManager configs = mock(ConfigManager.class);
            InterfaceLayoutConfig config = mock(InterfaceLayoutConfig.class, CALLS_REAL_METHODS);
            when(config.menuLayout()).thenReturn(MenuStoneController.Layout.VANILLA_HORIZONTAL);
            when(config.hideCombatStone()).thenReturn(true); when(config.hideClanStone()).thenReturn(true);
            WidgetStateStore states = new WidgetStateStore();
            Widget[] parents = {WidgetGeometryTest.widget(800, 500, 231, 36), WidgetGeometryTest.widget(800, 550, 231, 36)};
            Widget[] stones = new Widget[14], icons = new Widget[14];
            for (int i = 0; i < 14; i++)
            {
                int x = (i % 7) * 33;
                if (display == 2 && i >= 7) x = new int[]{99,132,165,0,66,33,0}[i - 7];
                stones[i] = WidgetGeometryTest.widget(x, 0, 33, 36);
                icons[i] = WidgetGeometryTest.widget(x + 4, 2, 25, 32);
                final int nativeX = x;
                final Widget nativeStone = stones[i], nativeIcon = icons[i];
                // The game restores native relative coordinates when forced position is cleared.
                doAnswer(call -> {
                    int px = call.getArgument(0), py = call.getArgument(1);
                    when(nativeStone.getRelativeX()).thenReturn(px == -1 ? nativeX : px);
                    when(nativeStone.getRelativeY()).thenReturn(py == -1 ? 0 : py);
                    return null;
                }).when(nativeStone).setForcedPosition(anyInt(), anyInt());
                doAnswer(call -> {
                    int px = call.getArgument(0), py = call.getArgument(1);
                    when(nativeIcon.getRelativeX()).thenReturn(px == -1 ? nativeX + 4 : px);
                    when(nativeIcon.getRelativeY()).thenReturn(py == -1 ? 2 : py);
                    return null;
                }).when(nativeIcon).setForcedPosition(anyInt(), anyInt());
                when(stones[i].getParent()).thenReturn(parents[i / 7]);
                when(icons[i].getParent()).thenReturn(parents[i / 7]);
                when(client.getWidget(registries[display][i][0])).thenReturn(stones[i]);
                when(client.getWidget(registries[display][i][1])).thenReturn(icons[i]);
            }
            MenuStoneController menu = Guice.createInjector(new AbstractModule() {
                @Override protected void configure() {
                    bind(Client.class).toInstance(client); bind(InterfaceLayoutConfig.class).toInstance(config);
                    bind(OrbController.class).toProvider(() -> mode); bind(ConfigManager.class).toInstance(configs);
                    bind(WidgetStateStore.class).toInstance(states);
                }
            }).getInstance(MenuStoneController.class);
            menu.apply();
            assertEquals(new Rectangle(800,500,198,36), menu.groups().get(0));
            assertEquals(new Rectangle(800,550,display == 2 ? 165 : 198,36), menu.groups().get(1));
            assertFalse(menu.stones().containsKey(0)); assertFalse(menu.stones().containsKey(7));
            for (Widget parent : parents)
            {
                verify(parent, never()).setForcedPosition(anyInt(),anyInt());
                verify(parent, never()).setHidden(anyBoolean());
            }
            verify(stones[10], never()).setHidden(true); verify(icons[10], never()).setHidden(true);
            if (display == 2) verify(stones[10], never()).setForcedPosition(anyInt(),anyInt());
            else assertTrue(menu.stones().containsKey(10)); // Fixed/Classic Logout remains in the row.
            states.restore();
            when(config.hideCombatStone()).thenReturn(false); when(config.hideClanStone()).thenReturn(false);
            menu.apply();
            assertEquals(new Point(800, 500), menu.groups().get(0).getLocation());
            assertEquals(new Point(800, 550), menu.groups().get(1).getLocation());
        }
    }

    @Test public void realConfigProxyAppliesEachIndividualHideInAllDisplayModes()
    {
        int[][][] registries = {MenuStoneRegistry.FIXED, MenuStoneRegistry.CLASSIC, MenuStoneRegistry.MODERN};
        for (int displayMode = 0; displayMode < registries.length; displayMode++)
        {
            for (int index = 0; index < 14; index++)
            {
                Client client = mock(Client.class);
                Widget stone = mock(Widget.class), icon = mock(Widget.class);
                when(client.getWidget(registries[displayMode][index][0])).thenReturn(stone);
                when(client.getWidget(registries[displayMode][index][1])).thenReturn(icon);
                ConfigManager configs = mock(ConfigManager.class);
                when(configs.getConfiguration("interfacelayout", "hideStone" + index)).thenReturn("true");
                InterfaceLayoutConfig config = RealConfigProxy.create(InterfaceLayoutConfig.class, configs);
                OrbController mode = mock(OrbController.class);
                when(mode.isLoggedIn()).thenReturn(true);
                when(mode.isFixedMode()).thenReturn(displayMode == 0);
                when(mode.isClassicResizable()).thenReturn(displayMode == 1);
                controller(client, config, new WidgetStateStore(), mode, configs).apply();
                verify(stone).setHidden(true); verify(icon).setHidden(true);
            }
        }
    }

    @Test public void realConfigProxyDoesNotAbortOrbRefreshAfterMenuPlacement()
    {
        Client client = mock(Client.class);
        when(client.getCanvasWidth()).thenReturn(1000); when(client.getCanvasHeight()).thenReturn(700);
        Widget stone = mock(Widget.class);
        when(client.getWidget(MenuStoneRegistry.MODERN[0][0])).thenReturn(stone);
        when(stone.getWidth()).thenReturn(33); when(stone.getHeight()).thenReturn(33);
        when(stone.getBounds()).thenReturn(new Rectangle(800, 600, 33, 33));
        ConfigManager configs = mock(ConfigManager.class);
        when(configs.getConfiguration("interfacelayout", "menuLayout")).thenReturn("VERTICAL_LEFT");
        InterfaceLayoutConfig config = RealConfigProxy.create(InterfaceLayoutConfig.class, configs);
        OrbController mode = mock(OrbController.class); when(mode.isLoggedIn()).thenReturn(true);
        WidgetStateStore states = new WidgetStateStore();
        MenuStoneController menu = controller(client, config, states, mode, configs);
        FreePositionController free = mock(FreePositionController.class);
        GameframeCoordinator coordinator = Guice.createInjector(new AbstractModule() {
            @Override protected void configure()
            {
                bind(Client.class).toInstance(client);
                bind(MenuStoneController.class).toProvider(() -> menu);
                bind(com.interfacelayout.layout.menu.MenuOverlayBridge.class).toProvider(() -> mock(com.interfacelayout.layout.menu.MenuOverlayBridge.class));
                bind(WidgetStateStore.class).toInstance(states);
                bind(FreePositionController.class).toProvider(() -> free);
                bind(WidgetBoundsExpander.class).toProvider(() -> mock(WidgetBoundsExpander.class));
                bind(InterfaceEditor.class).toProvider(() -> mock(InterfaceEditor.class));
                bind(OrbController.class).toProvider(() -> mode);
                bind(com.interfacelayout.layout.orbs.widget.layout.edit.EditManager.class)
                    .toProvider(() -> mock(com.interfacelayout.layout.orbs.widget.layout.edit.EditManager.class));
                bind(net.runelite.client.callback.ClientThread.class).toInstance(mock(net.runelite.client.callback.ClientThread.class));
            }
        }).getInstance(GameframeCoordinator.class);
        coordinator.start();
        verify(stone).setForcedPosition(0, 0);
        verify(free).apply();
    }
    private MenuStoneController controller(Client client, InterfaceLayoutConfig config, WidgetStateStore states)
    {
        OrbController mode = mock(OrbController.class);
        when(mode.isLoggedIn()).thenReturn(true); when(mode.isFixedMode()).thenReturn(true);
        return controller(client, config, states, mode, mock(ConfigManager.class));
    }
    private MenuStoneController controller(Client client, InterfaceLayoutConfig config, WidgetStateStore states,
        OrbController mode, ConfigManager configs)
    {
        return Guice.createInjector(new AbstractModule() {
            @Override protected void configure()
            {
                bind(Client.class).toInstance(client);
                bind(InterfaceLayoutConfig.class).toInstance(config);
                bind(OrbController.class).toProvider(() -> mode);
                bind(ConfigManager.class).toInstance(configs);
                bind(WidgetStateStore.class).toInstance(states);
                bind(WidgetBoundsExpander.class).toInstance(mock(WidgetBoundsExpander.class));
            }
        }).getInstance(MenuStoneController.class);
    }
    @Test public void individualHideIncludesBothNativeIconAndClickTarget()
    {
        Client client = mock(Client.class);
        Widget stone = mock(Widget.class), icon = mock(Widget.class);
        when(client.getWidget(MenuStoneRegistry.FIXED[0][0])).thenReturn(stone);
        when(client.getWidget(MenuStoneRegistry.FIXED[0][1])).thenReturn(icon);
        InterfaceLayoutConfig config = mock(InterfaceLayoutConfig.class, CALLS_REAL_METHODS);
        when(config.hiddenStones()).thenReturn(1);
        WidgetStateStore states = new WidgetStateStore();
        controller(client, config, states).apply();
        verify(stone).setHidden(true); verify(icon).setHidden(true);
        clearInvocations(stone, icon); states.restore();
        verify(stone).setHidden(false); verify(icon).setHidden(false);
    }
    @Test public void verticalPlacementPreservesNativeIconOffset()
    {
        Client client = mock(Client.class);
        when(client.getCanvasWidth()).thenReturn(765); when(client.getCanvasHeight()).thenReturn(503);
        Widget stone = mock(Widget.class), icon = mock(Widget.class), parent = mock(Widget.class);
        when(client.getWidget(MenuStoneRegistry.FIXED[0][0])).thenReturn(stone);
        when(client.getWidget(MenuStoneRegistry.FIXED[0][1])).thenReturn(icon);
        when(stone.getWidth()).thenReturn(33); when(stone.getHeight()).thenReturn(33);
        when(stone.getBounds()).thenReturn(new Rectangle(568, 169, 33, 33));
        when(icon.getBounds()).thenReturn(new Rectangle(575, 174, 20, 20));
        when(stone.getRelativeX()).thenReturn(568); when(stone.getRelativeY()).thenReturn(169);
        when(icon.getRelativeX()).thenReturn(575); when(icon.getRelativeY()).thenReturn(174);
        when(stone.getParent()).thenReturn(parent); when(icon.getParent()).thenReturn(parent);
        when(parent.getBounds()).thenReturn(new Rectangle(0, 0, 765, 503));
        InterfaceLayoutConfig config = mock(InterfaceLayoutConfig.class, CALLS_REAL_METHODS);
        when(config.menuLayout()).thenReturn(MenuStoneController.Layout.VERTICAL_RIGHT);
        controller(client, config, new WidgetStateStore()).apply();
        verify(stone).setForcedPosition(732, 0); verify(icon).setForcedPosition(739, 5);
    }
    @Test public void temporaryRestoreDoesNotUnhideNaturallyHiddenWidgets()
    {
        Client client = mock(Client.class);
        InterfaceLayoutConfig config = mock(InterfaceLayoutConfig.class, CALLS_REAL_METHODS);
        when(config.hideTopMenu()).thenReturn(true);
        MenuStoneController controller = controller(client, config, new WidgetStateStore());
        controller.toggleShown(); controller.apply();
        verify(client, never()).getWidget(anyInt());
    }
    @Test public void missingInterfacesAreSafe()
    {
        controller(mock(Client.class), mock(InterfaceLayoutConfig.class, CALLS_REAL_METHODS), new WidgetStateStore()).apply();
    }
    @Test public void individualCheckboxHidesTheNativeCombatStone()
    {
        Client client = mock(Client.class);
        Widget stone = mock(Widget.class), icon = mock(Widget.class);
        when(client.getWidget(MenuStoneRegistry.FIXED[0][0])).thenReturn(stone);
        when(client.getWidget(MenuStoneRegistry.FIXED[0][1])).thenReturn(icon);
        InterfaceLayoutConfig config = mock(InterfaceLayoutConfig.class, CALLS_REAL_METHODS);
        when(config.hideCombatStone()).thenReturn(true);
        controller(client, config, new WidgetStateStore()).apply();
        verify(stone).setHidden(true); verify(icon).setHidden(true);
    }

    @Test public void modernResizableMovesBothRowsWithTheirIconsAndRestores()
    {
        assertResizablePlacement(false, MenuStoneController.Layout.VERTICAL_LEFT, null, 0, 0);
        assertResizablePlacement(false, MenuStoneController.Layout.VERTICAL_RIGHT, null, 967, 0);
    }
    @Test public void classicResizableMovesBothRowsWithTheirIconsAndRestores()
    {
        assertResizablePlacement(true, MenuStoneController.Layout.VERTICAL_LEFT, null, 0, 0);
        assertResizablePlacement(true, MenuStoneController.Layout.VERTICAL_RIGHT, null, 967, 0);
    }
    @Test public void resizedCanvasClampsSavedOriginsInEachResizableMode()
    {
        assertResizablePlacement(false, MenuStoneController.Layout.VERTICAL_LEFT, new Point(1400, 900), 967, 630);
        assertResizablePlacement(true, MenuStoneController.Layout.VERTICAL_LEFT, new Point(1400, 900), 967, 630);
    }
    private void assertResizablePlacement(boolean classic, MenuStoneController.Layout layout, Point saved, int x, int y)
    {
        Client client = mock(Client.class);
        when(client.getCanvasWidth()).thenReturn(1000); when(client.getCanvasHeight()).thenReturn(700);
        OrbController mode = mock(OrbController.class);
        when(mode.isLoggedIn()).thenReturn(true); when(mode.isClassicResizable()).thenReturn(classic);
        ConfigManager configs = mock(ConfigManager.class);
        when(configs.getConfiguration("interfacelayout", "menuPosition_" + (classic ? "classic" : "modern"), Point.class)).thenReturn(saved);
        InterfaceLayoutConfig config = mock(InterfaceLayoutConfig.class, CALLS_REAL_METHODS);
        when(config.menuLayout()).thenReturn(layout); when(config.menuSpacing()).thenReturn(4);
        int[][] registry = classic ? MenuStoneRegistry.CLASSIC : MenuStoneRegistry.MODERN;
        Widget[] stones = new Widget[2], icons = new Widget[2];
        for (int row = 0; row < 2; row++)
        {
            int index = row * 7;
            Widget parent = mock(Widget.class);
            when(parent.getBounds()).thenReturn(new Rectangle(800, 500 + row * 40, 200, 40));
            when(parent.getRelativeX()).thenReturn(800); when(parent.getRelativeY()).thenReturn(500 + row * 40);
            stones[row] = mock(Widget.class); icons[row] = mock(Widget.class);
            Widget stone = stones[row], icon = icons[row];
            when(client.getWidget(registry[index][0])).thenReturn(stone);
            when(client.getWidget(registry[index][1])).thenReturn(icon);
            when(stone.getParent()).thenReturn(parent); when(icon.getParent()).thenReturn(parent);
            when(stone.getWidth()).thenReturn(33); when(stone.getHeight()).thenReturn(33);
            Rectangle stoneBounds = new Rectangle(800, 500 + row * 40, 33, 33);
            when(stone.getBounds()).thenAnswer(invocation -> new Rectangle(stoneBounds));
            when(icon.getBounds()).thenReturn(new Rectangle(807, 505 + row * 40, 20, 20));
            when(icon.getRelativeX()).thenReturn(7); when(icon.getRelativeY()).thenReturn(5);
            when(stone.getRelativeX()).thenAnswer(invocation -> stoneBounds.x - 800);
            when(stone.getRelativeY()).thenAnswer(invocation -> stoneBounds.y - parent.getRelativeY());
            doAnswer(invocation -> {
                int dx = invocation.getArgument(0), dy = invocation.getArgument(1);
                if (dx != -1 || dy != -1) stoneBounds.setLocation(800 + dx, parent.getBounds().y + dy);
                return null;
            }).when(stone).setForcedPosition(anyInt(), anyInt());
        }
        WidgetStateStore states = new WidgetStateStore();
        MenuStoneController controller = controller(client, config, states, mode, configs);
        controller.apply();
        verify(stones[0]).setForcedPosition(x - 800, y - 500);
        verify(icons[0]).setForcedPosition(x + 7 - 800, y + 5 - 500);
        verify(stones[1]).setForcedPosition(x - 800, y + 37 - 540);
        verify(icons[1]).setForcedPosition(x + 7 - 800, y + 42 - 540);
        assertEquals(new Rectangle(x, y, 33, 70), controller.bounds());
        verify(configs).getConfiguration("interfacelayout", "menuPosition_" + (classic ? "classic" : "modern"), Point.class);
        states.restore();
        for (Widget stone : stones) verify(stone).setForcedPosition(-1, -1);
        for (Widget icon : icons) verify(icon).setForcedPosition(-1, -1);
    }

    @Test public void undersizedResizableCanvasKeepsNativeClickTargets()
    {
        Client client = mock(Client.class);
        when(client.getCanvasWidth()).thenReturn(1000); when(client.getCanvasHeight()).thenReturn(20);
        Widget stone = mock(Widget.class);
        when(client.getWidget(MenuStoneRegistry.MODERN[0][0])).thenReturn(stone);
        when(stone.getWidth()).thenReturn(33); when(stone.getHeight()).thenReturn(33);
        InterfaceLayoutConfig config = mock(InterfaceLayoutConfig.class, CALLS_REAL_METHODS);
        when(config.menuLayout()).thenReturn(MenuStoneController.Layout.VERTICAL_LEFT);
        OrbController mode = mock(OrbController.class); when(mode.isLoggedIn()).thenReturn(true);
        MenuStoneController controller = controller(client, config, new WidgetStateStore(), mode, mock(ConfigManager.class));
        controller.apply();
        verify(stone, never()).setForcedPosition(anyInt(), anyInt());
        assertTrue(controller.bounds().isEmpty());
    }
}
