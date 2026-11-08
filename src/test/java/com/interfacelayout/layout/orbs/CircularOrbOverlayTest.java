package com.interfacelayout.layout.orbs;

import com.interfacelayout.InterfaceLayoutConfig;
import com.interfacelayout.layout.orbs.widget.elements.Orbs;
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
        Fixture() { this(Orbs.SPEC_ORB_CONTAINER, InterfaceID.Orbs.SPECENERGY_TEXT); }
        Fixture(Orbs orb, int textId)
        {
            overlay.client = mock(Client.class);
            overlay.config = mock(InterfaceLayoutConfig.class, CALLS_REAL_METHODS);
            overlay.sprites = mock(MenuButtonSprites.class);
            overlay.thread = mock(ClientThread.class);
            overlay.manager = mock(OrbController.class);
            when(overlay.config.hideOrbTabs()).thenReturn(true);
            when(overlay.client.getGameState()).thenReturn(GameState.LOGGED_IN);
            when(overlay.client.getMouseCanvasPosition()).thenReturn(new Point(140, 87));
            parent = widget(orb.getComponentId(), null, 100, 70, 57, 34);
            backing = widget(orb.getBackingId(), parent, 0, 0, 57, 34);
            button = widget(orb.getButtonId(), parent, 0, 0, 57, 34);
            indicator = widget(orb.getIndicatorId(), parent, 27, 4, 26, 26);
            emptyParent = widget(orb.getEmptyId(), parent, 27, 4, 26, 13);
            empty = widget(7001, emptyParent, 0, 0, 26, 26);
            icon = widget(orb.getIconId(), parent, 27, 4, 26, 26);
            number = widget(textId, parent, 0, 0, 25, 34);
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

    @Test public void paddedFillArtworkKeepsFrameContentAndClickAreaConcentric() {
        Fixture f = new Fixture(Orbs.PRAYER_ORB_CONTAINER, InterfaceID.Orbs.PRAYER_TEXT);
        BufferedImage padded = new BufferedImage(26,26,BufferedImage.TYPE_INT_ARGB);
        Graphics2D g=padded.createGraphics();g.setColor(Color.CYAN);g.fillOval(0,2,22,22);g.dispose();
        when(f.overlay.sprites.getPadded(f.indicator)).thenReturn(padded);
        when(f.emptyParent.isHidden()).thenReturn(true);
        when(f.icon.isHidden()).thenReturn(true);
        when(f.number.isHidden()).thenReturn(true);
        when(f.overlay.client.getMouseCanvasPosition()).thenReturn(new Point(138,87));
        f.refresh();
        Rectangle circle = new CircularOrbArtwork().aroundFill(new Rectangle(127,74,26,26),padded);
        assertTrue(circle.getCenterX()<140);
        assertEquals(circle,f.parent.getBounds());
        assertEquals(circle,f.button.getBounds());
        BufferedImage actual=f.render(), expected=new BufferedImage(200,130,BufferedImage.TYPE_INT_ARGB);
        g=expected.createGraphics();
        CircularOrbArtwork artwork=new CircularOrbArtwork();artwork.paintBackground(g,circle);
        g.drawImage(padded,127,74,26,26,null);artwork.paint(g,circle,f.overlay.config.orbRim());g.dispose();
        for(int y=circle.y;y<circle.y+circle.height;y++)for(int x=circle.x;x<circle.x+circle.width;x++)
            assertEquals(expected.getRGB(x,y),actual.getRGB(x,y));
        f.refresh();assertEquals(circle,f.parent.getBounds());
        when(f.overlay.config.hideOrbTabs()).thenReturn(false);f.refresh();
        assertEquals(new Rectangle(100,70,57,34),f.parent.getBounds());
    }

    @Test public void resourceUpdatesRemainReachableWithoutHovering() {
        Orbs[] types = {Orbs.HP_ORB_CONTAINER, Orbs.PRAYER_ORB_CONTAINER, Orbs.RUN_ORB_CONTAINER, Orbs.SPEC_ORB_CONTAINER};
        int[] numbers = {InterfaceID.Orbs.HEALTH_TEXT, InterfaceID.Orbs.PRAYER_TEXT, InterfaceID.Orbs.RUNENERGY_TEXT, InterfaceID.Orbs.SPECENERGY_TEXT};
        for (int i = 0; i < types.length; i++) {
            Fixture f = new Fixture(types[i], numbers[i]);
            when(f.overlay.client.getMouseCanvasPosition()).thenReturn(new Point(10, 10));
            when(f.overlay.config.orbContent()).thenReturn(InterfaceLayoutConfig.OrbContent.ICON_ONLY);
            when(f.icon.isHidden()).thenReturn(true);
            f.refresh();
            for (int amount : new int[]{25, 90, 0, 100}) {
                assertFalse(types[i] + " update container is clipped away", f.parent.getBounds().isEmpty());
                for (Widget listener : new Widget[]{f.backing, f.button, f.number}) {
                    assertFalse("Native listener must remain inside its parent", listener.getBounds().intersection(f.parent.getBounds()).isEmpty());
                    assertFalse(listener.getBounds().contains(10, 10));
                }
                f.overlay.onScriptPreFired(new net.runelite.api.events.ScriptPreFired(1000));
                when(f.number.getText()).thenReturn(Integer.toString(amount));
                f.emptyParent.setOriginalHeight(26 * (100 - amount) / 100);
                f.overlay.onScriptPostFired(new net.runelite.api.events.ScriptPostFired(1000));
                BufferedImage actual = f.render();
                int expected = amount >= 90 ? Color.CYAN.getRGB() : new Color(20, 30, 40).getRGB();
                assertEquals("Fill follows resource changes without mouse movement", expected, actual.getRGB(140, 80));
                when(f.overlay.config.orbContent()).thenReturn(InterfaceLayoutConfig.OrbContent.NUMBER_ONLY);
                BufferedImage textImage = f.render();
                Graphics2D g = actual.createGraphics();
                CircularOrbs.paintContent(g, new Rectangle(123, 70, 34, 34), null, Integer.toString(amount), Color.GREEN,
                    InterfaceLayoutConfig.OrbContent.NUMBER_ONLY, 255, 0);
                g.dispose();
                for (int y = 70; y < 104; y++) for (int x = 123; x < 157; x++)
                    assertEquals("Number follows native updates without hovering", actual.getRGB(x, y), textImage.getRGB(x, y));
                when(f.overlay.config.orbContent()).thenReturn(InterfaceLayoutConfig.OrbContent.ICON_ONLY);
            }
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
            assertEquals(34, f.parent.getOriginalWidth());
            assertEquals(1, f.button.getOriginalWidth());
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
