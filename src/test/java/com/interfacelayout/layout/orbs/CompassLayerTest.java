package com.interfacelayout.layout.orbs;

import com.interfacelayout.util.WidgetGeometry;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetType;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class CompassLayerTest {
    @Test public void nativeCompassRendersAfterTheMapWithOriginalMaskAndWithoutDuplicateActions() {
        Fixture f=new Fixture();CompassLayer layer=new CompassLayer();
        for(int frame=0;frame<10;frame++) {
            f.compass.setOriginalX(120+frame);
            layer.refresh(f.client,f.compass,f.map);
            assertEquals(1339,f.display.getContentType());assertEquals(0,f.compass.getContentType());
            assertEquals(WidgetGeometry.bounds(f.compass),WidgetGeometry.bounds(f.display));
            assertSame(f.foreign,f.root.getChildren()[0]);assertSame(f.display,f.root.getChildren()[1]);
            verify(f.display,atLeastOnce()).setSpriteId(888);
            verify(f.display,never()).setAction(anyInt(),anyString());
            verify(f.compass,never()).setAction(anyInt(),anyString());
            assertFalse(f.display.isHidden());assertFalse(f.display.getNoClickThrough());
            layer.restore(f.client);assertEquals(1339,f.compass.getContentType());
            assertEquals(40,f.compass.getOpacity());assertTrue(f.display.isHidden());
        }
        verify(f.root,times(1)).createChild(-1,WidgetType.GRAPHIC);
        layer.clear(f.client);assertArrayEquals(new Widget[]{f.foreign},f.root.getChildren());
    }

    @Test public void coveringCompassHidesItsPromotedDisplayAndClosingBankRestoresIt() {
        Fixture f=new Fixture();OrbController manager=mock(OrbController.class);
        CircularCompassFrame frame=new CircularCompassFrame();
        frame.refresh(f.client,manager,true,f.map);assertFalse(f.display.isHidden());
        frame.restore(f.client,manager);
        Widget bank=CircularOrbInputTest.widget(f.client,InterfaceID.Bankmain.UNIVERSE,null,100,20,250,250);
        visibility(bank);frame.refresh(f.client,manager,true,f.map);
        assertTrue(f.compass.isHidden());assertTrue(f.display.isHidden());
        frame.restore(f.client,manager);bank.setHidden(true);frame.refresh(f.client,manager,true,f.map);
        assertFalse(f.compass.isHidden());assertFalse(f.display.isHidden());
        frame.restore(f.client,manager);frame.clear(f.client);
        assertEquals(1339,f.compass.getContentType());assertEquals(40,f.compass.getOpacity());
        assertArrayEquals(new Widget[]{f.foreign},f.root.getChildren());
    }

    @Test public void replacementWidgetsAndForeignChildrenSurviveCleanup() {
        Fixture f=new Fixture();CompassLayer layer=new CompassLayer();layer.refresh(f.client,f.compass,f.map);
        Widget replacement=mock(Widget.class);when(f.client.getWidget(f.compass.getId())).thenReturn(replacement);
        Widget added=mock(Widget.class);f.root.setChildren(new Widget[]{f.foreign,f.display,added});
        layer.clear(f.client);assertArrayEquals(new Widget[]{f.foreign,null,added},f.root.getChildren());
        verifyNoInteractions(replacement);
    }

    private static final class Fixture {
        final Client client=mock(Client.class);
        final Widget root=CircularOrbInputTest.widget(client,100,null,20,10,500,400);
        final Widget compass=CircularOrbInputTest.widget(client,OrbConstants.Widgets.Modern.COMPASS,root,120,50,33,33);
        final Widget map=CircularOrbInputTest.widget(client,OrbConstants.Widgets.Modern.MINIMAP_MASK,root,100,30,152,152);
        final Widget display=CircularOrbInputTest.widget(client,101,root,0,0,0,0);
        final Widget foreign=mock(Widget.class);
        Fixture() {
            when(root.getScrollX()).thenReturn(7);when(root.getScrollY()).thenReturn(3);
            AtomicReference<Widget[]> children=new AtomicReference<>(new Widget[]{foreign});
            when(root.getChildren()).thenAnswer(call->children.get());
            doAnswer(call->{children.set(call.getArgument(0));return root;}).when(root).setChildren(any());
            when(root.createChild(-1,WidgetType.GRAPHIC)).thenAnswer(call->{
                Widget[] updated=Arrays.copyOf(children.get(),children.get().length+1);
                updated[updated.length-1]=display;children.set(updated);return display;
            });
            content(compass,1339);content(display,0);visibility(compass);visibility(display);
            when(compass.getSpriteId()).thenReturn(888);compass.setOpacity(40);
        }
    }

    private static void content(Widget widget,int value) {
        AtomicInteger content=new AtomicInteger(value);when(widget.getContentType()).thenAnswer(call->content.get());
        doAnswer(call->{content.set(call.getArgument(0));return widget;}).when(widget).setContentType(anyInt());
    }

    private static void visibility(Widget widget) {
        AtomicBoolean hidden=new AtomicBoolean();when(widget.isHidden()).thenAnswer(call->hidden.get());
        when(widget.isSelfHidden()).thenAnswer(call->hidden.get());
        doAnswer(call->{hidden.set(call.getArgument(0));return widget;}).when(widget).setHidden(anyBoolean());
    }
}
