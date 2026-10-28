package com.interfacelayout;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.interfacelayout.layout.menu.MenuButtonOverlay;
import com.interfacelayout.layout.menu.MenuButtonSprites;
import com.interfacelayout.layout.menu.MenuStoneController;
import com.interfacelayout.layout.menu.MenuStoneRegistry;
import com.interfacelayout.layout.orbs.OrbController;
import com.interfacelayout.util.WidgetStateStore;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Menu;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.Point;
import net.runelite.api.SpritePixels;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.PostMenuSort;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class MenuButtonOverlayTest
{
    @Test public void sizesAndGroupsAllModesWithoutResizingNativeWidgets()
    {
        for (int mode = 0; mode < 3; mode++)
        {
            Fixture f = new Fixture(mode);
            f.apply();
            assertTrue(f.overlay.active());
            assertEquals(new Rectangle(300, 100, 14 * 66, 72), f.controller.groups().get(0));
            for (int i = 0; i < 14; i++)
            {
                assertEquals(new Rectangle(300 + 66 * i, 100, 66, 72), f.controller.stones().get(i));
                assertTrue(f.stones[i].isSelfHidden());
                assertTrue(f.icons[i].isSelfHidden());
                verify(f.stones[i], never()).setOriginalWidth(anyInt());
                verify(f.stones[i], never()).setOriginalHeight(anyInt());
                verify(f.stones[i], never()).setForcedPosition(anyInt(), anyInt());
            }
            // Disable returns the same widget instances and original visibility.
            f.controller.restoreBackgrounds(); f.states.restore();
            when(f.config.menuOverlayButtons()).thenReturn(false);
            f.apply();
            assertFalse(f.overlay.active());
            assertEquals(33, f.controller.stones().get(3).width);
            assertFalse(f.icons[3].isSelfHidden());
        }
    }

    @Test public void hiddenIconsStayHiddenAndUnknownActionsFallBackToNative()
    {
        Fixture f = new Fixture(1);
        f.icons[3].setHidden(true);
        f.apply();
        assertFalse(f.controller.stones().containsKey(3));
        f.controller.restoreBackgrounds(); f.states.restore();
        assertTrue(f.icons[3].isSelfHidden());
        f.icons[3].setHidden(false);
        when(f.stones[5].getOnOpListener()).thenReturn(null);
        f.apply();
        assertFalse(f.overlay.active());
        assertEquals(33, f.controller.stones().get(3).width);
        assertFalse(f.icons[3].isSelfHidden());
    }

    @Test public void nativeOperationsRemainNativeAndRegisteredWidgetOptionsAreNotified()
    {
        Fixture f = new Fixture(2);
        f.apply();
        when(f.stones[3].getActions()).thenReturn(new String[]{"Inventory", "Configure"});
        f.hover(3);
        f.overlay.onPostMenuSort(new PostMenuSort());
        assertEquals(3, f.entries.size());
        MenuEntry primary = f.entries.get(2), secondary = f.entries.get(1);
        assertEquals("Inventory", primary.getOption());
        assertEquals(MenuAction.CC_OP, primary.getType());
        assertEquals(1, primary.getIdentifier());
        assertEquals(2, secondary.getIdentifier());
        assertEquals(-1, primary.getParam0());
        assertEquals(f.stones[3].getId(), primary.getParam1());
        verify(f.events, times(2)).post(isA(MenuEntryAdded.class));
        // A second build must neither duplicate entries nor let world actions win.
        f.overlay.onPostMenuSort(new PostMenuSort());
        assertEquals(3, f.entries.size());
        verify(f.client, never()).runScript(anyInt(), any());
    }

    @Test public void openMenusForeignWidgetsAndRetiredGameframesAreUntouched()
    {
        Fixture f = new Fixture(0);
        f.apply(); f.hover(2);
        when(f.client.isMenuOpen()).thenReturn(true);
        f.overlay.onPostMenuSort(new PostMenuSort());
        assertEquals(2, f.entries.size());
        when(f.client.isMenuOpen()).thenReturn(false);
        MenuEntry foreign = f.entry().setOption("Deposit").setType(MenuAction.CC_OP);
        doReturn(mock(Widget.class)).when(foreign).getWidget();
        f.entries.add(foreign);
        f.overlay.onPostMenuSort(new PostMenuSort());
        assertSame(foreign, f.entries.get(2));
        f.entries.remove(foreign);
        when(f.client.getTopLevelInterfaceId()).thenReturn(999);
        f.overlay.onPostMenuSort(new PostMenuSort());
        assertEquals(2, f.entries.size());
        when(f.client.getTopLevelInterfaceId()).thenReturn(f.root);
        when(f.client.getWidget(f.stones[2].getId())).thenReturn(mock(Widget.class));
        f.overlay.onPostMenuSort(new PostMenuSort());
        assertEquals(2, f.entries.size());
    }

    @Test public void supportedWindowsOccludeReplacementButtonsAndGameHiddenParentsRemainHidden()
    {
        Fixture f = new Fixture(2);
        Widget bank = WidgetGeometryTest.widget(290, 90, 140, 100);
        when(f.client.getWidget(InterfaceID.Bankmain.UNIVERSE)).thenReturn(bank);
        f.apply();
        assertFalse(f.controller.stones().containsKey(0));
        assertTrue(f.controller.stones().containsKey(3));
        f.controller.restoreBackgrounds(); f.states.restore();
        Widget hiddenParent = mock(Widget.class);
        when(hiddenParent.isSelfHidden()).thenReturn(true);
        when(f.icons[3].getParent()).thenReturn(hiddenParent);
        when(f.icons[3].isHidden()).thenReturn(true);
        f.apply();
        f.controller.restoreBackgrounds(); f.states.restore();
        verify(hiddenParent, never()).setHidden(false);
    }

    @Test public void resourcePackReplacementReloadAndRemovalRefreshWithoutChangingSettings()
    {
        Fixture f = new Fixture(2);
        f.apply();
        int spriteId = f.icons[3].getSpriteId();
        f.spriteOverrides.put(spriteId, pixels(Color.RED));
        assertEquals(Color.RED.getRGB(), f.renderIcon(3));
        f.spriteOverrides.put(spriteId, pixels(Color.GREEN));
        assertEquals(Color.GREEN.getRGB(), f.renderIcon(3));
        f.widgetOverrides.put(f.icons[3].getId(), pixels(Color.BLUE));
        assertEquals(Color.BLUE.getRGB(), f.renderIcon(3));
        f.widgetOverrides.clear(); f.spriteOverrides.clear();
        assertEquals(Color.WHITE.getRGB(), f.renderIcon(3));
        verifyNoInteractions(f.configs);
    }

    @Test public void scaleBoundsAndDefaultsAreSupportedByRealConfigurationProxy()
    {
        ConfigManager configs = mock(ConfigManager.class);
        InterfaceLayoutConfig config = RealConfigProxy.create(InterfaceLayoutConfig.class, configs);
        assertFalse(config.menuOverlayButtons()); assertEquals(100, config.menuGroupSize());
        assertFalse(config.menuKeepNativeIcons());
        Fixture f = new Fixture(0);
        when(f.config.menuGroupSize()).thenReturn(Integer.MAX_VALUE);
        assertEquals(66, f.overlay.scaled(33));
        when(f.config.menuGroupSize()).thenReturn(Integer.MIN_VALUE);
        assertEquals(8, f.overlay.scaled(33));
        when(f.config.menuGroupSize()).thenReturn(80);
        assertEquals(26, f.overlay.scaled(33));
    }

    @Test public void nativeIconsShrinkBelowOneHundredAndStayCentredWhenButtonsGrow()
    {
        int[] percentages = {25, 50, 90, 100, 125, 150, 200};
        int[] widths = {6, 12, 20, 23, 23, 23, 23};
        int[] heights = {6, 13, 23, 26, 26, 26, 26};
        for (int mode = 0; mode < 3; mode++)
        {
            Fixture f = new Fixture(mode);
            when(f.config.menuKeepNativeIcons()).thenReturn(true);
            f.widgetOverrides.put(f.icons[3].getId(), pixels(Color.RED));
            for (int p = 0; p < percentages.length; p++)
            {
                f.controller.restoreBackgrounds(); f.states.restore();
                clearInvocations(f.icons[3]);
                when(f.config.menuGroupSize()).thenReturn(percentages[p]);
                f.apply();
                Rectangle button = f.controller.stones().get(3);
                Rectangle icon = colouredBounds(f.render(), button, Color.RED);
                assertEquals(widths[p], icon.width);
                assertEquals(heights[p], icon.height);
                assertTrue(Math.abs(button.getCenterX() - icon.getCenterX()) <= 0.5);
                assertTrue(Math.abs(button.getCenterY() - icon.getCenterY()) <= 0.5);
                assertTrue(button.contains(icon));
                verify(f.icons[3], never()).setOriginalWidth(anyInt());
                verify(f.icons[3], never()).setOriginalHeight(anyInt());
            }
            verifyNoInteractions(f.configs);
        }
    }

    @Test public void nativeIconToggleAndPackReloadKeepButtonBoundsAndClickArea()
    {
        Fixture f = new Fixture(2);
        f.apply();
        Rectangle button = new Rectangle(f.controller.stones().get(3));
        f.widgetOverrides.put(f.icons[3].getId(), pixels(Color.RED));
        assertEquals(46, colouredBounds(f.render(), button, Color.RED).width);
        when(f.config.menuKeepNativeIcons()).thenReturn(true);
        assertEquals(23, colouredBounds(f.render(), button, Color.RED).width);
        f.widgetOverrides.put(f.icons[3].getId(), pixels(Color.BLUE));
        assertEquals(23, colouredBounds(f.render(), button, Color.BLUE).width);
        assertEquals(button, f.controller.stones().get(3));
        // The larger button remains clickable outside the smaller icon.
        when(f.client.getMouseCanvasPosition()).thenReturn(new Point(button.x + 1, button.y + 1));
        f.overlay.onPostMenuSort(new PostMenuSort());
        assertEquals("Tab 3", f.entries.get(f.entries.size() - 1).getOption());
        when(f.client.getMouseCanvasPosition()).thenReturn(null);
        when(f.config.menuKeepNativeIcons()).thenReturn(false);
        assertEquals(46, colouredBounds(f.render(), button, Color.BLUE).width);
        verifyNoInteractions(f.configs);
    }

    @Test public void centredIconsPreserveIncomingGraphicsTransformAndClip()
    {
        Fixture f = new Fixture(1);
        when(f.config.menuKeepNativeIcons()).thenReturn(true);
        f.widgetOverrides.put(f.icons[3].getId(), pixels(Color.RED));
        f.apply();
        Rectangle button = f.controller.stones().get(3);
        BufferedImage image = new BufferedImage(3200, 2000, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.scale(2, 2);
        graphics.clip(button);
        java.awt.geom.AffineTransform transform = graphics.getTransform();
        Rectangle clip = graphics.getClipBounds();
        f.overlay.render(graphics);
        assertEquals(transform, graphics.getTransform());
        assertEquals(clip, graphics.getClipBounds());
        graphics.dispose();
        Rectangle scaledButton = new Rectangle(button.x * 2, button.y * 2, button.width * 2, button.height * 2);
        Rectangle icon = colouredBounds(image, scaledButton, Color.RED);
        assertEquals(46, icon.width);
        assertEquals(52, icon.height);
        assertTrue(Math.abs(scaledButton.getCenterX() - icon.getCenterX()) <= 1);
        assertEquals(0, image.getRGB(scaledButton.x - 1, scaledButton.y));
    }

    private static Rectangle colouredBounds(BufferedImage image, Rectangle area, Color color)
    {
        Rectangle result = null;
        for (int y = area.y; y < area.y + area.height; y++)
            for (int x = area.x; x < area.x + area.width; x++)
                if (image.getRGB(x, y) == color.getRGB())
                {
                    Rectangle pixel = new Rectangle(x, y, 1, 1);
                    result = result == null ? pixel : result.union(pixel);
                }
        assertNotNull("Expected icon pixels", result);
        return result;
    }

    @Test public void unavailableSpritesLeaveNativeButtonsVisible()
    {
        Fixture f = new Fixture(0);
        when(f.client.getSprites(any(), anyInt(), eq(0))).thenReturn(null);
        f.apply();
        assertFalse(f.overlay.active());
        assertFalse(f.icons[3].isSelfHidden());
        assertEquals(33, f.controller.stones().get(3).width);
    }

    private static SpritePixels pixels(Color color)
    {
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics(); g.setColor(color); g.fillRect(0, 0, 16, 16); g.dispose();
        SpritePixels pixels = mock(SpritePixels.class); when(pixels.toBufferedImage()).thenReturn(image); return pixels;
    }

    private static final class Fixture
    {
        final Client client = mock(Client.class);
        final InterfaceLayoutConfig config = mock(InterfaceLayoutConfig.class, CALLS_REAL_METHODS);
        final ConfigManager configs = mock(ConfigManager.class);
        final WidgetStateStore states = new WidgetStateStore();
        final EventBus events = mock(EventBus.class);
        final MenuButtonOverlay overlay;
        final MenuStoneController controller;
        final Widget[] stones = new Widget[14], icons = new Widget[14];
        final Map<Integer, SpritePixels> spriteOverrides = new HashMap<>(), widgetOverrides = new HashMap<>();
        final List<MenuEntry> entries = new ArrayList<>();
        final int root;
        Fixture(int mode)
        {
            int[][] registry = mode == 0 ? MenuStoneRegistry.FIXED : mode == 1 ? MenuStoneRegistry.CLASSIC : MenuStoneRegistry.MODERN;
            root = registry[0][0] >>> 16;
            when(client.getTopLevelInterfaceId()).thenReturn(root);
            when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
            when(client.getCanvasWidth()).thenReturn(1600); when(client.getCanvasHeight()).thenReturn(1000);
            when(client.getSpriteOverrides()).thenReturn(spriteOverrides); when(client.getWidgetSpriteOverrides()).thenReturn(widgetOverrides);
            SpritePixels original = pixels(Color.WHITE);
            when(client.getSprites(any(), anyInt(), eq(0))).thenReturn(new SpritePixels[]{original});
            when(config.menuOverlayButtons()).thenReturn(true); when(config.menuGroupSize()).thenReturn(200);
            when(config.menuLayout()).thenReturn(MenuStoneController.Layout.VERTICAL_LEFT);
            when(config.restoreLogoutTab()).thenReturn(true);
            when(configs.getConfiguration(eq("interfacelayout"), startsWith("menuHorizontal_"), eq(Boolean.class))).thenReturn(true);
            when(configs.getConfiguration(eq("interfacelayout"), startsWith("menuPosition_"), eq(java.awt.Point.class)))
                .thenReturn(new java.awt.Point(300, 100));
            OrbController orbs = mock(OrbController.class);
            when(orbs.isLoggedIn()).thenReturn(true); when(orbs.isFixedMode()).thenReturn(mode == 0);
            when(orbs.isClassicResizable()).thenReturn(mode == 1);
            for (int i = 0; i < 14; i++)
            {
                stones[i] = widget(registry[i][0], i % 7 * 33, i / 7 * 40, 33, 36);
                icons[i] = widget(registry[i][1], i % 7 * 33 + 5, i / 7 * 40 + 5, 23, 26);
                when(stones[i].getActions()).thenReturn(new String[]{"Tab " + i});
                when(stones[i].getOnOpListener()).thenReturn(new Object[]{914});
            }
            int[] rowIds = mode == 0 ? new int[]{InterfaceID.Toplevel.SIDE_TOP_CONTAINER_GRAPHIC0, InterfaceID.Toplevel.SIDE_BOTTOM_CONTAINER_GRAPHIC0}
                : mode == 1 ? new int[]{InterfaceID.ToplevelOsrsStretch.SIDE_MENU_GRAPHIC5, InterfaceID.ToplevelOsrsStretch.SIDE_MENU_GRAPHIC3}
                : new int[]{InterfaceID.ToplevelPreEoc.SIDE_MOVABLE_BACKGROUND, InterfaceID.ToplevelPreEoc.SIDE_STATIC_BACKGROUND};
            for (int i = 0; i < 2; i++) widget(rowIds[i], 0, i * 40, 231, 36);
            Menu menu = mock(Menu.class); when(client.getMenu()).thenReturn(menu);
            when(menu.getMenuEntries()).thenAnswer(inv -> entries.toArray(new MenuEntry[0]));
            doAnswer(inv -> { entries.remove(inv.getArgument(0)); return null; }).when(menu).removeMenuEntry(any());
            when(menu.createMenuEntry(anyInt())).thenAnswer(inv -> { MenuEntry e = entry(); entries.add(e); return e; });
            entries.add(entry().setType(MenuAction.CANCEL)); entries.add(entry().setType(MenuAction.WALK));
            Injector injector = Guice.createInjector(new AbstractModule() {
                @Override protected void configure() {
                    bind(Client.class).toInstance(client); bind(InterfaceLayoutConfig.class).toInstance(config);
                    bind(ConfigManager.class).toInstance(configs); bind(OrbController.class).toProvider(() -> orbs);
                    bind(WidgetStateStore.class).toInstance(states); bind(EventBus.class).toInstance(events);
                    com.interfacelayout.layout.menu.MenuStoneBackgrounds backgrounds = mock(com.interfacelayout.layout.menu.MenuStoneBackgrounds.class);
                    when(backgrounds.isNativeRowVisible(anyBoolean())).thenReturn(true);
                    when(backgrounds.isNativeRowVisible(anyInt())).thenReturn(true);
                    bind(com.interfacelayout.layout.menu.MenuStoneBackgrounds.class).toInstance(backgrounds);
                }
            });
            overlay = injector.getInstance(MenuButtonOverlay.class); controller = injector.getInstance(MenuStoneController.class);
        }
        Widget widget(int id, int x, int y, int w, int h)
        {
            Widget widget = WidgetGeometryTest.widget(x, y, w, h);
            when(widget.getId()).thenReturn(id); when(widget.getSpriteId()).thenReturn(id & 65535);
            boolean[] hidden = {false};
            when(widget.isSelfHidden()).thenAnswer(inv -> hidden[0]); when(widget.isHidden()).thenAnswer(inv -> hidden[0]);
            doAnswer(inv -> { hidden[0] = inv.getArgument(0); return null; }).when(widget).setHidden(anyBoolean());
            when(client.getWidget(id)).thenReturn(widget); return widget;
        }
        MenuEntry entry()
        {
            MenuEntry e = mock(MenuEntry.class, RETURNS_SELF);
            doAnswer(inv -> { when(e.getType()).thenReturn(inv.getArgument(0)); return e; }).when(e).setType(any());
            doAnswer(inv -> { when(e.getOption()).thenReturn(inv.getArgument(0)); return e; }).when(e).setOption(any());
            doAnswer(inv -> { when(e.getIdentifier()).thenReturn(inv.getArgument(0)); return e; }).when(e).setIdentifier(anyInt());
            doAnswer(inv -> { when(e.getParam0()).thenReturn(inv.getArgument(0)); return e; }).when(e).setParam0(anyInt());
            doAnswer(inv -> { when(e.getParam1()).thenReturn(inv.getArgument(0)); return e; }).when(e).setParam1(anyInt());
            when(e.getWidget()).thenAnswer(inv -> client.getWidget(e.getParam1()));
            return e;
        }
        void apply() { controller.apply(); clearInvocations(configs); }
        void hover(int i) { Rectangle b = controller.stones().get(i); when(client.getMouseCanvasPosition()).thenReturn(new Point(b.x + b.width / 2, b.y + b.height / 2)); }
        int renderIcon(int i)
        {
            BufferedImage image = render();
            Rectangle bounds = controller.stones().get(i); return image.getRGB(bounds.x + bounds.width / 2, bounds.y + bounds.height / 2);
        }
        BufferedImage render()
        {
            BufferedImage image = new BufferedImage(1600, 1000, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = image.createGraphics(); overlay.render(g); g.dispose();
            return image;
        }
    }
}
