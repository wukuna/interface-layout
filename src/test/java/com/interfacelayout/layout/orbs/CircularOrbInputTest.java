package com.interfacelayout.layout.orbs;

import java.awt.Rectangle;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import net.runelite.api.Client;
import net.runelite.api.widgets.Widget;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class CircularOrbInputTest {
    static Widget widget(Client client, int id, Widget parent, int x, int y, int width, int height) {
        Widget w = mock(Widget.class, RETURNS_SELF);
        when(w.getId()).thenReturn(id); when(w.getIndex()).thenReturn(-1); when(w.getParent()).thenReturn(parent);
        when(client.getWidget(id)).thenReturn(w);
        AtomicInteger px=new AtomicInteger(x), py=new AtomicInteger(y), pw=new AtomicInteger(width), ph=new AtomicInteger(height);
        AtomicInteger xm=new AtomicInteger(), ym=new AtomicInteger(), wm=new AtomicInteger(), hm=new AtomicInteger(), opacity=new AtomicInteger();
        AtomicBoolean block=new AtomicBoolean();
        when(w.getOriginalX()).thenAnswer(i->px.get()); doAnswer(i->{px.set(i.getArgument(0));return w;}).when(w).setOriginalX(anyInt());
        when(w.getOriginalY()).thenAnswer(i->py.get()); doAnswer(i->{py.set(i.getArgument(0));return w;}).when(w).setOriginalY(anyInt());
        when(w.getOriginalWidth()).thenAnswer(i->pw.get()); doAnswer(i->{pw.set(i.getArgument(0));return w;}).when(w).setOriginalWidth(anyInt());
        when(w.getOriginalHeight()).thenAnswer(i->ph.get()); doAnswer(i->{ph.set(i.getArgument(0));return w;}).when(w).setOriginalHeight(anyInt());
        when(w.getRelativeX()).thenAnswer(i->px.get());when(w.getRelativeY()).thenAnswer(i->py.get());
        when(w.getWidth()).thenAnswer(i->pw.get());when(w.getHeight()).thenAnswer(i->ph.get());
        when(w.getXPositionMode()).thenAnswer(i->xm.get()); doAnswer(i->{xm.set(i.getArgument(0));return w;}).when(w).setXPositionMode(anyInt());
        when(w.getYPositionMode()).thenAnswer(i->ym.get()); doAnswer(i->{ym.set(i.getArgument(0));return w;}).when(w).setYPositionMode(anyInt());
        when(w.getWidthMode()).thenAnswer(i->wm.get()); doAnswer(i->{wm.set(i.getArgument(0));return w;}).when(w).setWidthMode(anyInt());
        when(w.getHeightMode()).thenAnswer(i->hm.get()); doAnswer(i->{hm.set(i.getArgument(0));return w;}).when(w).setHeightMode(anyInt());
        when(w.getOpacity()).thenAnswer(i->opacity.get()); doAnswer(i->{opacity.set(i.getArgument(0));return w;}).when(w).setOpacity(anyInt());
        when(w.getNoClickThrough()).thenAnswer(i->block.get()); doAnswer(i->{block.set(i.getArgument(0));return w;}).when(w).setNoClickThrough(anyBoolean());
        when(w.getBounds()).thenAnswer(i->{Rectangle p=parent==null?new Rectangle():parent.getBounds();
            return new Rectangle(p.x+px.get()-(parent==null?0:parent.getScrollX()),p.y+py.get()-(parent==null?0:parent.getScrollY()),pw.get(),ph.get());});
        return w;
    }

    @Test public void oldTabNoLongerReceivesActionsOrBlocksClicksAcrossWidthsAndPositions() {
        for(int width:new int[]{57,65}) for(int offset:new int[]{0,300}) {
            Client client=mock(Client.class);CircularOrbInput input=new CircularOrbInput();
            Widget parent=widget(client,1,null,offset,70,width,34);
            when(parent.getScrollX()).thenReturn(3);when(parent.getScrollY()).thenReturn(2);
            Widget backing=widget(client,2,parent,3,2,width,34),button=widget(client,3,parent,3,2,width,34);
            Widget number=widget(client,4,parent,3,2,width-32,34),blocker=widget(client,5,parent,3,2,width,34);
            parent.setNoClickThrough(true);blocker.setNoClickThrough(true);when(parent.getChildren()).thenReturn(new Widget[]{blocker});
            Rectangle old=button.getBounds(),circle=new Rectangle(old.x+width-34,old.y,34,34);
            assertTrue(old.contains(offset+2,80));input.apply(parent,backing,button,number,circle,true);
            assertEquals(circle.getCenterX(),button.getBounds().x+24+button.getBounds().height/2.0,0);assertEquals(circle,backing.getBounds());assertEquals(circle,blocker.getBounds());
            input.restore(client);input.apply(parent,backing,button,number,circle,false);assertFalse(button.getBounds().contains(offset+2,80));input.restore(client);input.apply(parent,backing,button,number,circle,true);assertFalse(parent.getNoClickThrough());assertTrue(backing.getNoClickThrough());
            assertTrue(button.getBounds().contains(circle.getCenterX(),circle.getCenterY()));
            verify(button,never()).setAction(anyInt(),anyString());verify(button,never()).setHidden(anyBoolean());
            input.restore(client);assertEquals(old,button.getBounds());assertEquals(old,backing.getBounds());assertEquals(old,blocker.getBounds());
            assertTrue(parent.getNoClickThrough());assertFalse(backing.getNoClickThrough());assertTrue(blocker.getNoClickThrough());
        }
    }

    @Test public void restoresPositionModesButPreservesNewerGeometryAndReplacementWidgets() {
        Client client=mock(Client.class);CircularOrbInput input=new CircularOrbInput();
        Widget parent=widget(client,1,null,10,10,57,34),backing=widget(client,2,parent,0,0,57,34),button=widget(client,3,parent,0,0,57,34);
        backing.setXPositionMode(1);backing.setYPositionMode(2);backing.setWidthMode(1);backing.setHeightMode(1);
        input.apply(parent,backing,button,null,new Rectangle(33,10,34,34),true);input.restore(client);
        assertEquals(1,backing.getXPositionMode());assertEquals(2,backing.getYPositionMode());assertEquals(1,backing.getWidthMode());assertEquals(1,backing.getHeightMode());
        input.apply(parent,backing,button,null,new Rectangle(33,10,34,34),true);button.setOriginalX(99);
        Widget replacement=mock(Widget.class);when(client.getWidget(2)).thenReturn(replacement);
        input.restore(client);assertEquals(99,button.getOriginalX());verifyNoInteractions(replacement);

        assertEquals(57,button.getOriginalWidth());
        assertEquals(34,button.getOriginalHeight());
    }

    @Test public void restoresOwnedDynamicBlockerUsingParentAndIndex() {
        Client client=mock(Client.class);CircularOrbInput input=new CircularOrbInput();
        Widget parent=widget(client,1,null,0,0,57,34),backing=widget(client,2,parent,0,0,57,34),blocker=widget(client,1,parent,0,0,57,34);
        when(client.getWidget(1)).thenReturn(parent);when(blocker.getIndex()).thenReturn(4);when(parent.getChild(4)).thenReturn(blocker);
        when(parent.getChildren()).thenReturn(new Widget[]{blocker});blocker.setNoClickThrough(true);
        input.apply(parent,backing,null,null,new Rectangle(23,0,34,34),true);assertEquals(34,blocker.getOriginalWidth());
        input.restore(client);assertEquals(57,blocker.getOriginalWidth());
    }

    @Test public void staleDrawCoordinatesNeverRestoreTheTabHitboxOrDriftOnRepeatedFrames() {
        Client client=mock(Client.class);CircularOrbInput input=new CircularOrbInput();
        Widget parent=widget(client,1,null,300,70,57,34),backing=widget(client,2,parent,0,0,57,34);
        Widget prayer=widget(client,3,parent,0,0,57,34),number=widget(client,4,parent,0,0,25,34);

        when(parent.getBounds()).thenReturn(new Rectangle(100,70,57,34));
        when(backing.getBounds()).thenReturn(new Rectangle(123,70,34,34));
        when(prayer.getBounds()).thenReturn(new Rectangle(100,70,57,34));
        backing.setNoClickThrough(true);when(parent.getChildren()).thenReturn(new Widget[]{backing});
        Rectangle circle=new Rectangle(323,70,34,34);
        for(int frame=0;frame<12;frame++) {
            input.apply(parent,backing,prayer,number,circle,true);
            assertEquals(new Rectangle(circle.x-25,circle.y,circle.width+25,circle.height+2),com.interfacelayout.util.WidgetGeometry.bounds(prayer));
            input.restore(client);input.apply(parent,backing,prayer,number,circle,false);assertFalse(com.interfacelayout.util.WidgetGeometry.bounds(prayer).contains(305,85));input.restore(client);input.apply(parent,backing,prayer,number,circle,true);
            assertEquals(circle,com.interfacelayout.util.WidgetGeometry.bounds(backing));
            input.restore(client);assertEquals(0,prayer.getOriginalX());assertEquals(57,prayer.getOriginalWidth());
            assertEquals(0,backing.getOriginalX());assertEquals(57,backing.getOriginalWidth());
        }
    }
    @Test public void completeCircleRemainsClickableWithInsetNativeButtonsAndScaledMovedParents() {
        for(int diameter:new int[]{26,34,42,52}) for(int parentWidth:new int[]{57,65}) {
            Client client=mock(Client.class);CircularOrbInput input=new CircularOrbInput();
            Widget root=widget(client,10,null,310,110,200,200);
            when(root.getScrollX()).thenReturn(7);when(root.getScrollY()).thenReturn(3);
            Widget parent=widget(client,11,root,20,30,parentWidth,diameter);
            Widget backing=widget(client,12,parent,0,0,parentWidth,diameter);
            Widget button=widget(client,13,parent,parentWidth-diameter+4,4,diameter-8,diameter-8);
            Rectangle old=button.getBounds();
            Rectangle circle=new Rectangle(parent.getBounds().x+parentWidth-diameter,parent.getBounds().y,diameter,diameter);
            for(int[] point:new int[][]{{diameter/2,diameter/2},{0,diameter/2},{diameter-1,diameter/2},{diameter/2,0},{diameter/2,diameter-1},{0,0},{-4,diameter/2}}) {
                int x=circle.x+point[0],y=circle.y+point[1];
                boolean inside=CircularOrbArtwork.contains(circle,x,y);
                input.apply(parent,backing,button,null,circle,inside);
                assertEquals("Native click area must match visible circle",inside,button.getBounds().contains(x,y));
                assertTrue(parent.getBounds().contains(circle));
                assertFalse(parent.getNoClickThrough());
                input.restore(client);assertEquals(old,button.getBounds());
            }
        }
    }

    @Test public void forcedFreePositionsDoNotOffsetTheCircleAndSurviveRestoration() {
        Client client=mock(Client.class);CircularOrbInput input=new CircularOrbInput();
        Widget parent=widget(client,1,null,100,70,57,34),backing=widget(client,2,parent,0,0,57,34),button=widget(client,3,parent,0,0,57,34);
        forced(parent,300,120);forced(backing,0,0);forced(button,0,0);
        Rectangle original=parent.getBounds(),circle=new Rectangle(323,120,34,34);
        for(int i=0;i<5;i++){
            input.apply(parent,backing,button,null,circle,true);
            assertTrue(parent.getBounds().contains(circle));assertEquals(circle.getCenterX(),button.getBounds().x+24+button.getBounds().height/2.0,0);
            input.restore(client);assertEquals(original,parent.getBounds());assertEquals(original,button.getBounds());
        }
        verify(parent,never()).setForcedPosition(anyInt(),anyInt());
    }
    private static void forced(Widget widget,int x,int y) {
        AtomicInteger rx=new AtomicInteger(x),ry=new AtomicInteger(y);
        when(widget.getRelativeX()).thenAnswer(i->rx.get());when(widget.getRelativeY()).thenAnswer(i->ry.get());
        doAnswer(i->{rx.set(i.getArgument(0));return null;}).when(widget).setRelativeX(anyInt());
        doAnswer(i->{ry.set(i.getArgument(0));return null;}).when(widget).setRelativeY(anyInt());
        doAnswer(i->{rx.set(x);ry.set(y);return null;}).when(widget).revalidate();
        when(widget.getBounds()).thenAnswer(i->com.interfacelayout.util.WidgetGeometry.bounds(widget));
    }

}
