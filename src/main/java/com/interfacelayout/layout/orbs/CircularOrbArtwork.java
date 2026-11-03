package com.interfacelayout.layout.orbs;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.util.LinkedHashMap;
import java.util.Map;

final class CircularOrbArtwork {
    private final Map<Integer, BufferedImage> frames = new LinkedHashMap<Integer, BufferedImage>(8, .75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<Integer, BufferedImage> entry) { return size() > 8; }
    };

    void clear() { frames.clear(); }

    static Rectangle around(Rectangle content) {
        int diameter = Math.max(content.width, content.height) + 8;
        return new Rectangle(content.x + (content.width - diameter) / 2,
            content.y + (content.height - diameter) / 2, diameter, diameter);
    }

    void paint(Graphics2D g, Rectangle circle) {
        if (circle.width < 10 || circle.width != circle.height) return;
        g.drawImage(frames.computeIfAbsent(circle.width, CircularOrbArtwork::create), circle.x, circle.y, null);
    }

    private static BufferedImage create(int diameter) {

        BufferedImage large = new BufferedImage(diameter * 4, diameter * 4, BufferedImage.TYPE_INT_ARGB);
        Graphics2D draw = large.createGraphics();
        try {
            draw.scale(4, 4);
            draw.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            band(draw, diameter, 0, 4, new Color(18, 23, 27));
            band(draw, diameter, .8, 1.7, new Color(139, 146, 152));
            band(draw, diameter, 1.7, 3, new Color(48, 54, 59));
            band(draw, diameter, 3, 4, new Color(12, 16, 19));
        } finally { draw.dispose(); }
        BufferedImage image = new BufferedImage(diameter, diameter, BufferedImage.TYPE_INT_ARGB);
        draw = image.createGraphics();
        try {
            draw.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            draw.drawImage(large, 0, 0, diameter, diameter, null);
        } finally { draw.dispose(); }
        return image;
    }

    private static void band(Graphics2D g, int diameter, double outside, double inside, Color color) {
        Area ring = new Area(new Ellipse2D.Double(outside, outside, diameter - outside * 2, diameter - outside * 2));
        ring.subtract(new Area(new Ellipse2D.Double(inside, inside, diameter - inside * 2, diameter - inside * 2)));
        g.setColor(color);
        g.fill(ring);
    }
}
