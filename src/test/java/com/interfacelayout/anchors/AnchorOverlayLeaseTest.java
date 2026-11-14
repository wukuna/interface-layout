package com.interfacelayout.anchors;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Point;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Collection;
import net.runelite.client.ui.overlay.*;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class AnchorOverlayLeaseTest
{
    private static final class Control extends Overlay
    {
        int validations,resizes;
        Control(){setPosition(OverlayPosition.DYNAMIC);setMovable(true);setSnappable(true);setLayer(OverlayLayer.UNDER_WIDGETS);}
        void mobility(boolean movable,boolean snappable){setMovable(movable);setSnappable(snappable);}
        @Override public Dimension render(Graphics2D g){return new Dimension(34,34);}
        @Override public void revalidate(){validations++;}
        @Override public void setPreferredSize(Dimension size){resizes++;super.setPreferredSize(size);}
    }
    private OverlayManager manager()throws Exception {
        Constructor<?> constructor=OverlayManager.class.getDeclaredConstructors()[0];constructor.setAccessible(true);Class<?>[] types=constructor.getParameterTypes();Object[] args=new Object[types.length];for(int i=0;i<args.length;i++)args[i]=mock(types[i]);return (OverlayManager)constructor.newInstance(args);
    }
    private Collection<?> under(OverlayManager manager)throws Exception {Method method=OverlayManager.class.getDeclaredMethod("getLayer",OverlayLayer.class);method.setAccessible(true);return (Collection<?>)method.invoke(manager,OverlayLayer.UNDER_WIDGETS);}
    @Test public void repeatedOwnedMovesPreserveLayersSizesAndOtherOverlayValidation()throws Exception
    {
        OverlayManager manager=manager();Control a=new Control(),b=new Control();manager.add(a);manager.add(b);int old=b.validations;
        a.setPreferredLocation(new Point(55,66));int resizes=a.resizes;assertTrue(a.isMovable());
        manager.saveOverlay(a);assertFalse(under(manager).contains(a));
        OverlayLease lease=new OverlayLease(a,a::mobility,manager);for(int i=0;i<100;i++)lease.move(new Point(i,10));
        assertTrue(under(manager).contains(a));assertFalse(a.isMovable());assertEquals(old,b.validations);assertEquals(resizes,a.resizes);
        lease.release(false);assertTrue(a.isMovable());assertTrue(a.isSnappable());assertEquals(new Point(55,66),a.getPreferredLocation());assertEquals(OverlayPosition.DYNAMIC,a.getPosition());
    }
    @Test public void profilePreparationRestoresMovabilityBeforeRuneLiteLoadsNewPreferences()throws Exception
    {
        OverlayManager manager=manager();Control overlay=new Control();manager.add(overlay);overlay.setPreferredLocation(new Point(10,20));
        OverlayLease lease=new OverlayLease(overlay,overlay::mobility,manager);lease.move(new Point(300,400));
        OverlayLease.beforeProfileChange();assertTrue(overlay.isMovable());assertEquals(new Point(10,20),overlay.getPreferredLocation());
        overlay.setPreferredLocation(new Point(70,80));lease.release(false);assertEquals(new Point(70,80),overlay.getPreferredLocation());
    }
}
