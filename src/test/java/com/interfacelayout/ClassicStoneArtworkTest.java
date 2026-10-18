package com.interfacelayout;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.interfacelayout.layout.menu.MenuStoneBackgrounds;
import com.interfacelayout.layout.menu.MenuStoneController;
import com.interfacelayout.layout.menu.MenuStoneRegistry;
import com.interfacelayout.layout.orbs.OrbController;
import com.interfacelayout.util.WidgetBoundsExpander;
import com.interfacelayout.util.WidgetStateStore;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.lang.reflect.Type;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetType;
import net.runelite.client.config.ConfigManager;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/** Issue #7: selection-highlight widgets do not contain the shared stone artwork. */
public class ClassicStoneArtworkTest
{
    @Test public void smallerStonesUseMatchingArtworkAndHitBoundsInAllModes()
    {
        for (int mode = 0; mode < 3; mode++)
            for (boolean stretched : new boolean[]{false, true})
            {
                Fixture f = new Fixture(mode == 0, mode == 2);
                when(f.client.isStretchedEnabled()).thenReturn(stretched);
                when(f.config.menuGroupSize()).thenReturn(80);
                when(f.config.sizeStoneInventory()).thenReturn(75);
                when(f.config.menuSpacing()).thenReturn(3);
                f.menu.apply();
                Rectangle inv = f.menu.stones().get(3);
                assertEquals(20, inv.width); assertEquals(22, inv.height);
                assertEquals(20, f.stones[3].getWidth()); assertEquals(22, f.stones[3].getHeight());
                assertEquals(14, f.icons[3].getWidth()); assertEquals(16, f.icons[3].getHeight());
                net.runelite.api.Point center = new net.runelite.api.Point(inv.x + inv.width / 2, inv.y + inv.height / 2);
                net.runelite.api.MenuEntry action = mock(net.runelite.api.MenuEntry.class);
                when(action.getType()).thenReturn(net.runelite.api.MenuAction.CC_OP);
                int[][] registry = mode == 0 ? MenuStoneRegistry.FIXED : mode == 1 ? MenuStoneRegistry.CLASSIC : MenuStoneRegistry.MODERN;
                when(action.getParam1()).thenReturn(registry[3][0]);
                when(f.client.getMouseCanvasPosition()).thenReturn(center);
                assertTrue(f.menu.isHoveredStoneAction(action));
                when(f.client.getMouseCanvasPosition()).thenReturn(new net.runelite.api.Point(inv.x + inv.width, inv.y));
                assertFalse(f.menu.isHoveredStoneAction(action));
                f.restore(); assertEquals(33, f.stones[3].getWidth()); assertEquals(36, f.stones[3].getHeight());
                when(f.config.menuGroupSize()).thenReturn(100); when(f.config.sizeStoneInventory()).thenReturn(100);
                f.menu.apply(); assertEquals(33, f.menu.stones().get(3).width);
            }
    }

    @Test public void smallerStonesKeepBankAndWindowProtectionAndModernLogoutSize()
    {
        Fixture f = new Fixture(false);
        when(f.config.menuGroupSize()).thenReturn(50);
        for (Widget icon : f.icons) icon.setHidden(true);
        f.menu.apply(); assertTrue(f.menu.stones().isEmpty()); assertEquals(14, f.artCount());
        f.restore();
        for (Widget icon : f.icons) icon.setHidden(false);
        Widget window = visible(400, 300, 17, 18);
        when(f.client.getWidget(InterfaceID.BankpinKeypad.UNIVERSE)).thenReturn(window);
        f.menu.apply(); assertFalse(f.menu.stones().containsKey(0));
        f.restore(); window.setHidden(true); f.menu.apply(); assertTrue(f.menu.stones().containsKey(0));
        Fixture modern = new Fixture(false, true); when(modern.config.sizeStoneLogout()).thenReturn(50);
        modern.menu.apply(); assertEquals(17, modern.stones[10].getWidth());
        assertEquals(18, modern.stones[10].getHeight());
        modern.restore(); assertEquals(33, modern.stones[10].getWidth());
    }

    @Test public void freshClassicFreePositionKeepsRowsSeparateAndCustomGroupsSurvive()
    {
        for (MenuStoneController.Direction direction : MenuStoneController.Direction.values())
        {
            Fixture f = new Fixture(false);
            when(f.config.menuLayout()).thenReturn(MenuStoneController.Layout.FREE_POSITION);
            when(f.config.menuDirection()).thenReturn(direction);
            f.menu.apply();
            assertEquals(2, f.menu.groups().size());
            for (int i = 0; i < 14; i++) assertEquals(i / 7, f.menu.groupForStone(i));
            assertFalse(f.menu.groups().get(0).intersects(f.menu.groups().get(1)));
            Rectangle top = f.menu.groups().get(0), bottom = f.menu.groups().get(1);
            f.restore(); f.menu.apply();
            assertEquals(top, f.menu.groups().get(0)); assertEquals(bottom, f.menu.groups().get(1));
            f.settings.put("menuGroup_classic_8", 0);
            f.restore(); f.menu.apply();
            assertEquals(0, f.menu.groupForStone(8));
            f.menu.reattach(8);
            assertEquals(1, f.menu.groupForStone(8));
        }
        Fixture legacy = new Fixture(false);
        when(legacy.config.menuLayout()).thenReturn(MenuStoneController.Layout.FREE_POSITION);
        legacy.settings.put("menuPosition_classic", new java.awt.Point(100, 100));
        legacy.menu.apply();
        assertEquals(2, legacy.menu.groups().size());
        assertEquals(1, legacy.menu.groupForStone(8));
        assertEquals(new java.awt.Point(100, 100), legacy.menu.groups().get(0).getLocation());
    }

    @Test public void modernAndClassicSplitMovedImplicitRowsWithoutOverwritingDetachedGroups()
    {
        for (boolean modern : new boolean[]{false, true})
        {
            String mode = modern ? "modern" : "classic";
            Fixture f = new Fixture(false, modern);
            if (modern) when(f.stones[10].getRelativeY()).thenReturn(10);
            when(f.config.menuLayout()).thenReturn(MenuStoneController.Layout.FREE_POSITION);
            when(f.config.menuDirection()).thenReturn(MenuStoneController.Direction.HORIZONTAL);
            f.settings.put("menuPosition_" + mode, new java.awt.Point(100, 100));
            f.settings.put("menuGroup_" + mode + "_3", 1);
            f.settings.put("menuGroupPosition_" + mode + "_1", new java.awt.Point(50, 50));
            f.settings.put("menuGroup_" + mode + "_8", 0); // Explicit user join remains in top group.
            f.settings.put("menuGeneration_" + mode + "_2", 3);
            f.menu.apply();
            assertEquals(0, f.menu.groupForStone(0)); assertEquals(1, f.menu.groupForStone(3));
            assertEquals(2, f.menu.groupForStone(7)); assertEquals(0, f.menu.groupForStone(8));
            assertEquals(4, f.menu.groupGeneration(2));
            assertEquals(200, f.menu.groups().get(2).y);
            assertEquals(new java.awt.Point(50, 50), f.settings.get("menuGroupPosition_" + mode + "_1"));
            f.menu.reattach(3); assertEquals(0, f.menu.groupForStone(3));
            f.menu.reattach(8); assertEquals(2, f.menu.groupForStone(8));
            Map<String, Object> saved = new HashMap<>(f.settings);
            f.restore(); f.menu.apply(); assertEquals(saved, f.settings);
        }
    }

    @Test public void freeRowsRetainWholeArtworkAndNativeOnePixelOffset()
    {
        for (boolean fixed : new boolean[]{false, true})
        {
            Fixture f = new Fixture(fixed);
            String mode = fixed ? "fixed" : "classic";
            when(f.config.menuLayout()).thenReturn(MenuStoneController.Layout.FREE_POSITION);
            when(f.config.menuDirection()).thenReturn(MenuStoneController.Direction.HORIZONTAL);
            when(f.rows[0].getRelativeY()).thenReturn(301);
            when(f.stones[1].getRelativeX()).thenReturn(431);
            f.settings.put("menuPosition_" + mode, new java.awt.Point(100, 150));
            f.menu.apply();
            assertEquals(0, f.artCount());
            verify(f.rows[0]).setForcedPosition(100, 151);
            assertEquals(131, f.menu.stones().get(1).x);
            assertEquals(2, f.menu.groups().size());
            f.restore();
            for (Widget row : f.rows) {
                assertEquals(WidgetType.GRAPHIC, row.getType());
                verify(row).setForcedPosition(-1, -1);
            }
        }
    }

    @Test public void movedFreeRowSplitsForWindowsAndRestoresWholeArtworkAfterClosing()
    {
        Fixture f = new Fixture(false);
        when(f.config.menuLayout()).thenReturn(MenuStoneController.Layout.FREE_POSITION);
        when(f.config.menuDirection()).thenReturn(MenuStoneController.Direction.HORIZONTAL);
        f.settings.put("menuPosition_classic", new java.awt.Point(100, 150));
        Widget window = visible(100, 150, 33, 36);
        when(f.client.getWidget(InterfaceID.Collection.FRAME)).thenReturn(window);
        f.menu.apply();
        assertEquals(6, f.artCount());
        assertFalse(f.menu.stones().containsKey(0));
        f.restore(); window.setHidden(true); f.menu.apply();
        assertEquals(0, f.artCount());
        assertEquals(new Rectangle(100, 150, 33, 36), f.menu.stones().get(0));
        f.restore();
        f.settings.put("menuOrder_classic_1", -1);
        f.menu.apply();
        assertEquals(7, f.artCount()); // Reordered row still uses individual stone artwork.
        assertTrue(f.menu.stones().get(1).x < f.menu.stones().get(0).x);
    }

    @Test public void movingNativeRowsTranslatesUnequalSlotsAndBankArtTogether()
    {
        Fixture f = new Fixture(false);
        when(f.stones[1].getRelativeX()).thenReturn(431);
        when(f.icons[1].getRelativeX()).thenReturn(438);
        f.settings.put("menuNativePosition_classic_0", new java.awt.Point(100, 150));
        f.menu.apply();
        assertEquals(new Rectangle(100, 150, 33, 36), f.menu.stones().get(0));
        assertEquals(new Rectangle(131, 150, 33, 36), f.menu.stones().get(1));
        verify(f.icons[1]).setForcedPosition(138, 155);
        assertEquals(0, f.artCount()); // Both native row graphics remain intact.
        f.restore();
        for (Widget stone : f.stones) stone.setHidden(true);
        for (Widget icon : f.icons) icon.setHidden(true);
        f.menu.apply();
        assertEquals(0, f.artCount());
        assertTrue(f.menu.stones().isEmpty());
        assertEquals(new java.awt.Point(100, 150), f.menu.groups().get(0).getLocation());
        f.restore();
        for (Widget icon : f.icons) assertTrue(icon.isHidden());
    }
    @Test public void compactedClassicRowsCropArtworkWithoutChangingSelectionHighlights()
    {
        Fixture f = new Fixture(false);
        when(f.config.menuSpacing()).thenReturn(1);
        f.menu.apply();
        assertEquals(14, f.menu.stones().size());
        assertEquals(14, f.artCount());
        verify(f.rows[0], never()).setHidden(true);
        verify(f.rows[1], never()).setHidden(true);
        // Slot 2 crops its own part of the row, rather than shrinking a row into one tile.
        Widget clip = f.children.get(0).get(4), art = f.children.get(0).get(5);
        verify(clip).setOriginalX(68); verify(clip).setOriginalY(0);
        verify(clip).setOriginalWidth(33); verify(clip).setOriginalHeight(36);
        verify(art).setOriginalX(-66); verify(art).setOriginalY(0);
        verify(art).setOriginalWidth(231); verify(art).setOriginalHeight(36);
        verify(art).setSpriteTiling(false);
        verify(art).setSpriteId(1036);
        for (Widget stone : f.stones) verify(stone, never()).setSpriteId(anyInt());
    }

    @Test public void untouchedNativeRowsPreserveUnequalOverlappingTargetsAndIconOffsets()
    {
        for (boolean fixed : new boolean[]{false, true})
        {
            Fixture f = new Fixture(fixed);
            int[] offsets = {0, 31, 65, 98, 130, 164, 197};
            for (int n = 0; n < 14; n++)
            {
                when(f.stones[n].getRelativeX()).thenReturn(400 + offsets[n % 7]);
                when(f.stones[n].getWidth()).thenReturn(n % 3 == 0 ? 35 : 33);
                when(f.icons[n].getRelativeX()).thenReturn(407 + offsets[n % 7]);
                when(f.icons[n].getRelativeY()).thenReturn(302 + n / 7 * 100 + n % 2);
            }
            f.menu.apply();
            assertEquals(0, f.artCount());
            assertEquals(14, f.menu.stones().size());
            for (int n = 0; n < 14; n++)
            {
                assertEquals(400 + offsets[n % 7], f.menu.stones().get(n).x);
                verify(f.stones[n], never()).setForcedPosition(anyInt(), anyInt());
                verify(f.icons[n], never()).setForcedPosition(anyInt(), anyInt());
            }
            for (Widget row : f.rows) verify(row, never()).setType(anyInt());
            // Bank scripts may hide both icon and selection graphic while leaving row art.
            f.restore();
            for (Widget stone : f.stones) stone.setHidden(true);
            for (Widget icon : f.icons) icon.setHidden(true);
            f.menu.apply();
            assertEquals(0, f.artCount());
            assertTrue(f.menu.stones().isEmpty());
            for (Widget row : f.rows) verify(row, never()).setType(anyInt());
        }
    }

    @Test public void layoutsSpacingIndividualHidingAndStretchedModeRetainArtwork()
    {
        for (boolean fixed : new boolean[]{false, true})
            for (boolean stretched : new boolean[]{false, true})
                for (MenuStoneController.Layout layout : MenuStoneController.Layout.values())
                {
                    Fixture f = new Fixture(fixed);
                    when(f.client.isStretchedEnabled()).thenReturn(stretched);
                    when(f.config.menuLayout()).thenReturn(layout);
                    when(f.config.menuSpacing()).thenReturn(8);
                    when(f.config.hideCombatStone()).thenReturn(true);
                    f.menu.apply();
                    int expected = layout == MenuStoneController.Layout.HIDDEN ? 0 : 13;
                    assertEquals(expected, f.artCount());
                    assertEquals(expected, f.menu.stones().size());
                    assertFalse(f.menu.stones().containsKey(0));
                    f.restore();
                    assertEquals(0, f.artCount());
                    assertEquals(WidgetType.GRAPHIC, f.rows[0].getType());
                    assertEquals(WidgetType.GRAPHIC, f.rows[1].getType());
                }
    }

    @Test public void bankKeepsVisibleDecorationWithoutRevealingIconsOrActions()
    {
        for (boolean fixed : new boolean[]{false, true})
        {
            Fixture f = new Fixture(fixed);
            when(f.config.menuLayout()).thenReturn(MenuStoneController.Layout.FREE_POSITION);
            for (Widget stone : f.stones) stone.setHidden(true);
            for (Widget icon : f.icons) icon.setHidden(true);
            f.menu.apply();
            assertEquals(14, f.artCount());
            assertTrue(f.menu.stones().isEmpty());
            for (Widget icon : f.icons) verify(icon, never()).setHidden(false);
            f.restore();
            for (Widget icon : f.icons) assertTrue(icon.isHidden());
            for (Widget stone : f.stones) stone.setHidden(false);
            for (Widget icon : f.icons) icon.setHidden(false);
            f.menu.apply();
            assertEquals(14, f.menu.stones().size());
            assertEquals(14, f.artCount());
        }
    }

    @Test public void hiddenNativeRowsStayHidden()
    {
        Fixture f = new Fixture(false);
        for (Widget row : f.rows) row.setHidden(true);
        for (Widget stone : f.stones) stone.setHidden(true);
        for (Widget icon : f.icons) icon.setHidden(true);
        f.menu.apply();
        assertEquals(0, f.artCount());
        assertTrue(f.menu.stones().isEmpty());
        for (Widget row : f.rows) verify(row, never()).setHidden(false);
    }

    @Test public void bankPinAndMovableWindowsRemoveOnlyCoveredArtworkAndRestore()
    {
        for (int windowId : new int[]{InterfaceID.Bankmain.UNIVERSE, InterfaceID.BankpinKeypad.UNIVERSE,
            InterfaceID.Collection.FRAME, InterfaceID.CaOverview.UNIVERSE, InterfaceID.AreaTask.UNIVERSE})
        {
            Fixture f = new Fixture(false);
            Widget window = visible(400, 300, 33, 36);
            when(f.client.getWidget(windowId)).thenReturn(window);
            f.menu.apply();
            assertEquals(6, f.artCount());
            assertFalse(f.menu.stones().containsKey(0));
            verify(window, never()).setForcedPosition(anyInt(), anyInt());
            verify(window, never()).setOriginalWidth(anyInt());
            f.restore(); window.setHidden(true); f.menu.apply();
            assertEquals(0, f.artCount());
            assertEquals(new Rectangle(400, 300, 33, 36), f.menu.stones().get(0));
        }
    }

    @Test public void repeatedRefreshAndTemporaryNativeRestoreDoNotLeakArtwork()
    {
        Fixture f = new Fixture(false);
        when(f.config.menuLayout()).thenReturn(MenuStoneController.Layout.FREE_POSITION);
        Widget foreign = mock(Widget.class);
        f.children.get(0).add(foreign);
        for (int i = 0; i < 20; i++)
        {
            f.menu.apply(); assertEquals(14, f.artCount());
            f.restore(); assertEquals(0, f.artCount());
            assertEquals(1, f.children.get(0).size());
            assertSame(foreign, f.children.get(0).get(0));
        }
        f.menu.toggleShown(); f.menu.apply();
        assertEquals(0, f.artCount());
        assertEquals(WidgetType.GRAPHIC, f.rows[0].getType());
        f.menu.toggleShown(); f.menu.apply(); assertEquals(14, f.artCount());
        f.restore(); when(f.orbs.isLoggedIn()).thenReturn(false); f.menu.apply();
        assertEquals(0, f.artCount());
    }

    private static Widget visible(int x, int y, int width, int height)
    {
        Widget w = WidgetGeometryTest.widget(x, y, width, height);
        // Native revalidation returns a cleared forced position to its original coordinates.
        doAnswer(i -> {
            int px = i.getArgument(0), py = i.getArgument(1);
            when(w.getRelativeX()).thenReturn(px == -1 ? x : px);
            when(w.getRelativeY()).thenReturn(py == -1 ? y : py);
            return null;
        }).when(w).setForcedPosition(anyInt(), anyInt());
        boolean[] hidden = {false};
        when(w.isHidden()).thenAnswer(i -> hidden[0]);
        when(w.isSelfHidden()).thenAnswer(i -> hidden[0]);
        doAnswer(i -> { hidden[0] = i.getArgument(0); return null; }).when(w).setHidden(anyBoolean());
        return w;
    }

    private static final class Fixture
    {
        final Client client = mock(Client.class);
        final InterfaceLayoutConfig config = mock(InterfaceLayoutConfig.class, CALLS_REAL_METHODS);
        final OrbController orbs = mock(OrbController.class);
        final WidgetStateStore states = new WidgetStateStore();
        final Widget[] rows = new Widget[2], stones = new Widget[14], icons = new Widget[14];
        final List<List<Widget>> children = new ArrayList<>();
        final MenuStoneBackgrounds backgrounds;
        final MenuStoneController menu;
        final Map<String, Object> settings = new HashMap<>();
        Fixture(boolean fixed)
        {
            this(fixed, false);
        }
        Fixture(boolean fixed, boolean modern)
        {
            when(client.getCanvasWidth()).thenReturn(1200); when(client.getCanvasHeight()).thenReturn(800);
            when(orbs.isLoggedIn()).thenReturn(true); when(orbs.isFixedMode()).thenReturn(fixed);
            when(orbs.isClassicResizable()).thenReturn(!fixed && !modern);
            when(config.menuLayout()).thenReturn(MenuStoneController.Layout.VANILLA_HORIZONTAL);
            int[][] registry = fixed ? MenuStoneRegistry.FIXED : modern ? MenuStoneRegistry.MODERN : MenuStoneRegistry.CLASSIC;
            for (int r = 0; r < 2; r++)
            {
                Widget row = visible(400, 300 + r * 100, 231, 36); rows[r] = row;
                int[] type = {WidgetType.GRAPHIC};
                when(row.getType()).thenAnswer(i -> type[0]);
                doAnswer(i -> { type[0] = i.getArgument(0); return null; }).when(row).setType(anyInt());
                when(row.getSpriteId()).thenReturn(1036 + r);
                int component = fixed ? (r == 0 ? InterfaceID.Toplevel.SIDE_TOP_CONTAINER_GRAPHIC0
                    : InterfaceID.Toplevel.SIDE_BOTTOM_CONTAINER_GRAPHIC0)
                    : (r == 0 ? InterfaceID.ToplevelOsrsStretch.SIDE_MENU_GRAPHIC5
                    : InterfaceID.ToplevelOsrsStretch.SIDE_MENU_GRAPHIC3);
                when(client.getWidget(component)).thenReturn(row);
                List<Widget> list = new ArrayList<>(); children.add(list);
                when(row.getChildren()).thenAnswer(i -> list.toArray(new Widget[0]));
                doAnswer(i -> { list.clear(); java.util.Collections.addAll(list, (Widget[]) i.getArgument(0)); return null; })
                    .when(row).setChildren(any(Widget[].class));
                doAnswer(i -> {
                    Widget clip = mock(Widget.class);
                    int index = list.size(); when(clip.getIndex()).thenReturn(index); list.add(clip);
                    doAnswer(a -> {
                        Widget art = mock(Widget.class);
                        when(art.getIndex()).thenReturn(list.size()); list.add(art); return art;
                    }).when(clip).createChild(-1, WidgetType.GRAPHIC);
                    return clip;
                }).when(row).createChild(-1, WidgetType.LAYER);
            }
            for (int n = 0; n < 14; n++)
            {
                stones[n] = visible(400 + n % 7 * 33, 300 + n / 7 * 100, 33, 36);
                icons[n] = visible(405 + n % 7 * 33, 305 + n / 7 * 100, 23, 26);
                when(client.getWidget(registry[n][0])).thenReturn(stones[n]);
                when(client.getWidget(registry[n][1])).thenReturn(icons[n]);
            }
            ConfigManager configs = mock(ConfigManager.class);
            when(configs.getConfiguration(anyString(), anyString(), any(Type.class))).thenAnswer(i -> settings.get(i.getArgument(1)));
            doAnswer(i -> { settings.put(i.getArgument(1), i.getArgument(2)); return null; })
                .when(configs).setConfiguration(anyString(), anyString(), any(Object.class));
            doAnswer(i -> { settings.remove(i.getArgument(1)); return null; }).when(configs).unsetConfiguration(anyString(), anyString());
            com.google.inject.Injector injector = Guice.createInjector(new AbstractModule() {
                @Override protected void configure()
                {
                    bind(Client.class).toInstance(client); bind(InterfaceLayoutConfig.class).toInstance(config);
                    bind(OrbController.class).toProvider(() -> orbs);
                    bind(ConfigManager.class).toInstance(configs);
                    bind(WidgetStateStore.class).toInstance(states);
                    bind(WidgetBoundsExpander.class).toInstance(mock(WidgetBoundsExpander.class));
                    if (modern) bind(MenuStoneBackgrounds.class).toInstance(mock(MenuStoneBackgrounds.class));
                }
            });
            backgrounds = injector.getInstance(MenuStoneBackgrounds.class);
            menu = injector.getInstance(MenuStoneController.class);
        }
        int artCount() { return (children.get(0).size() + children.get(1).size()) / 2; }
        void restore() { backgrounds.restore(); states.restore(); }
    }
}
