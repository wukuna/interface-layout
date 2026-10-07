package com.interfacelayout;

import com.interfacelayout.util.LayoutGeometry;
import java.awt.Point;
import java.awt.Rectangle;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class LayoutGeometryTest
{
    @Test public void negativeAndOffscreenPositionsRemainAccessible()
    {
        assertEquals(new Point(0, 450), LayoutGeometry.clamp(new Point(-20, 900), 60, 50, 800, 500));
    }
    @Test public void undersizedCanvasNeverProducesNegativeCoordinates()
    {
        assertEquals(new Point(0, 0), LayoutGeometry.clamp(new Point(100, 100), 60, 50, 20, 10));
    }
    @Test public void canvasPositionConvertsForAnOffsetParent()
    {
        assertEquals(new Point(-490, 115), LayoutGeometry.relative(new Point(10, 120), new Rectangle(500, 5, 200, 200)));
    }
    @Test public void gridRoundsToClosestIntersection()
    {
        assertEquals(new Point(16, 24), LayoutGeometry.snap(new Point(13, 27), 8));
        assertEquals(new Point(13, 27), LayoutGeometry.snap(new Point(13, 27), 1));
    }
    @Test public void resizeClampDoesNotMutateSavedPosition()
    {
        Point saved = new Point(1000, 700);
        assertEquals(new Point(740, 450), LayoutGeometry.clamp(saved, 60, 50, 800, 500));
        assertEquals(new Point(1000, 700), saved);
        assertEquals(saved, LayoutGeometry.clamp(saved, 60, 50, 1600, 900));
    }
}
