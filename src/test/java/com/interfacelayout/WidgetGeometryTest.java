package com.interfacelayout;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.interfacelayout.util.WidgetBoundsExpander;
import com.interfacelayout.util.WidgetGeometry;
import com.interfacelayout.util.WidgetStateStore;
import java.awt.Point;
import java.awt.Rectangle;
import net.runelite.api.Client;
import net.runelite.api.widgets.Widget;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class WidgetGeometryTest
{
    @Test public void usesCurrentCoordinatesInsteadOfCachedDrawBounds()
    {
        Widget parent = widget(790, 0, 210, 207), child = widget(5, 10, 33, 36);
        when(child.getParent()).thenReturn(parent);
        when(parent.getBounds()).thenReturn(new Rectangle(0, 0, 210, 207));
        assertEquals(new Point(795, 10), WidgetGeometry.location(child));
        parent.setForcedPosition(0, 0);
        assertEquals(new Point(5, 10), WidgetGeometry.location(child));
        assertEquals(new Rectangle(0, 0, 210, 207), parent.getBounds());
    }
    @Test public void accountsForScrollAndSafelyStopsParentCycles()
    {
        Widget parent = widget(100, 200, 100, 100), child = widget(10, 20, 33, 36);
        when(child.getParent()).thenReturn(parent);
        when(parent.getScrollX()).thenReturn(3); when(parent.getScrollY()).thenReturn(7);
        assertEquals(new Point(107, 213), WidgetGeometry.location(child));
        when(parent.getParent()).thenReturn(child);
        assertNotNull(WidgetGeometry.location(child));
    }
    @Test public void expandingNestedClippingLayersKeepsMinimapAndSiblingPositions()
    {
        Client client = mock(Client.class);
        when(client.getCanvasWidth()).thenReturn(1000); when(client.getCanvasHeight()).thenReturn(700);
        Widget root = widget(0, 0, 1000, 700), map = widget(790, 0, 210, 207);
        Widget orbs = widget(0, 10, 207, 197), minimap = widget(0, 0, 210, 180), hp = widget(5, 30, 56, 36);
        when(map.getParent()).thenReturn(root); when(orbs.getParent()).thenReturn(map);
        when(minimap.getParent()).thenReturn(map); when(hp.getParent()).thenReturn(orbs);
        when(map.getStaticChildren()).thenReturn(new Widget[]{orbs, minimap});
        when(orbs.getNestedChildren()).thenReturn(new Widget[]{hp});
        WidgetBoundsExpander expander = Guice.createInjector(new AbstractModule() {
            @Override protected void configure() {
                bind(Client.class).toInstance(client);
                bind(WidgetStateStore.class).toInstance(new WidgetStateStore());
            }
        }).getInstance(WidgetBoundsExpander.class);
        expander.allowCanvasPosition(hp);
        assertEquals(new Point(790, 0), WidgetGeometry.location(minimap));
        assertEquals(new Point(795, 40), WidgetGeometry.location(hp));
        hp.setForcedPosition(100, 200);
        assertEquals(new Point(100, 200), WidgetGeometry.location(hp));
        expander.allowCanvasPosition(hp);
        assertEquals(new Point(790, 0), WidgetGeometry.location(minimap));
        verify(map, times(1)).setForcedPosition(0, 0);
    }
    static Widget widget(int x, int y, int width, int height)
    {
        Widget widget = mock(Widget.class);
        int[] position = {x, y};
        int[] size = {width, height};
        when(widget.getRelativeX()).thenAnswer(invocation -> position[0]);
        when(widget.getRelativeY()).thenAnswer(invocation -> position[1]);
        when(widget.getWidth()).thenAnswer(invocation -> size[0]);
        when(widget.getHeight()).thenAnswer(invocation -> size[1]);
        when(widget.getOriginalWidth()).thenReturn(width); when(widget.getOriginalHeight()).thenReturn(height);
        when(widget.getBounds()).thenReturn(new Rectangle(x, y, width, height));
        doAnswer(invocation -> { position[0] = invocation.getArgument(0); position[1] = invocation.getArgument(1); return null; })
            .when(widget).setForcedPosition(anyInt(), anyInt());
        doAnswer(invocation -> { size[0] = invocation.getArgument(0); return null; }).when(widget).setOriginalWidth(anyInt());
        doAnswer(invocation -> { size[1] = invocation.getArgument(0); return null; }).when(widget).setOriginalHeight(anyInt());
        return widget;
    }
}
