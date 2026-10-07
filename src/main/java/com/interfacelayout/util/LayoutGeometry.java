package com.interfacelayout.util;

import java.awt.Point;
import java.awt.Rectangle;

public final class LayoutGeometry
{
    private LayoutGeometry() {}
    public static Point clamp(Point point, int width, int height, int canvasWidth, int canvasHeight)
    {
        return new Point(Math.max(0, Math.min(point.x, Math.max(0, canvasWidth - width))),
            Math.max(0, Math.min(point.y, Math.max(0, canvasHeight - height))));
    }
    public static Point snap(Point point, int grid)
    {
        if (grid <= 1) return new Point(point);
        return new Point(Math.round((float) point.x / grid) * grid, Math.round((float) point.y / grid) * grid);
    }
    public static Point relative(Point canvas, Rectangle parent)
    {
        return new Point(canvas.x - parent.x, canvas.y - parent.y);
    }
}
