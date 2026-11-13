package com.interfacelayout.layout.orbs;

import com.interfacelayout.InterfaceLayoutConfig;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.concurrent.atomic.AtomicBoolean;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.BeforeRender;
import net.runelite.api.widgets.Widget;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class CircularMinimapFrameTest {
    @Test public void replacesCombinedFrameAndBlockersWithoutChangingNativeMapOrCompass() {
        for (boolean classic : new boolean[]{true, false}) {
            Client client = mock(Client.class);
            OrbController manager = mock(OrbController.class);
            when(manager.isClassicResizable()).thenReturn(classic);
            int mapId = classic ? OrbConstants.Widgets.Classic.MINIMAP_MASK : OrbConstants.Widgets.Modern.MINIMAP_MASK;
            int frameId = classic ? OrbConstants.Widgets.Classic.MINIMAP : OrbConstants.Widgets.Modern.MINIMAP;
            int blockId = classic ? OrbConstants.Widgets.Classic.MAP_NOCLICK_0 : OrbConstants.Widgets.Modern.MAP_NOCLICK_0;
            Widget map = widget(client, mapId, 80, 50, 152, 152);
            when(map.getContentType()).thenReturn(OrbConstants.Layout.MinimapOverlay.MINIMAP_CONTENT);
            Widget oldFrame = widget(client, frameId, 45, 25, 182, 166);
            Widget blocker = widget(client, blockId, 45, 25, 178, 40);
            CircularMinimapFrame frame = new CircularMinimapFrame();
            frame.refresh(client, manager);
            assertTrue(oldFrame.isHidden());
            assertTrue(blocker.isHidden());
            BufferedImage image = render(frame);
            assertNotEquals(0, image.getRGB(77, 126));
            assertEquals(0, image.getRGB(156, 126));
            verify(map, never()).setHidden(anyBoolean());
            verify(map, never()).setSpriteId(anyInt());
            verify(map, never()).setContentType(anyInt());
            map.setOriginalX(120);
            assertNotEquals(0, render(frame).getRGB(117, 126));
            frame.restore(client);
            assertFalse(oldFrame.isHidden());
            assertFalse(blocker.isHidden());
            assertEquals(0, render(frame).getRGB(117, 126));
            oldFrame.setHidden(true);
            frame.refresh(client, manager);
            frame.restore(client);
            assertTrue(oldFrame.isHidden());
        }
    }

    @Test public void freePositionUsesSelectedCustomRingsEvenWithNativeOrbsAndDuringEditing() {
        CircularOrbs overlay = new CircularOrbs();
        overlay.client = mock(Client.class);
        overlay.manager = mock(OrbController.class);
        overlay.config = mock(InterfaceLayoutConfig.class, CALLS_REAL_METHODS);
        when(overlay.client.getGameState()).thenReturn(GameState.LOGGED_IN);
        when(overlay.config.layout()).thenReturn(OrbLayout.FREE_POSITION);
        when(overlay.config.minimapFrame()).thenReturn(InterfaceLayoutConfig.MinimapFrame.CIRCULAR);
        when(overlay.config.hideOrbTabs()).thenReturn(false);
        when(overlay.config.editInterface()).thenReturn(true);
        Widget map = widget(overlay.client, OrbConstants.Widgets.Modern.MINIMAP_MASK, 80, 50, 152, 152);
        when(map.getContentType()).thenReturn(OrbConstants.Layout.MinimapOverlay.MINIMAP_CONTENT);
        Widget compass = widget(overlay.client, OrbConstants.Widgets.Modern.COMPASS, 50, 30, 33, 33);
        when(compass.getContentType()).thenReturn(OrbConstants.Layout.MinimapOverlay.COMPASS_CONTENT);
        Widget frame = widget(overlay.client, OrbConstants.Widgets.Modern.MINIMAP, 45, 25, 182, 166);
        overlay.start();
        overlay.onBeforeRender(new BeforeRender());
        BufferedImage image = new BufferedImage(300, 250, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        overlay.frames().render(g);
        g.dispose();
        assertTrue(frame.isHidden());
        assertNotEquals(0, image.getRGB(77, 126));
        assertNotEquals(0, image.getRGB(47, 46));
        when(overlay.config.layout()).thenReturn(OrbLayout.VERTICAL);
        overlay.onBeforeRender(new BeforeRender());
        assertFalse(frame.isHidden());
    }

    @Test public void nativeFrameDefaultAndStyleChangesPreserveResourcePackArtwork() {
        CircularOrbs overlay = new CircularOrbs();
        overlay.client = mock(Client.class);
        overlay.manager = mock(OrbController.class);
        overlay.config = mock(InterfaceLayoutConfig.class, CALLS_REAL_METHODS);
        when(overlay.client.getGameState()).thenReturn(GameState.LOGGED_IN);
        when(overlay.config.layout()).thenReturn(OrbLayout.FREE_POSITION);
        Widget map = widget(overlay.client, OrbConstants.Widgets.Modern.MINIMAP_MASK, 80, 50, 152, 152);
        when(map.getContentType()).thenReturn(1338);
        Widget compass = widget(overlay.client, OrbConstants.Widgets.Modern.COMPASS, 50, 30, 33, 33);
        when(compass.getContentType()).thenReturn(1339);
        Widget frame = widget(overlay.client, OrbConstants.Widgets.Modern.MINIMAP, 45, 25, 182, 166);
        overlay.start();
        overlay.onBeforeRender(new BeforeRender());
        assertFalse(frame.isHidden());
        assertEquals(0, render(overlay).getRGB(77, 126));
        when(overlay.config.minimapFrame()).thenReturn(InterfaceLayoutConfig.MinimapFrame.CIRCULAR);
        overlay.onBeforeRender(new BeforeRender());
        assertTrue(frame.isHidden());
        assertNotEquals(0, render(overlay).getRGB(77, 126));
        when(overlay.config.orbRim()).thenReturn(InterfaceLayoutConfig.OrbRim.NONE);
        overlay.onBeforeRender(new BeforeRender());
        assertTrue(frame.isHidden());
        assertEquals(0, render(overlay).getRGB(77, 126));
        when(overlay.config.minimapFrame()).thenReturn(InterfaceLayoutConfig.MinimapFrame.NATIVE);
        overlay.onBeforeRender(new BeforeRender());
        assertFalse(frame.isHidden());
        verify(frame, never()).setSpriteId(anyInt());
        verify(map, never()).setContentType(anyInt());
        overlay.releaseForLayout();
        assertFalse(frame.isHidden());
    }

    private static BufferedImage render(CircularOrbs overlay) {
        BufferedImage image = new BufferedImage(300, 250, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        overlay.frames().render(graphics);
        graphics.dispose();
        return image;
    }

    @Test public void respectsGameHiddenMinimizedAndReplacedWidgets() {
        Client client = mock(Client.class);
        OrbController manager = mock(OrbController.class);
        Widget map = widget(client, OrbConstants.Widgets.Modern.MINIMAP_MASK, 80, 50, 152, 152);
        when(map.getContentType()).thenReturn(OrbConstants.Layout.MinimapOverlay.MINIMAP_CONTENT);
        Widget oldFrame = widget(client, OrbConstants.Widgets.Modern.MINIMAP, 45, 25, 182, 166);
        CircularMinimapFrame frame = new CircularMinimapFrame();
        map.setHidden(true);
        frame.refresh(client, manager);
        assertFalse(oldFrame.isHidden());
        map.setHidden(false);
        when(manager.isMinimapMinimized()).thenReturn(true);
        frame.refresh(client, manager);
        assertFalse(oldFrame.isHidden());
        when(manager.isMinimapMinimized()).thenReturn(false);
        frame.refresh(client, manager);
        assertTrue(oldFrame.isHidden());
        Widget replacement = mock(Widget.class);
        when(client.getWidget(OrbConstants.Widgets.Modern.MINIMAP)).thenReturn(replacement);
        frame.restore(client);
        verifyNoInteractions(replacement);
    }

    private static Widget widget(Client client, int id, int x, int y, int width, int height) {
        Widget widget = CircularOrbInputTest.widget(client, id, null, x, y, width, height);
        AtomicBoolean hidden = new AtomicBoolean();
        when(widget.isHidden()).thenAnswer(i -> hidden.get());
        when(widget.isSelfHidden()).thenAnswer(i -> hidden.get());
        doAnswer(i -> { hidden.set(i.getArgument(0)); return null; }).when(widget).setHidden(anyBoolean());
        return widget;
    }

    private static BufferedImage render(CircularMinimapFrame frame) {
        BufferedImage image = new BufferedImage(300, 250, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        frame.paint(g, new CircularOrbArtwork(), InterfaceLayoutConfig.OrbRim.SILVER);
        g.dispose();
        return image;
    }
}
