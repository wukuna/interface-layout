package com.interfacelayout;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.interfacelayout.layout.edit.InterfaceEditor;
import com.interfacelayout.layout.menu.MenuOverlayBridge;
import com.interfacelayout.layout.menu.MenuStoneBackgrounds;
import com.interfacelayout.layout.menu.MenuStoneController;
import com.interfacelayout.layout.menu.MenuStoneRegistry;
import com.interfacelayout.layout.orbs.FreePositionController;
import com.interfacelayout.layout.orbs.OrbController;
import com.interfacelayout.util.WidgetBoundsExpander;
import com.interfacelayout.util.WidgetStateStore;
import java.awt.Canvas;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import net.runelite.api.Client;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.RuneLiteConfig;
import net.runelite.client.input.MouseManager;
import net.runelite.client.plugins.stretchedmode.TranslateMouseListener;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/** Regression coverage for reporter feedback: native coordinates and atomic grouping. */
public class MenuFeedbackDiagnosticsTest {
    @Test public void nativeCoordinatePipelineWorksAcrossDisplayModesAndScaleChanges() {
        int probes = 0;
        for (int display = 0; display < 3; display++) {
            Fixture f = new Fixture(display);
            MouseManager mouse = Guice.createInjector(new AbstractModule() {
                @Override protected void configure() { bind(RuneLiteConfig.class).toInstance(mock(RuneLiteConfig.class)); }
            }).getInstance(MouseManager.class);
            TranslateMouseListener translate = new TranslateMouseListener(f.client);
            mouse.registerMouseListener(0, f.editor);
            mouse.registerMouseListener(0, translate);
            // Native dimensions remain authoritative; integer/fractional and unequal X/Y scales.
            for (Dimension screen : new Dimension[]{new Dimension(1500, 1050), new Dimension(2000, 1400),
                    new Dimension(3000, 2100), new Dimension(1700, 1610)}) {
                when(f.client.getStretchedDimensions()).thenReturn(screen);
                int x = (int)Math.ceil(110 * screen.width / 1000.0);
                int y = (int)Math.ceil(110 * screen.height / 700.0);
                MouseEvent result = mouse.processMousePressed(f.event(MouseEvent.MOUSE_PRESSED, x, y, MouseEvent.BUTTON1));
                assertEquals(new Point(110, 110), result.getPoint());
                assertTrue("Translated press reaches stone after scale/resize change", result.isConsumed());
                f.editor.cancel(); f.refresh(); probes++;
            }
        }
        assertEquals(12, probes);
    }
    @Test public void stretchedHitTestingWorksRegardlessOfPluginStartOrder() {
        Fixture f = new Fixture();
        MouseManager mouse = Guice.createInjector(new AbstractModule() {
            @Override protected void configure() { bind(RuneLiteConfig.class).toInstance(mock(RuneLiteConfig.class)); }
        }).getInstance(MouseManager.class);
        TranslateMouseListener translate = new TranslateMouseListener(f.client);
        mouse.registerMouseListener(0, translate);
        mouse.registerMouseListener(f.editor);
        MouseEvent raw = f.event(MouseEvent.MOUSE_PRESSED, 220, 220, MouseEvent.BUTTON1);
        MouseEvent result = mouse.processMousePressed(raw);
        assertEquals(new Point(110, 110), result.getPoint());
        assertTrue(result.isConsumed());
        f.editor.cancel(); f.refresh();
        mouse.unregisterMouseListener(translate);
        mouse.registerMouseListener(0, translate); // Stretched Mode disable/re-enable workaround.
        assertTrue(mouse.processMousePressed(f.event(MouseEvent.MOUSE_PRESSED, 220, 220, MouseEvent.BUTTON1)).isConsumed());
    }

    @Test public void onePixelShiftMovementNeverDetachesLastStone() {
        Fixture f = new Fixture();
        Rectangle stone = f.menu.stones().get(5);
        f.editor.mousePressed(f.event(MouseEvent.MOUSE_PRESSED, stone.x + 10, stone.y + 10, MouseEvent.BUTTON1));
        f.editor.mouseDragged(f.event(MouseEvent.MOUSE_DRAGGED, stone.x + 11, stone.y + 10, MouseEvent.NOBUTTON));
        f.editor.mouseReleased(f.event(MouseEvent.MOUSE_RELEASED, stone.x + 11, stone.y + 10, MouseEvent.BUTTON1));
        assertEquals(0, f.menu.groupForStone(5));
    }

    @Test public void cancellationKeepsAllSavedMembershipAndPositions() {
        Fixture interrupted = new Fixture();
        Map<String, Object> before = new HashMap<>(interrupted.values);
        Rectangle last = interrupted.menu.stones().get(5);
        interrupted.editor.mousePressed(interrupted.event(MouseEvent.MOUSE_PRESSED, last.x + 10, last.y + 10, MouseEvent.BUTTON1));
        interrupted.editor.mouseDragged(interrupted.event(MouseEvent.MOUSE_DRAGGED, 700, 400, MouseEvent.NOBUTTON));
        assertEquals(before, interrupted.values);
        interrupted.editor.cancel(); // Same cancellation called on native subchange/focus/config transitions.
        interrupted.refresh();
        assertEquals(before, interrupted.values);
        assertEquals(0, interrupted.menu.groupForStone(5));
        assertEquals(1, interrupted.menu.groups().size());
        Fixture completed = new Fixture();
        Rectangle original = completed.menu.stones().get(5);
        dragOnePixel(completed);
        completed.editor.mouseReleased(completed.event(MouseEvent.MOUSE_RELEASED,
                original.x + 9, original.y + 10, MouseEvent.BUTTON1));
        assertEquals(0, completed.menu.groupForStone(5));
        assertEquals(1, completed.menu.groups().size());
    }

    @Test public void movedStonesKeepNativeInputFlagsAndSpacedGapsHaveNoStone() {
        Fixture f = new Fixture();
        for (Widget stone : f.stones) verify(stone, never()).setNoClickThrough(true);
        Rectangle first = f.menu.stones().get(0), second = f.menu.stones().get(1);
        Point gap = new Point(first.x + first.width + 1, first.y + first.height / 2);
        assertTrue(second.x > gap.x);
        assertFalse(f.menu.stones().values().stream().anyMatch(bounds -> bounds.contains(gap)));
    }

    @Test public void deliberateDetachCommitsOnlyOnRelease() {
        Fixture f = new Fixture(); Rectangle last = f.menu.stones().get(5);
        f.editor.mousePressed(f.event(MouseEvent.MOUSE_PRESSED, last.x + 10, last.y + 10, MouseEvent.BUTTON1));
        f.editor.mouseDragged(f.event(MouseEvent.MOUSE_DRAGGED, 710, 410, MouseEvent.NOBUTTON));
        assertEquals(0, f.menu.groupForStone(5));
        f.editor.mouseReleased(f.event(MouseEvent.MOUSE_RELEASED, 710, 410, MouseEvent.BUTTON1));
        assertEquals(1, f.menu.groupForStone(5));
        assertEquals(new Point(700, 400), f.values.get("menuGroupPosition_modern_1"));
    }

    @Test public void resizeAndStretchToggleDuringGestureNeverSaveThePreview() {
        for (int change = 0; change < 3; change++) {
            Fixture f = new Fixture();
            when(f.client.isStretchedEnabled()).thenReturn(true);
            Rectangle last = f.menu.stones().get(5); Map<String, Object> before = new HashMap<>(f.values);
            f.editor.mousePressed(f.event(MouseEvent.MOUSE_PRESSED, last.x + 10, last.y + 10, MouseEvent.BUTTON1));
            f.editor.mouseDragged(f.event(MouseEvent.MOUSE_DRAGGED, 700, 400, MouseEvent.NOBUTTON));
            if (change == 0) when(f.client.getCanvasWidth()).thenReturn(900);
            if (change == 1) when(f.client.getStretchedDimensions()).thenReturn(new Dimension(1800, 1400));
            if (change == 2) when(f.client.isStretchedEnabled()).thenReturn(false);
            assertTrue(f.editor.mouseReleased(f.event(MouseEvent.MOUSE_RELEASED, 700, 400, MouseEvent.BUTTON1)).isConsumed());
            assertEquals(before, f.values); assertEquals(0, f.menu.groupForStone(5));
        }
    }

    @Test public void gapBlockingConsumesWholeClickAndNeverBlocksStoneOrOutsideBar() {
        Fixture f = new Fixture(); f.allowGaps = false;
        Point gap = new Point(134, 115);
        for (int button : new int[]{MouseEvent.BUTTON1, MouseEvent.BUTTON3}) {
            assertTrue(f.editor.mousePressed(f.event(MouseEvent.MOUSE_PRESSED, gap.x, gap.y, button)).isConsumed());
            assertTrue(f.editor.mouseReleased(f.event(MouseEvent.MOUSE_RELEASED, gap.x, gap.y, button)).isConsumed());
            assertTrue(f.editor.mouseClicked(f.event(MouseEvent.MOUSE_CLICKED, gap.x, gap.y, button)).isConsumed());
        }
        f.manager.isEditingLayout = false;
        assertFalse(f.editor.mousePressed(f.event(MouseEvent.MOUSE_PRESSED, 110, 110, MouseEvent.BUTTON1)).isConsumed());
        assertFalse(f.editor.mousePressed(f.event(MouseEvent.MOUSE_PRESSED, 800, 500, MouseEvent.BUTTON1)).isConsumed());
        f.allowGaps = true;
        assertFalse(f.editor.mousePressed(f.event(MouseEvent.MOUSE_PRESSED, gap.x, gap.y, MouseEvent.BUTTON1)).isConsumed());
        f.allowGaps = false;
        when(f.client.isMenuOpen()).thenReturn(true);
        assertFalse(f.editor.mousePressed(f.event(MouseEvent.MOUSE_PRESSED, gap.x, gap.y, MouseEvent.BUTTON1)).isConsumed());
        when(f.client.isMenuOpen()).thenReturn(false); when(f.manager.isLoggedIn()).thenReturn(false);
        assertFalse(f.editor.mousePressed(f.event(MouseEvent.MOUSE_PRESSED, gap.x, gap.y, MouseEvent.BUTTON1)).isConsumed());
    }

    private void dragOnePixel(Fixture f) {
        Rectangle stone = f.menu.stones().get(5);
        f.editor.mousePressed(f.event(MouseEvent.MOUSE_PRESSED, stone.x + 10, stone.y + 10, MouseEvent.BUTTON1));
        f.editor.mouseDragged(f.event(MouseEvent.MOUSE_DRAGGED, stone.x + 9, stone.y + 10, MouseEvent.NOBUTTON));
    }

    @Test public void hiddenBankIconsKeepNativeVisibleBackgroundsWithoutClickableStones() {
        Fixture f = new Fixture(2);
        Rectangle before = f.menu.groups().get(0);
        f.allowGaps = false;
        for (Widget icon : f.icons) when(icon.isHidden()).thenReturn(true);
        when(f.backgrounds.isNativeRowVisible(true)).thenReturn(true);
        clearInvocations(f.backgrounds);
        f.refresh();
        assertEquals(before, f.menu.groups().get(0));
        assertTrue(f.menu.stones().isEmpty());
        verify(f.backgrounds, times(6)).place(anyInt(), any(Point.class), anyInt(), anyInt(), anyBoolean());
        assertFalse(f.menu.isBlockedGap(new Point(before.x + 10, before.y + 10)));
        for (Widget icon : f.icons) verify(icon, never()).setHidden(false);
    }

    @Test public void bankAndPinCoverMovedStonesAndClosingRestoresAllModes() {
        for (int display = 0; display < 3; display++)
            for (int modal : new int[]{net.runelite.api.gameval.InterfaceID.Bankmain.UNIVERSE,
                    net.runelite.api.gameval.InterfaceID.BankpinKeypad.UNIVERSE}) {
                Fixture f = new Fixture(display); f.allowGaps = false;
                Map<Integer, Rectangle> before = f.menu.stones();
                Widget window = WidgetGeometryTest.widget(90, 90, 600, 150);
                when(f.client.getWidget(modal)).thenReturn(window);
                f.refresh();
                assertTrue(f.menu.stones().isEmpty());
                assertFalse(f.menu.isBlockedGap(new Point(134, 115)));
                assertFalse(f.editor.mousePressed(f.event(MouseEvent.MOUSE_PRESSED,134,115,MouseEvent.BUTTON1)).isConsumed());
                for (Widget icon : f.icons) verify(icon).setHidden(true);
                when(window.isHidden()).thenReturn(true);
                f.refresh();
                assertEquals(before, f.menu.stones());
                for (Widget icon : f.icons) verify(icon).setHidden(false);
            }
    }

    @Test public void pinOutsideTheMovedBarDoesNotHideIt() {
        Fixture f = new Fixture();
        Map<Integer, Rectangle> before = f.menu.stones();
        Widget window = WidgetGeometryTest.widget(500, 350, 300, 250);
        when(f.client.getWidget(net.runelite.api.gameval.InterfaceID.BankpinKeypad.UNIVERSE)).thenReturn(window);
        f.refresh();
        assertEquals(before, f.menu.stones());
        for (Widget icon : f.icons) verify(icon, never()).setHidden(true);
    }

    @Test public void movableWindowsTrackDragResizeHideAndCloseInEveryDisplayMode() {
        for (int display = 0; display < 3; display++)
            for (int id : new int[]{net.runelite.api.gameval.InterfaceID.Collection.FRAME,
                net.runelite.api.gameval.InterfaceID.CollectionOverview.UNIVERSE,
                net.runelite.api.gameval.InterfaceID.SkillGuide.WINDOW,
                net.runelite.api.gameval.InterfaceID.SkillGuideV2.UNIVERSE,
                net.runelite.api.gameval.InterfaceID.Settings.UNIVERSE}) {
                Fixture f = new Fixture(display);
                Map<Integer, Rectangle> original = f.menu.stones();
                Map<String, Object> saved = new HashMap<>(f.values);
                Widget window = WidgetGeometryTest.widget(600, 350, 33, 36);
                when(f.client.getWidget(id)).thenReturn(window);
                assertTrue(f.menu.windowBoundsChanged()); f.refresh();
                assertEquals(original, f.menu.stones());
                assertFalse(f.menu.windowBoundsChanged());
                window.setForcedPosition(100, 100);
                assertTrue(f.menu.windowBoundsChanged()); f.refresh();
                assertFalse(f.menu.stones().containsKey(0));
                assertEquals(original.get(5), f.menu.stones().get(5));
                window.setOriginalWidth(120);
                assertTrue(f.menu.windowBoundsChanged()); f.refresh();
                assertFalse(f.menu.stones().containsKey(2));
                assertEquals(original.get(5), f.menu.stones().get(5));
                window.setOriginalWidth(20);
                assertTrue(f.menu.windowBoundsChanged()); f.refresh();
                assertTrue(f.menu.stones().containsKey(1));
                assertFalse(f.menu.stones().containsKey(0));
                when(window.isHidden()).thenReturn(true);
                assertTrue(f.menu.windowBoundsChanged()); f.refresh();
                assertEquals(original, f.menu.stones());
                when(f.client.getWidget(id)).thenReturn(null);
                assertFalse(f.menu.windowBoundsChanged());
                assertEquals(saved, f.values);
                verify(window, never()).setHidden(anyBoolean());
                verify(window, never()).revalidate();
            }
    }

    @Test public void overlappingWindowsRemainIndependentAndNeverUseResizePreview() {
        Fixture f = new Fixture();
        Widget log = WidgetGeometryTest.widget(100,100,33,36);
        Widget settings = WidgetGeometryTest.widget(305,100,33,36);
        Widget preview = WidgetGeometryTest.widget(0,0,1000,700);
        when(f.client.getWidget(net.runelite.api.gameval.InterfaceID.Collection.FRAME)).thenReturn(log);
        when(f.client.getWidget(net.runelite.api.gameval.InterfaceID.Settings.UNIVERSE)).thenReturn(settings);
        when(f.client.getWidget(net.runelite.api.gameval.InterfaceID.Settings.INFINITE)).thenReturn(preview);
        when(f.client.getWidget(net.runelite.api.gameval.InterfaceID.CollectionOverview.RESIZE_PREVIEW)).thenReturn(preview);
        f.refresh();
        assertFalse(f.menu.stones().containsKey(0)); assertFalse(f.menu.stones().containsKey(5));
        assertTrue(f.menu.stones().containsKey(2));
        when(log.isHidden()).thenReturn(true); f.refresh();
        assertTrue(f.menu.stones().containsKey(0)); assertFalse(f.menu.stones().containsKey(5));
        when(settings.isHidden()).thenReturn(true); f.refresh();
        assertEquals(6, f.menu.stones().size());
    }

    @Test public void combatAchievementAndQuestDiaryWindowsCoverOnlyTheirOwnAreaAndRestore() {
        for (int display = 0; display < 3; display++)
            for (int id : new int[]{
                net.runelite.api.gameval.InterfaceID.CaOverview.UNIVERSE,
                net.runelite.api.gameval.InterfaceID.CaTasks.UNIVERSE,
                net.runelite.api.gameval.InterfaceID.CaBosses.UNIVERSE,
                net.runelite.api.gameval.InterfaceID.CaBoss.UNIVERSE,
                net.runelite.api.gameval.InterfaceID.CaRewards.UNIVERSE,
                net.runelite.api.gameval.InterfaceID.Questjournal.UNIVERSE,
                net.runelite.api.gameval.InterfaceID.QuestjournalOverview.UNIVERSE,
                net.runelite.api.gameval.InterfaceID.AreaTask.UNIVERSE,
                net.runelite.api.gameval.InterfaceID.Journalscroll.UNIVERSE,
                net.runelite.api.gameval.InterfaceID.Questdisplay.UNIVERSE,
                net.runelite.api.gameval.InterfaceID.Questscroll.UNIVERSE,
                net.runelite.api.gameval.InterfaceID.QuestscrollSpeedrun.UNIVERSE}) {
                Fixture f = new Fixture(display); f.allowGaps = false;
                Map<Integer, Rectangle> original = f.menu.stones();
                Map<String, Object> saved = new HashMap<>(f.values);
                Widget window = WidgetGeometryTest.widget(95,95,135,80);
                when(f.client.getWidget(id)).thenReturn(window);
                assertTrue(f.menu.windowBoundsChanged()); f.refresh();
                assertFalse(f.menu.stones().containsKey(0));
                assertFalse(f.menu.stones().containsKey(2));
                assertFalse(f.menu.isBlockedGap(new Point(134,115)));
                assertEquals(original.get(5),f.menu.stones().get(5));
                assertFalse(f.menu.windowBoundsChanged());
                when(f.client.getWidget(id)).thenReturn(null);
                assertTrue(f.menu.windowBoundsChanged()); f.refresh();
                assertEquals(original,f.menu.stones());
                assertEquals(saved,f.values);
                verify(window,never()).setForcedPosition(anyInt(),anyInt());
                verify(window,never()).setOriginalWidth(anyInt());
                verify(window,never()).setOriginalHeight(anyInt());
                verify(window,never()).setHidden(anyBoolean());
            }
    }

    @Test public void hiddenEmptyWindowRootsAndQuestSidebarDoNotOccludeStones() {
        Fixture f = new Fixture();
        Map<Integer, Rectangle> original = f.menu.stones();
        Widget hidden = WidgetGeometryTest.widget(0,0,1000,700);
        when(hidden.isHidden()).thenReturn(true);
        Widget empty = WidgetGeometryTest.widget(100,100,0,0);
        Widget sidebar = WidgetGeometryTest.widget(0,0,1000,700);
        when(f.client.getWidget(net.runelite.api.gameval.InterfaceID.CaOverview.UNIVERSE)).thenReturn(hidden);
        when(f.client.getWidget(net.runelite.api.gameval.InterfaceID.AreaTask.UNIVERSE)).thenReturn(empty);
        when(f.client.getWidget(net.runelite.api.gameval.InterfaceID.Questlist.UNIVERSE)).thenReturn(sidebar);
        assertFalse(f.menu.windowBoundsChanged()); f.refresh();
        assertEquals(original,f.menu.stones());
    }

    private static final class Fixture {
        final Client client = mock(Client.class);
        final OrbController manager = mock(OrbController.class);
        final Widget[] stones = new Widget[6];
        final Widget[] icons = new Widget[6];
        final MenuStoneBackgrounds backgrounds = mock(MenuStoneBackgrounds.class);
        final Map<String, Object> values = new HashMap<>();
        final MenuStoneController menu;
        final InterfaceEditor editor;
        final WidgetStateStore states = new WidgetStateStore();
        final Canvas canvas = new Canvas();
        boolean allowGaps = true;
        Fixture() { this(2); }
        Fixture(int display) {
            when(client.getCanvasWidth()).thenReturn(1000); when(client.getCanvasHeight()).thenReturn(700);
            when(client.getRealDimensions()).thenReturn(new Dimension(1000, 700));
            when(client.getStretchedDimensions()).thenReturn(new Dimension(2000, 1400));
            when(manager.isLoggedIn()).thenReturn(true);
            manager.isEditingLayout = true;
            when(manager.isFixedMode()).thenReturn(display == 0);
            when(manager.isClassicResizable()).thenReturn(display == 1);
            values.put("menuPosition_" + (display == 0 ? "fixed" : display == 1 ? "classic" : "modern"), new Point(100, 100));
            ConfigManager configs = mock(ConfigManager.class);
            when(configs.getConfiguration(anyString(), anyString(), any(Type.class))).thenAnswer(i -> values.get(i.getArgument(1)));
            doAnswer(i -> { values.put(i.getArgument(1), i.getArgument(2)); return null; })
                    .when(configs).setConfiguration(anyString(), anyString(), any(Object.class));
            doAnswer(i -> { values.remove(i.getArgument(1)); return null; })
                    .when(configs).unsetConfiguration(anyString(), anyString());
            for (int i = 0; i < stones.length; i++) {
                stones[i] = WidgetGeometryTest.widget(i * 33, 600, 33, 36);
                int[][] registry = display == 0 ? MenuStoneRegistry.FIXED : display == 1 ? MenuStoneRegistry.CLASSIC : MenuStoneRegistry.MODERN;
                when(client.getWidget(registry[i][0])).thenReturn(stones[i]);
                icons[i] = WidgetGeometryTest.widget(i * 33 + 5, 605, 23, 26);
                when(client.getWidget(registry[i][1])).thenReturn(icons[i]);
            }
            InterfaceLayoutConfig config = mock(InterfaceLayoutConfig.class, CALLS_REAL_METHODS);
            when(config.menuLayout()).thenReturn(MenuStoneController.Layout.FREE_POSITION);
            when(config.menuDirection()).thenReturn(MenuStoneController.Direction.HORIZONTAL);
            when(config.menuSpacing()).thenReturn(8); when(config.editGrid()).thenReturn(1);
            when(config.allowClicksThroughGaps()).thenAnswer(i -> allowGaps);
            ClientThread thread = mock(ClientThread.class);
            doAnswer(i -> { ((Runnable)i.getArgument(0)).run(); return null; }).when(thread).invokeLater(any(Runnable.class));
            FreePositionController orbs = mock(FreePositionController.class); when(orbs.bounds()).thenReturn(Map.of());
            menu = Guice.createInjector(new AbstractModule() {
                @Override protected void configure() {
                    bind(Client.class).toInstance(client); bind(InterfaceLayoutConfig.class).toInstance(config);
                    bind(ConfigManager.class).toInstance(configs); bind(OrbController.class).toProvider(() -> manager);
                    bind(WidgetStateStore.class).toInstance(states);
                    bind(WidgetBoundsExpander.class).toProvider(() -> mock(WidgetBoundsExpander.class));
                    bind(MenuStoneBackgrounds.class).toInstance(backgrounds);
                }
            }).getInstance(MenuStoneController.class);
            editor = Guice.createInjector(new AbstractModule() {
                @Override protected void configure() {
                    bind(Client.class).toInstance(client);
                    bind(InterfaceLayoutConfig.class).toInstance(config); bind(OrbController.class).toProvider(() -> manager);
                    bind(MenuStoneController.class).toProvider(() -> menu);
                    bind(MenuOverlayBridge.class).toProvider(() -> mock(MenuOverlayBridge.class));
                    bind(FreePositionController.class).toProvider(() -> orbs); bind(ClientThread.class).toProvider(() -> thread);
                }
            }).getInstance(InterfaceEditor.class);
            editor.setRefresh(this::refresh); refresh();
        }
        void refresh() {
            states.restore(); menu.apply();
            java.awt.Graphics2D g = new BufferedImage(1000, 700, BufferedImage.TYPE_INT_ARGB).createGraphics();
            try { editor.render(g); } finally { g.dispose(); }
        }
        MouseEvent event(int type, int x, int y, int button) {
            return new MouseEvent(canvas, type, 0, InputEvent.SHIFT_DOWN_MASK, x, y, 1, false, button);
        }
    }
}
