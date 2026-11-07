package com.interfacelayout.layout.orbs;

import com.interfacelayout.InterfaceLayoutConfig;
import com.interfacelayout.layout.menu.MenuButtonSprites;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Menu;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.Point;
import net.runelite.api.events.BeforeRender;
import net.runelite.api.events.PostMenuSort;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetType;
import net.runelite.client.callback.ClientThread;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class CircularOrbOverlayTest
{
    static final class Fixture
    {
        final CircularOrbs overlay = new CircularOrbs();
        final Widget parent, backing, button, indicator, emptyParent, empty, icon, number, hover, tooltip;
        Fixture()
        {
            overlay.client = mock(Client.class);
            overlay.config = mock(InterfaceLayoutConfig.class, CALLS_REAL_METHODS);
            overlay.sprites = mock(MenuButtonSprites.class);
            overlay.thread = mock(ClientThread.class);
            overlay.manager = mock(OrbController.class);
            when(overlay.config.hideOrbTabs()).thenReturn(true);
            when(overlay.client.getGameState()).thenReturn(GameState.LOGGED_IN);
            when(overlay.client.getMouseCanvasPosition()).thenReturn(new Point(140, 87));
            parent = widget(InterfaceID.Orbs.ORB_SPECENERGY, null, 100, 70, 57, 34);
            backing = widget(InterfaceID.Orbs.SPECENERGY_BACKING, parent, 0, 0, 57, 34);
            button = widget(InterfaceID.Orbs.SPECBUTTON, parent, 0, 0, 57, 34);
            indicator = widget(InterfaceID.Orbs.SPECENERGY_INDICATOR, parent, 27, 4, 26, 26);
            emptyParent = widget(InterfaceID.Orbs.ORB_SPECENERGY_EMPTY, parent, 27, 4, 26, 13);
            empty = widget(InterfaceID.Orbs.ORB_SPECENERGY_EMPTY_GRAPHIC0, emptyParent, 0, 0, 26, 26);
            icon = widget(InterfaceID.Orbs.SPECENERGY_ICON, parent, 27, 4, 26, 26);
            number = widget(InterfaceID.Orbs.SPECENERGY_TEXT, parent, 0, 0, 25, 34);
            hover = widget(1001, parent, 0, 0, 57, 34);
            tooltip = widget(InterfaceID.Orbs.TOOLTIP, null, 0, 0, 60, 30);
            when(parent.getStaticChildren()).thenReturn(new Widget[]{backing, button, indicator, emptyParent, icon, number, hover});
            when(emptyParent.getStaticChildren()).thenReturn(new Widget[]{empty});
            when(hover.hasListener()).thenReturn(true);
            when(number.getText()).thenReturn("100");
            when(number.getTextColor()).thenReturn(Color.GREEN.getRGB());
            when(indicator.getType()).thenReturn(WidgetType.GRAPHIC);
            when(empty.getType()).thenReturn(WidgetType.GRAPHIC);
            when(overlay.sprites.getPadded(indicator)).thenReturn(solid(Color.CYAN));
            when(overlay.sprites.getPadded(empty)).thenReturn(solid(new Color(20, 30, 40)));
            when(overlay.sprites.get(icon)).thenReturn(solid(Color.YELLOW));
            overlay.start();
        }
        private Widget widget(int id, Widget parent, int x, int y, int width, int height)
        {
            return CircularOrbInputTest.widget(overlay.client, id, parent, x, y, width, height);
        }
        void refresh() { overlay.onBeforeRender(new BeforeRender()); }
        BufferedImage render()
        {
            BufferedImage image = new BufferedImage(200, 130, BufferedImage.TYPE_INT_ARGB);
            Graphics2D draw = image.createGraphics();
            overlay.render(draw); draw.dispose(); return image;
        }
    }

    @Test public void fullOrbIsAnOverlayIncludingNativeFillAndClippedEmptyPortion()
    {
        Fixture f = new Fixture();
        when(f.overlay.config.orbContent()).thenReturn(InterfaceLayoutConfig.OrbContent.ICON_ONLY);
        when(f.icon.isHidden()).thenReturn(true);
        f.refresh();
        assertEquals(255, f.indicator.getOpacity());
        assertEquals(255, f.empty.getOpacity());
        assertEquals(255, f.backing.getOpacity());
        BufferedImage image = f.render();
        assertEquals(new Color(20, 30, 40).getRGB(), image.getRGB(140, 80));
        assertEquals(Color.CYAN.getRGB(), image.getRGB(140, 94));
        assertEquals(0, image.getRGB(105, 85));
        assertEquals(0, image.getRGB(123, 70));
        when(f.overlay.config.hideOrbTabs()).thenReturn(false); f.refresh();
        assertEquals(0, f.indicator.getOpacity()); assertEquals(0, f.empty.getOpacity());
        assertEquals(57, f.parent.getOriginalWidth());
        assertEquals(27, f.indicator.getOriginalX());
        verify(f.button, never()).setOnOpListener(any());
        verify(f.button, never()).setAction(anyInt(), anyString());
    }

    @Test public void hiddenIndicatorOrMissingIconDoesNotRestoreTheSideTab()
    {
        Fixture f = new Fixture();
        when(f.indicator.isHidden()).thenReturn(true);
        when(f.overlay.sprites.get(f.icon)).thenReturn(null);
        f.refresh();
        assertEquals(255, f.backing.getOpacity()); assertEquals(255, f.number.getOpacity());
        assertEquals(34, f.parent.getOriginalWidth());
        assertNotEquals(0, f.render().getRGB(140, 97));
        when(f.overlay.config.hideOrbTabs()).thenReturn(false); f.refresh();
        assertEquals(57, f.parent.getOriginalWidth());
        verify(f.indicator, never()).setHidden(anyBoolean());
    }

    @Test public void nativeContainerAndTooltipDoNotReactToRemovedTabOrTransparentCorner()
    {
        Fixture f = new Fixture();
        Widget tipText = CircularOrbInputTest.widget(f.overlay.client, f.tooltip.getId(), f.tooltip, 0, 0, 60, 30);
        when(f.overlay.client.getWidget(f.tooltip.getId())).thenReturn(f.tooltip);
        when(tipText.getIndex()).thenReturn(0); when(f.tooltip.getChild(0)).thenReturn(tipText);
        when(f.tooltip.getChildren()).thenReturn(new Widget[]{tipText});
        for (Point point : new Point[]{new Point(105, 85), new Point(124, 71)})
        {
            when(f.overlay.client.getMouseCanvasPosition()).thenReturn(point); f.refresh();
            assertEquals(0, f.parent.getOriginalWidth());
            assertEquals(0, f.button.getOriginalWidth());
            assertEquals(255, f.tooltip.getOpacity()); assertEquals(255, tipText.getOpacity());
            assertNotEquals(0, f.render().getRGB(140, 95));
        }
        when(f.overlay.client.getMouseCanvasPosition()).thenReturn(new Point(140, 87)); f.refresh();
        assertEquals(34, f.parent.getOriginalWidth());
        assertEquals(0, f.tooltip.getOpacity()); assertEquals(0, tipText.getOpacity());
        assertFalse(f.parent.getBounds().contains(105, 85));
        assertEquals(new Rectangle(100, 70, 57, 34), f.hover.getBounds());
    }

    @Test public void cornerMenusAreRemovedWithoutRemovingWorldActionsOrChangingNativeOperations()
    {
        Fixture f = new Fixture();
        Menu menu = mock(Menu.class);
        when(f.overlay.client.getMenu()).thenReturn(menu);
        MenuEntry world = mock(MenuEntry.class), special = mock(MenuEntry.class), hover = mock(MenuEntry.class);
        when(world.getType()).thenReturn(MenuAction.WALK);
        when(special.getType()).thenReturn(MenuAction.CC_OP);
        when(special.getParam1()).thenReturn(InterfaceID.Orbs.SPECBUTTON);
        when(hover.getWidget()).thenReturn(f.hover);
        when(menu.getMenuEntries()).thenReturn(new MenuEntry[]{world, special, hover});
        when(f.overlay.client.getMouseCanvasPosition()).thenReturn(new Point(124, 71));
        f.refresh(); f.overlay.onPostMenuSort(new PostMenuSort());
        verify(menu).setMenuEntries(new MenuEntry[]{world});
        clearInvocations(menu);
        when(f.overlay.client.getMouseCanvasPosition()).thenReturn(new Point(140, 87));
        f.refresh(); f.overlay.onPostMenuSort(new PostMenuSort());
        verify(menu, never()).setMenuEntries(any());
    }

    @Test public void gameHiddenParentAndEditModeUseNativeState()
    {
        Fixture f = new Fixture();
        f.refresh();
        when(f.parent.isHidden()).thenReturn(true); f.refresh();
        assertEquals(0, f.backing.getOpacity()); assertEquals(57, f.parent.getOriginalWidth());
        assertEquals(0, f.render().getRGB(140, 90));
        when(f.parent.isHidden()).thenReturn(false);
        when(f.overlay.config.editInterface()).thenReturn(true); f.refresh();
        assertEquals(0, f.backing.getOpacity()); assertEquals(57, f.button.getOriginalWidth());
    }

    static BufferedImage solid(Color color)
    {
        BufferedImage image = new BufferedImage(26, 26, BufferedImage.TYPE_INT_ARGB);
        Graphics2D draw = image.createGraphics(); draw.setColor(color); draw.fillRect(0,0,26,26); draw.dispose();
        return image;
    }
}
