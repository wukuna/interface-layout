package com.interfacelayout;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.interfacelayout.util.WidgetBoundsExpander;
import com.interfacelayout.util.WidgetGeometry;
import com.interfacelayout.util.WidgetStateStore;
import java.awt.Point;
import net.runelite.api.Client;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetType;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class InventoryClickProtectionTest
{
    @Test public void movingClassicTabsKeepsInventoryBlockingWithoutBlockingTheExpandedCanvas()
    {
        Fixture f = new Fixture(true);
        f.expander.allowCanvasPosition(f.tab);
        f.tab.setForcedPosition(100, 100);
        assertFalse(f.side.getNoClickThrough());
        assertFalse(f.row.getNoClickThrough());
        assertTrue(f.inventory.getNoClickThrough());
        assertEquals(new Point(790, 300), WidgetGeometry.location(f.inventory));
        assertEquals(210, f.inventory.getWidth()); assertEquals(300, f.inventory.getHeight());
        assertFalse(WidgetGeometry.bounds(f.inventory).contains(100, 100));
        assertTrue(WidgetGeometry.bounds(f.inventory).contains(850, 400));
        assertEquals(new Point(100, 100), WidgetGeometry.location(f.tab));
        f.expander.allowCanvasPosition(f.tab);
        assertTrue(f.inventory.getNoClickThrough());
        f.states.restore(); f.expander.clear();
        assertTrue(f.side.getNoClickThrough());
        assertFalse(f.inventory.getNoClickThrough());
        assertFalse(f.row.getNoClickThrough());
    }

    @Test public void nativeClickThroughLayoutsAndDecorativeGraphicsStayUnchanged()
    {
        Fixture f = new Fixture(false);
        f.expander.allowCanvasPosition(f.tab);
        assertFalse(f.inventory.getNoClickThrough());
        verify(f.inventory, never()).setNoClickThrough(true);
        Fixture blocked = new Fixture(true);
        when(blocked.inventory.getType()).thenReturn(WidgetType.GRAPHIC);
        blocked.expander.allowCanvasPosition(blocked.tab);
        verify(blocked.inventory, never()).setNoClickThrough(true);
    }

    @Test public void anAlreadyFullCanvasParentKeepsItsNativeBlockingFlag()
    {
        Fixture f = new Fixture(true);
        when(f.side.getWidth()).thenReturn(1000); when(f.side.getHeight()).thenReturn(700);
        f.side.setForcedPosition(0, 0);
        f.expander.allowCanvasPosition(f.tab);
        verify(f.side, never()).setNoClickThrough(false);
        verify(f.inventory, never()).setNoClickThrough(true);
    }

    private static final class Fixture
    {
        final WidgetStateStore states = new WidgetStateStore();
        final Widget root = WidgetGeometryTest.widget(0,0,1000,700);
        final Widget side = WidgetGeometryTest.widget(790,250,210,450);
        final Widget row = WidgetGeometryTest.widget(0,0,210,50);
        final Widget inventory = WidgetGeometryTest.widget(0,50,210,300);
        final Widget tab = WidgetGeometryTest.widget(5,5,33,36);
        final WidgetBoundsExpander expander;
        Fixture(boolean blocking)
        {
            Client client = mock(Client.class); when(client.getCanvasWidth()).thenReturn(1000); when(client.getCanvasHeight()).thenReturn(700);
            when(side.getParent()).thenReturn(root); when(row.getParent()).thenReturn(side);
            when(inventory.getParent()).thenReturn(side); when(tab.getParent()).thenReturn(row);
            when(side.getStaticChildren()).thenReturn(new Widget[]{row, inventory});
            when(row.getStaticChildren()).thenReturn(new Widget[]{tab});
            when(tab.getType()).thenReturn(WidgetType.GRAPHIC);
            for (Widget widget : new Widget[]{side,row,inventory})
            {
                boolean[] value = {widget == side && blocking};
                when(widget.getNoClickThrough()).thenAnswer(i -> value[0]);
                doAnswer(i -> { value[0] = i.getArgument(0); return null; }).when(widget).setNoClickThrough(anyBoolean());
            }
            expander = Guice.createInjector(new AbstractModule() {
                @Override protected void configure() { bind(Client.class).toInstance(client); bind(WidgetStateStore.class).toInstance(states); }
            }).getInstance(WidgetBoundsExpander.class);
        }
    }
}
