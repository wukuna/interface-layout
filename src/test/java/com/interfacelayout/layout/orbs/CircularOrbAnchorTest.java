package com.interfacelayout.layout.orbs;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.interfacelayout.layout.orbs.widget.elements.Orbs;
import com.interfacelayout.util.WidgetGeometry;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.runelite.api.Point;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.OverlayPosition;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class CircularOrbAnchorTest
{
    @Test public void anchoredOrbsKeepTheirSizeAcrossHoverAndRenderOrder()
    {
        Orbs[] orbs = {Orbs.HP_ORB_CONTAINER, Orbs.PRAYER_ORB_CONTAINER, Orbs.RUN_ORB_CONTAINER, Orbs.SPEC_ORB_CONTAINER};
        int[] numbers = {InterfaceID.Orbs.HEALTH_TEXT, InterfaceID.Orbs.PRAYER_TEXT, InterfaceID.Orbs.RUNENERGY_TEXT, InterfaceID.Orbs.SPECENERGY_TEXT};
        for (int i = 0; i < orbs.length; i++)
        {
            Fixture f = new Fixture(orbs[i], numbers[i]);
            f.anchor.setPreferredPosition(OverlayPosition.CANVAS_TOP_RIGHT);
            for (int frame = 0; frame < 20; frame++)
            {
                boolean hovered = frame % 3 == 0;
                when(f.circular.overlay.client.getMouseCanvasPosition()).thenReturn(hovered ? new Point(140, 87) : new Point(105, 85));
                f.circular.refresh();
                if (frame % 2 == 0) assertEquals(new Dimension(57, 34), f.anchor.render(null));
                BufferedImage image = CircularOrbTooltipBoundsTest.render(f.circular);
                assertEquals(hovered ? 57 : 0, f.circular.parent.getWidth());
                assertEquals("Anchor size must not follow temporary tooltip masking", new Dimension(57, 34), f.anchor.render(null));
                f.circular.overlay.drawManager.processDrawComplete(() -> image);
                assertEquals(new Rectangle(100, 70, 57, 34), f.circular.parent.getBounds());
            }
            verify(f.free, times(1)).applyOverlayPosition(orbs[i], new java.awt.Point(100, 70));
        }
    }

    @Test public void stableBoundsStillFollowLayoutChangesAndActualVisibility()
    {
        Fixture f = new Fixture(Orbs.SPEC_ORB_CONTAINER, InterfaceID.Orbs.SPECENERGY_TEXT);
        f.anchor.setPreferredLocation(new java.awt.Point(100, 70));
        when(f.circular.overlay.client.getMouseCanvasPosition()).thenReturn(new Point(10, 10));
        f.circular.refresh();
        CircularOrbTooltipBoundsTest.render(f.circular);
        assertEquals(new Dimension(57, 34), f.anchor.render(null));

        f.circular.overlay.releaseForLayout();
        f.circular.parent.setOriginalWidth(65);
        f.bridge.sync();
        f.circular.refresh();
        CircularOrbTooltipBoundsTest.render(f.circular);
        assertEquals(new Dimension(65, 34), f.anchor.render(null));

        when(f.circular.parent.isHidden()).thenReturn(true);
        assertNull(f.anchor.render(null));
        f.bridge.sync();
        assertTrue(f.anchor.getBounds().isEmpty());
        when(f.circular.parent.isHidden()).thenReturn(false);
        f.circular.overlay.releaseForLayout();
        f.bridge.sync();
        assertEquals(new Dimension(65, 34), f.anchor.render(null));
        f.anchor.setPreferredLocation(null);
        f.anchor.revalidate();
        assertEquals(new Dimension(65, 34), f.anchor.render(null));
        verify(f.free).reset(Orbs.SPEC_ORB_CONTAINER);
        f.bridge.stop();
        assertNull(f.anchor.render(null));
        assertTrue(f.anchor.getBounds().isEmpty());
    }

    private static final class Fixture
    {
        final CircularOrbOverlayTest.Fixture circular;
        final FreePositionController free = mock(FreePositionController.class);
        final FreeOrbOverlayBridge bridge;
        final Overlay anchor;

        Fixture(Orbs orb, int number)
        {
            circular = new CircularOrbOverlayTest.Fixture(orb, number);
            when(free.overlayBounds()).thenAnswer(i -> circular.parent.isHidden() ? Collections.emptyMap()
                : Collections.singletonMap(orb, WidgetGeometry.bounds(circular.parent)));
            OverlayManager overlays = mock(OverlayManager.class);
            List<Overlay> registered = new ArrayList<>();
            when(overlays.add(any())).thenAnswer(i -> registered.add(i.getArgument(0)));
            bridge = Guice.createInjector(new AbstractModule() {
                @Override protected void configure() {
                    bind(FreePositionController.class).toProvider(() -> free);
                    bind(OverlayManager.class).toProvider(() -> overlays);
                    bind(net.runelite.api.Client.class).toInstance(circular.overlay.client);
                    bind(net.runelite.client.config.ConfigManager.class).toInstance(mock(net.runelite.client.config.ConfigManager.class));
                    bind(OrbController.class).toProvider(() -> circular.overlay.manager);
                }
            }).getInstance(FreeOrbOverlayBridge.class);
            bridge.start(() -> {
                circular.overlay.releaseForLayout();
                bridge.sync();
                circular.refresh();
            });
            anchor = registered.get(0);
        }
    }
}
