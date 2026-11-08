package com.interfacelayout.layout.menu;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Area;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.PathIterator;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

final class MenuIconArtwork
{
    private final BufferedImage original;
    private final Path2D.Double silhouette;
    private final List<Layer> layers;

    private MenuIconArtwork(BufferedImage original, Path2D.Double silhouette, List<Layer> layers)
    {
        this.original = original; this.silhouette = silhouette; this.layers = layers;
    }

    static MenuIconArtwork trace(BufferedImage original)
    {
        int width = original.getWidth(), height = original.getHeight();
        Map<Integer, Area> regions = new TreeMap<>(Comparator.comparingDouble(MenuIconArtwork::luminance));
        if ((long) width * height > 4096) return new MenuIconArtwork(original, null, null);
        for (int y = 0; y < height; y++)
            for (int x = 0; x < width;)
            {
                int color = original.getRGB(x, y), alpha = color >>> 24;
                if (alpha == 0) { x++; continue; }
                if (alpha != 255) return new MenuIconArtwork(original, null, null);
                int start = x++;
                while (x < width && original.getRGB(x, y) == color) x++;
                regions.computeIfAbsent(color & 0xffffff, key -> new Area())
                    .add(new Area(new Rectangle2D.Double(start, y, x - start, 1)));
                if (regions.size() > 64) return new MenuIconArtwork(original, null, null);
            }
        Area remaining = new Area();
        regions.values().forEach(remaining::add);
        Path2D.Double silhouette = contour(remaining);
        List<Layer> layers = new ArrayList<>();
        regions.forEach((color, area) -> {
            layers.add(new Layer(contour(remaining), new Color(color)));
            remaining.subtract(area);
        });
        return new MenuIconArtwork(original, silhouette, layers);
    }

    BufferedImage render(int width, int height)
    {
        BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB_PRE);
        if (layers == null)
        {
            Graphics2D draw = result.createGraphics();
            try
            {
                draw.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                draw.drawImage(original, 0, 0, width, height, null);
            }
            finally { draw.dispose(); }
            return result;
        }
        if (layers.isEmpty()) return result;
        BufferedImage large = new BufferedImage(width * 4, height * 4, BufferedImage.TYPE_INT_ARGB_PRE);
        Graphics2D draw = large.createGraphics();
        try
        {
            draw.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            draw.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            draw.scale(width * 4.0 / original.getWidth(), height * 4.0 / original.getHeight());
            draw.setColor(layers.get(0).color); draw.fill(silhouette); draw.clip(silhouette);
            draw.setStroke(new BasicStroke(.26f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            for (Layer layer : layers)
            {
                draw.setColor(layer.color); draw.draw(layer.shape); draw.fill(layer.shape);
            }
        }
        finally { draw.dispose(); }
        draw = result.createGraphics();
        try
        {
            draw.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            draw.drawImage(large, 0, 0, width, height, null);
        }
        finally { draw.dispose(); }
        return result;
    }

    private static Path2D.Double contour(Area area)
    {
        Path2D.Double result = new Path2D.Double(Path2D.WIND_NON_ZERO);
        double[] coordinates = new double[6];
        List<Point2D.Double> points = new ArrayList<>();
        for (PathIterator iterator = area.getPathIterator(null); !iterator.isDone(); iterator.next())
        {
            int type = iterator.currentSegment(coordinates);
            if (type == PathIterator.SEG_MOVETO) points = new ArrayList<>();
            if (type == PathIterator.SEG_MOVETO || type == PathIterator.SEG_LINETO)
                points.add(new Point2D.Double(coordinates[0], coordinates[1]));
            else if (type == PathIterator.SEG_CLOSE) append(result, simplify(points));
        }
        return result;
    }

    private static List<Point2D.Double> simplify(List<Point2D.Double> points)
    {
        if (points.size() < 5) return points;
        int middle = 0;
        double farthest = 0;
        for (int i = 1; i < points.size(); i++)
        {
            double distance = points.get(0).distanceSq(points.get(i));
            if (distance > farthest) { farthest = distance; middle = i; }
        }
        List<Point2D.Double> first = reduce(points.subList(0, middle + 1));
        List<Point2D.Double> second = new ArrayList<>(points.subList(middle, points.size()));
        second.add(points.get(0)); second = reduce(second);
        first.remove(first.size() - 1); second.remove(second.size() - 1); first.addAll(second);
        return first.size() < 3 ? points : first;
    }

    private static List<Point2D.Double> reduce(List<Point2D.Double> points)
    {
        if (points.size() < 3) return new ArrayList<>(points);
        Point2D.Double first = points.get(0), last = points.get(points.size() - 1);
        int split = 0;
        double farthest = 0;
        for (int i = 1; i < points.size() - 1; i++)
        {
            Point2D.Double point = points.get(i);
            double distance = Line2D.ptSegDist(first.x, first.y, last.x, last.y, point.x, point.y);
            if (distance > farthest) { split = i; farthest = distance; }
        }
        if (farthest <= .48) return new ArrayList<>(Arrays.asList(first, last));
        List<Point2D.Double> result = reduce(points.subList(0, split + 1));
        result.remove(result.size() - 1); result.addAll(reduce(points.subList(split, points.size())));
        return result;
    }

    private static void append(Path2D.Double path, List<Point2D.Double> points)
    {
        int count = points.size();
        if (count < 3) return;
        for (int i = 0; i < count; i++)
        {
            Point2D.Double point = points.get(i);
            Point2D.Double before = along(point, points.get((i + count - 1) % count));
            Point2D.Double after = along(point, points.get((i + 1) % count));
            if (i == 0) path.moveTo(before.x, before.y); else path.lineTo(before.x, before.y);
            path.quadTo(point.x, point.y, after.x, after.y);
        }
        path.closePath();
    }

    private static Point2D.Double along(Point2D.Double start, Point2D.Double end)
    {
        double length = start.distance(end), scale = length == 0 ? 0 : Math.min(.12 / length, .30);
        return new Point2D.Double(start.x + (end.x - start.x) * scale, start.y + (end.y - start.y) * scale);
    }

    private static double luminance(int color)
    {
        return ((color >> 16) & 255) * .299 + ((color >> 8) & 255) * .587 + (color & 255) * .114 + color * 1e-10;
    }

    private static final class Layer
    {
        final Path2D.Double shape;
        final Color color;
        Layer(Path2D.Double shape, Color color) { this.shape = shape; this.color = color; }
    }
}
