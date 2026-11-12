package com.interfacelayout.util;

import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.client.config.ConfigManager;

@Singleton
public class LayoutPositionStore
{
    private static final String GROUP = "interfacelayout";
    @Inject private Client client;
    @Inject private ConfigManager configs;

    public Point load(String key, int width, int height)
    {
        Point point = configs.getConfiguration(GROUP, key, Point.class);
        if (point == null) return null;
        Dimension canvas = canvas();
        Dimension reference = configs.getConfiguration(GROUP, key + "_viewport", Dimension.class);
        if (reference == null && canvas.width > 0 && canvas.height > 0
            && new Rectangle(canvas).contains(new Rectangle(point, new Dimension(width, height))))
        {
            reference = canvas;
            configs.setConfiguration(GROUP, key + "_viewport", reference);
        }
        if (reference == null || reference.width <= 0 || reference.height <= 0) return new Point(point);
        return new Point(adapt(point.x, reference.width, canvas.width), adapt(point.y, reference.height, canvas.height));
    }

    public void save(String key, Point point)
    {
        Dimension canvas = canvas();
        if (canvas.width <= 0 || canvas.height <= 0) return;
        configs.setConfiguration(GROUP, key, new Point(point));
        configs.setConfiguration(GROUP, key + "_viewport", canvas);
    }

    public void reset(String key)
    {
        configs.unsetConfiguration(GROUP, key);
        configs.unsetConfiguration(GROUP, key + "_viewport");
    }

    private Dimension canvas() { return new Dimension(client.getCanvasWidth(), client.getCanvasHeight()); }

    private static int adapt(int point, int reference, int canvas)
    {
        if (canvas <= 0) return point;
        if (point > reference * .55) return point + canvas - reference;
        return point < reference * .45 ? point : point + canvas / 2 - reference / 2;
    }
}
