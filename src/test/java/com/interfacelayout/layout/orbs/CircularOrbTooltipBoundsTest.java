package com.interfacelayout.layout.orbs;

import com.interfacelayout.layout.orbs.widget.elements.Orbs;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import net.runelite.api.Point;
import net.runelite.api.gameval.InterfaceID;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class CircularOrbTooltipBoundsTest
{
    @Test public void completedFramesRestoreResourceUpdateBoundsAndKeepRegenRingsCentered() throws Exception
    {
        CircularOrbOverlayTest.Fixture f = new CircularOrbOverlayTest.Fixture();
        CircularOrbCoreOverlayTest.Core core = new CircularOrbCoreOverlayTest.Core(f.overlay.client, "regenmeter.RegenMeterOverlay");
        core.stub("regenmeter.RegenMeterConfig", "showSpecial", true);
        core.stub("regenmeter.RegenMeterPlugin", "getSpecialPercentage", 1.0);
        when(f.overlay.client.getMouseCanvasPosition()).thenReturn(new Point(105, 85));
        for (int frame = 0; frame < 20; frame++)
        {
            f.refresh();
            Rectangle nativeBounds = f.parent.getBounds();
            assertFalse(nativeBounds.isEmpty());
            BufferedImage image = render(f);
            assertEquals(0, f.parent.getWidth());
            Rectangle ring = CircularOrbCoreOverlayTest.visible(core.render());
            assertEquals(140, ring.getCenterX(), 1); assertEquals(87, ring.getCenterY(), 1);
            f.overlay.drawManager.processDrawComplete(() -> image);
            assertEquals(nativeBounds, f.parent.getBounds());
            assertFalse(f.backing.getBounds().isEmpty());
            assertFalse(f.number.getBounds().isEmpty());
        }
    }

    @Test public void disablingBetweenRenderAndCompletionRestoresOriginalGeometry()
    {
        CircularOrbOverlayTest.Fixture f = new CircularOrbOverlayTest.Fixture();
        when(f.overlay.client.getMouseCanvasPosition()).thenReturn(new Point(105, 85));
        f.refresh(); BufferedImage image = render(f);
        assertEquals(0, f.parent.getWidth());
        when(f.overlay.config.hideOrbTabs()).thenReturn(false); f.refresh();
        assertEquals(new Rectangle(100, 70, 57, 34), f.parent.getBounds());
        f.overlay.drawManager.processDrawComplete(() -> image);
        assertEquals(new Rectangle(100, 70, 57, 34), f.parent.getBounds());
    }

    @Test public void renderRestorePreservesNewerWidthsAndDoesNotTouchReplacementWidgets()
    {
        net.runelite.api.Client client = mock(net.runelite.api.Client.class);
        CircularOrbInput input = new CircularOrbInput();
        net.runelite.api.widgets.Widget parent = CircularOrbInputTest.widget(client, 1, null, 100, 70, 57, 34);
        input.apply(parent, null, null, null, new Rectangle(123, 70, 34, 34), false);
        input.maskOutsideHover(parent); parent.setOriginalWidth(88);
        input.restoreRenderBounds(client); assertEquals(88, parent.getWidth());
        input.maskOutsideHover(parent);
        net.runelite.api.widgets.Widget replacement = mock(net.runelite.api.widgets.Widget.class);
        when(client.getWidget(1)).thenReturn(replacement);
        input.restoreRenderBounds(client); verifyNoInteractions(replacement);
    }

    @Test public void parentBoundsRejectRemovedTabsAndCornersDuringOverlayRendering()
    {
        for (Orbs orb : new Orbs[]{Orbs.PRAYER_ORB_CONTAINER, Orbs.SPEC_ORB_CONTAINER})
        {
            CircularOrbOverlayTest.Fixture f = new CircularOrbOverlayTest.Fixture(orb,
                orb == Orbs.PRAYER_ORB_CONTAINER ? InterfaceID.Orbs.PRAYER_TEXT : InterfaceID.Orbs.SPECENERGY_TEXT);
            for (Point mouse : new Point[]{new Point(105, 85), new Point(100, 87), new Point(124, 71), new Point(140, 87), new Point(156, 87)})
            {
                when(f.overlay.client.getMouseCanvasPosition()).thenReturn(mouse);
                f.refresh();
                render(f);
                boolean inside = CircularOrbArtwork.contains(new Rectangle(123, 70, 34, 34), mouse.getX(), mouse.getY());
                assertEquals("Container-based tooltips must follow the visible circle", inside, f.parent.getBounds().contains(mouse.getX(), mouse.getY()));
            }
        }
    }

    static BufferedImage render(CircularOrbOverlayTest.Fixture f)
    {
        BufferedImage image = new BufferedImage(400, 200, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try { f.overlay.render(graphics); }
        finally { graphics.dispose(); }
        return image;
    }
}
