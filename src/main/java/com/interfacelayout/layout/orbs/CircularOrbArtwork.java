package com.interfacelayout.layout.orbs;

import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.util.LinkedHashMap;
import java.util.Map;

/** Completes the tab-connected frame with its intact outer half, retaining pack artwork. */
final class CircularOrbArtwork {
    private final Map<Key, BufferedImage> frames = new LinkedHashMap<Key, BufferedImage>(32, .75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<Key, BufferedImage> entry) { return size() > 32; }
    };

    void clear() { frames.clear(); }

    void paint(Graphics2D g, BufferedImage frame, Rectangle backing, Rectangle circle) {
        Key key = new Key(frame, backing, circle);
        BufferedImage image = frames.computeIfAbsent(key, CircularOrbArtwork::create);
        g.drawImage(image, circle.x, circle.y, null);
    }

    private static BufferedImage create(Key key) {
        int w = key.circle.width, h = key.circle.height;
        BufferedImage texture = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D draw = texture.createGraphics();
        try {
            draw.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            draw.drawImage(key.source, -key.circle.x, -key.circle.y, key.width, key.height, null);
        } finally { draw.dispose(); }
        // The left half of the native frame joins the number tab. Its right half
        // is already a complete arc. Mirror only the rim, never the live fill.
        for (int y = 0; y < h; y++) for (int x = 0; x < w / 2; x++)
            texture.setRGB(x, y, texture.getRGB(w - 1 - x, y));
        BufferedImage mask = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        draw = mask.createGraphics();
        try {
            draw.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Area ring = new Area(new Ellipse2D.Double(0, 0, w, h));
            ring.subtract(new Area(new Ellipse2D.Double(4, 4, w - 8, h - 8)));
            draw.fill(ring);
        } finally { draw.dispose(); }
        draw = texture.createGraphics();
        try {
            draw.setComposite(AlphaComposite.DstIn);
            draw.drawImage(mask, 0, 0, null);
        } finally { draw.dispose(); }
        return texture;
    }

    private static final class Key {
        final BufferedImage source;
        final int width, height;
        final Rectangle circle;
        Key(BufferedImage source, Rectangle backing, Rectangle circle) {
            this.source = source; width = backing.width; height = backing.height;
            this.circle = new Rectangle(circle.x - backing.x, circle.y - backing.y, circle.width, circle.height);
        }
        @Override public int hashCode() { return 31 * (31 * System.identityHashCode(source) + width) + height + circle.hashCode(); }
        @Override public boolean equals(Object obj) {
            if (!(obj instanceof Key)) return false;
            Key other = (Key) obj;
            return source == other.source && width == other.width && height == other.height && circle.equals(other.circle);
        }
    }
}
