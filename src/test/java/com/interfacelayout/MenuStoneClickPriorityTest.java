package com.interfacelayout;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.interfacelayout.layout.menu.*;
import com.interfacelayout.layout.orbs.OrbController;
import com.interfacelayout.util.*;
import java.awt.Rectangle;
import java.util.concurrent.atomic.AtomicReference;
import net.runelite.api.*;
import net.runelite.api.events.PostMenuSort;
import net.runelite.api.widgets.Widget;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.menuentryswapper.MenuEntrySwapperConfig;
import net.runelite.client.plugins.menuentryswapper.MenuEntrySwapperPlugin;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class MenuStoneClickPriorityTest
{
    @Mock private Client client;
    @Mock private ConfigManager configs;
    @Mock private MenuEntrySwapperConfig config;
    @Mock private ItemManager items;
    @InjectMocks private MenuEntrySwapperPlugin swapper;

    @Test public void actualSwapperCannotReplaceNativeStoneWithQuickExit() throws Exception
    {
        try (AutoCloseable mocks = MockitoAnnotations.openMocks(this))
        {
            Fixture f = new Fixture(client, 2);
            ObjectComposition exit = mock(ObjectComposition.class);
            when(client.getObjectDefinition(100)).thenReturn(exit);
            when(configs.getConfiguration(MenuEntrySwapperConfig.GROUP, "object_100")).thenReturn("0");
            f.entries.set(new MenuEntry[]{f.cancel, f.exit, f.action});
            EventBus bus = new EventBus();
            bus.register(swapper); bus.register(f.guard); bus.register(f.guard.nativeListener());
            // Registration order must not determine which action wins.
            bus.post(new PostMenuSort());
            assertArrayEquals(new MenuEntry[]{f.cancel, f.exit, f.action}, f.entries.get());
            verify(f.menu, times(2)).setMenuEntries(any(MenuEntry[].class)); // swapper, then guard
            bus.unregister(f.guard);
            bus.unregister(f.guard.nativeListener());
            f.entries.set(new MenuEntry[]{f.cancel, f.exit, f.action});
            bus.post(new PostMenuSort());
            assertSame(f.exit, f.entries.get()[2]); // proves the real conflicting swap occurred
        }
    }

    @Test public void everyObjectOptionPreservesNativeActionAcrossModesAndStretching()
    {
        for (int mode = 0; mode < 3; mode++) for (boolean stretched : new boolean[]{false, true})
        {
            Fixture f = new Fixture(mock(Client.class), mode);
            when(f.client.isStretchedEnabled()).thenReturn(stretched);
            for (MenuAction type : new MenuAction[]{MenuAction.GAME_OBJECT_FIRST_OPTION, MenuAction.GAME_OBJECT_SECOND_OPTION,
                MenuAction.GAME_OBJECT_THIRD_OPTION, MenuAction.GAME_OBJECT_FOURTH_OPTION, MenuAction.GAME_OBJECT_FIFTH_OPTION})
            {
                when(f.exit.getType()).thenReturn(type);
                f.captureAndSwap(); f.guard.afterSwaps(new PostMenuSort());
                assertArrayEquals(new MenuEntry[]{f.cancel, f.exit, f.action}, f.entries.get());
                verify(f.action, never()).setType(any());
                verify(f.action, never()).setIdentifier(anyInt());
                verify(f.action, never()).setParam0(anyInt());
                verify(f.action, never()).setParam1(anyInt());
            }
        }
    }

    @Test public void bankOrOtherNativeWindowAboveStoneKeepsPriority()
    {
        Fixture f = new Fixture(mock(Client.class), 2);
        MenuEntry bank = entry(MenuAction.CC_OP, "Withdraw-1");
        when(bank.getParam1()).thenReturn(net.runelite.api.gameval.InterfaceID.BANKMAIN << 16);
        f.entries.set(new MenuEntry[]{f.cancel, f.action, bank});
        f.guard.beforeSwaps(new PostMenuSort());
        f.entries.set(new MenuEntry[]{f.cancel, f.action, bank, f.exit});
        f.guard.afterSwaps(new PostMenuSort());
        verify(f.menu, never()).setMenuEntries(any(MenuEntry[].class));
    }

    @Test public void gapsAndOutsideBoundsNeverAcquireStoneAction()
    {
        Fixture f = new Fixture(mock(Client.class), 2);
        Rectangle bounds = f.stones.stones().get(0);
        when(f.client.getMouseCanvasPosition()).thenReturn(new Point(bounds.x + bounds.width + 1, bounds.y));
        f.captureAndSwap(); f.guard.afterSwaps(new PostMenuSort());
        verify(f.menu, never()).setMenuEntries(any(MenuEntry[].class));
    }

    @Test public void hiddenUnavailableOrRemovedActionNeverGetsRecreated()
    {
        for (int change = 0; change < 5; change++)
        {
            Fixture f = new Fixture(mock(Client.class), 2); f.captureAndSwap();
            if (change == 0) when(f.icon.isHidden()).thenReturn(true);
            if (change == 1) when(f.config.hideCombatStone()).thenReturn(true);
            if (change == 2) f.entries.set(new MenuEntry[]{f.cancel, f.exit});
            if (change == 3) f.stones.toggleShown();
            if (change == 4) f.stones.resetSession();
            f.guard.afterSwaps(new PostMenuSort());
            verify(f.menu, never()).setMenuEntries(any(MenuEntry[].class));
        }
    }

    @Test public void staleOpenMovedLogoutChangedAndClearedFramesFailOpen()
    {
        for (int change = 0; change < 7; change++)
        {
            Fixture f = new Fixture(mock(Client.class), 2); f.captureAndSwap();
            if (change == 0) when(f.client.isMenuOpen()).thenReturn(true);
            if (change == 1) when(f.client.getGameState()).thenReturn(GameState.LOGIN_SCREEN);
            if (change == 2) when(f.client.getMouseCanvasPosition()).thenReturn(new Point(700, 400));
            if (change == 3) when(f.client.getMenu()).thenReturn(mock(Menu.class));
            if (change == 4) when(f.action.getIdentifier()).thenReturn(99);
            if (change == 5) f.guard.clear();
            if (change == 6) when(f.exit.getType()).thenReturn(MenuAction.RUNELITE_OVERLAY_CONFIG);
            f.guard.afterSwaps(new PostMenuSort());
            verify(f.menu, never()).setMenuEntries(any(MenuEntry[].class));
            when(f.client.isMenuOpen()).thenReturn(false);
            // A second callback cannot reuse the captured action.
            f.guard.afterSwaps(new PostMenuSort());
            verify(f.menu, never()).setMenuEntries(any(MenuEntry[].class));
        }
    }

    private static MenuEntry entry(MenuAction type, String option)
    {
        MenuEntry entry = mock(MenuEntry.class);
        when(entry.getType()).thenReturn(type); when(entry.getOption()).thenReturn(option);
        when(entry.getTarget()).thenReturn(""); return entry;
    }

    @Test public void nativeGapMenuBlocksWalkAndQuickExitOnlyWhenDisabled() {
        Fixture f = new Fixture(mock(Client.class), 2);
        Rectangle first = f.stones.stones().get(0);
        when(f.client.getMouseCanvasPosition()).thenReturn(new Point(first.x + first.width + 2, first.y + 10));
        MenuEntry walk = entry(MenuAction.WALK, "Walk here");
        MenuEntry[] original = {f.cancel, walk, f.exit};
        for (boolean allow : new boolean[]{true, false, true}) {
            when(f.config.allowClicksThroughGaps()).thenReturn(allow);
            f.entries.set(original.clone());
            f.guard.onPostMenuSort(new PostMenuSort());
            assertArrayEquals(allow ? original : new MenuEntry[]{f.cancel}, f.entries.get());
        }
    }

    @Test public void nativeGapProtectionDoesNotRemoveWindowOrOpenMenuActions() {
        Fixture f = new Fixture(mock(Client.class), 2);
        Rectangle first = f.stones.stones().get(0);
        when(f.config.allowClicksThroughGaps()).thenReturn(false);
        when(f.client.getMouseCanvasPosition()).thenReturn(new Point(first.x + first.width + 2, first.y + 10));
        for (MenuAction type : new MenuAction[]{MenuAction.CC_OP, MenuAction.RUNELITE, MenuAction.PLAYER_FIRST_OPTION}) {
            MenuEntry ui = entry(type, "Action");
            MenuEntry[] original = {f.cancel, f.exit, ui};
            f.entries.set(original); f.guard.onPostMenuSort(new PostMenuSort());
            assertArrayEquals(original, f.entries.get());
        }
        when(f.client.isMenuOpen()).thenReturn(true);
        f.entries.set(new MenuEntry[]{f.cancel, f.exit});
        f.guard.onPostMenuSort(new PostMenuSort());
        assertEquals(2, f.entries.get().length);
        verify(f.menu, never()).setMenuEntries(any(MenuEntry[].class));
    }

    private static final class Fixture
    {
        final Client client;
        final InterfaceLayoutConfig config = mock(InterfaceLayoutConfig.class, CALLS_REAL_METHODS);
        final MenuStoneController stones;
        final MenuStoneClickPriority guard;
        final Widget stone = WidgetGeometryTest.widget(100, 100, 33, 36);
        final Widget icon = WidgetGeometryTest.widget(105, 105, 23, 26);
        final MenuEntry action = entry(MenuAction.CC_OP, "Combat options");
        final MenuEntry exit = entry(MenuAction.GAME_OBJECT_FIRST_OPTION, "Quick-exit");
        final MenuEntry cancel = entry(MenuAction.CANCEL, "Cancel");
        final Menu menu = mock(Menu.class);
        final AtomicReference<MenuEntry[]> entries = new AtomicReference<>();
        Fixture(Client client, int display)
        {
            this.client = client;
            OrbController orbs = mock(OrbController.class);
            when(orbs.isLoggedIn()).thenReturn(true);
            when(orbs.isFixedMode()).thenReturn(display == 0);
            when(orbs.isClassicResizable()).thenReturn(display == 1);
            when(config.menuLayout()).thenReturn(MenuStoneController.Layout.FREE_POSITION);
            when(config.menuDirection()).thenReturn(MenuStoneController.Direction.HORIZONTAL);
            when(config.menuSpacing()).thenReturn(8);
            when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
            when(client.getCanvasWidth()).thenReturn(1000); when(client.getCanvasHeight()).thenReturn(700);
            int[][] registry = display == 0 ? MenuStoneRegistry.FIXED : display == 1 ? MenuStoneRegistry.CLASSIC : MenuStoneRegistry.MODERN;
            when(stone.getId()).thenReturn(registry[0][0]); when(icon.getId()).thenReturn(registry[0][1]);
            when(client.getWidget(registry[0][0])).thenReturn(stone);
            when(client.getWidget(registry[0][1])).thenReturn(icon);
            Widget second = WidgetGeometryTest.widget(133,100,33,36);
            Widget secondIcon = WidgetGeometryTest.widget(138,105,23,26);
            when(client.getWidget(registry[1][0])).thenReturn(second);
            when(client.getWidget(registry[1][1])).thenReturn(secondIcon);
            when(action.getWidget()).thenReturn(stone); when(action.getParam1()).thenReturn(registry[0][0]);
            when(action.getIdentifier()).thenReturn(1); when(exit.getIdentifier()).thenReturn(100);
            com.google.inject.Injector injector = Guice.createInjector(new AbstractModule() {
                @Override protected void configure() {
                    bind(Client.class).toInstance(client); bind(InterfaceLayoutConfig.class).toInstance(config);
                    bind(OrbController.class).toProvider(() -> orbs);
                    bind(ConfigManager.class).toInstance(mock(ConfigManager.class));
                    bind(WidgetStateStore.class).toInstance(new WidgetStateStore());
                    bind(WidgetBoundsExpander.class).toProvider(() -> mock(WidgetBoundsExpander.class));
                    bind(MenuStoneBackgrounds.class).toProvider(() -> mock(MenuStoneBackgrounds.class));
                }
            });
            stones = injector.getInstance(MenuStoneController.class); stones.apply();
            guard = injector.getInstance(MenuStoneClickPriority.class);
            Rectangle bounds = stones.stones().get(0);
            when(client.getMouseCanvasPosition()).thenReturn(new Point(bounds.x + 10, bounds.y + 10));
            when(client.getMenu()).thenReturn(menu);
            entries.set(new MenuEntry[]{cancel, exit, action});
            when(menu.getMenuEntries()).thenAnswer(i -> entries.get().clone());
            doAnswer(i -> { entries.set(i.getArgument(0)); return null; }).when(menu).setMenuEntries(any(MenuEntry[].class));
        }
        void captureAndSwap()
        {
            entries.set(new MenuEntry[]{cancel, exit, action});
            guard.beforeSwaps(new PostMenuSort());
            entries.set(new MenuEntry[]{cancel, action, exit});
        }
    }
}
