package com.interfacelayout.layout.orbs;

import com.interfacelayout.InterfaceLayoutConfig.OrbRim;
import java.awt.AlphaComposite;
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
    private static final Map<BufferedImage, BufferedImage> icons = new LinkedHashMap<BufferedImage, BufferedImage>(32, .75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<BufferedImage, BufferedImage> entry) { return size() > 32; }
    };
    static BufferedImage content(BufferedImage image) {
        return icons.computeIfAbsent(image, source -> {
            int left=source.getWidth(),top=source.getHeight(),right=-1,bottom=-1;
            if(left>512 || top>512)return source;
            for(int y=0;y<source.getHeight();y++)for(int x=0;x<source.getWidth();x++)
                if((source.getRGB(x,y)>>>24)!=0) {
                    left=Math.min(left,x);top=Math.min(top,y);right=Math.max(right,x);bottom=Math.max(bottom,y);
                }
            return right<left?source:source.getSubimage(left,top,right-left+1,bottom-top+1);
        });
    }
    private final Map<Integer, BufferedImage> frames = new LinkedHashMap<Integer, BufferedImage>(8, .75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<Integer, BufferedImage> entry) { return size() > 8; }
    };

    private final Map<BufferedImage, Rectangle> fillBounds = new LinkedHashMap<BufferedImage, Rectangle>(8, .75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<BufferedImage, Rectangle> entry) { return size() > 8; }
    };

    private OrbRim rim = OrbRim.SILVER;

    void clear() { frames.clear(); fillBounds.clear(); }

    static Rectangle around(Rectangle content) {
        int diameter = Math.max(content.width, content.height) + 8;
        return new Rectangle(content.x + (content.width - diameter) / 2,
            content.y + (content.height - diameter) / 2, diameter, diameter);
    }

    Rectangle aroundFill(Rectangle content, BufferedImage image) {
        Rectangle circle = around(content);
        if (image == null || image.getWidth() > 512 || image.getHeight() > 512) return circle;
        Rectangle visible = visibleFill(image);
        circle.x = (int)Math.round(content.x + visible.getCenterX() * content.width / image.getWidth() - circle.width / 2.0);
        circle.y = (int)Math.round(content.y + visible.getCenterY() * content.height / image.getHeight() - circle.height / 2.0);
        return circle;
    }

    Rectangle insetFill(Rectangle content, BufferedImage image) {
        double cx = content.getCenterX(), cy = content.getCenterY();
        double width = content.width, height = content.height;
        if (image != null && image.getWidth() <= 512 && image.getHeight() <= 512) {
            Rectangle visible = visibleFill(image);
            cx = content.x + visible.getCenterX() * content.width / image.getWidth();
            cy = content.y + visible.getCenterY() * content.height / image.getHeight();
            width = (double)visible.width * content.width / image.getWidth();
            height = (double)visible.height * content.height / image.getHeight();
        }
        int diameter = Math.max(0, (int)Math.floor(Math.min(width, height)) - 2);
        return new Rectangle((int)Math.round(cx - diameter / 2.0), (int)Math.round(cy - diameter / 2.0), diameter, diameter);
    }

    private Rectangle visibleFill(BufferedImage image) {
        return fillBounds.computeIfAbsent(image, source -> {
            int left=source.getWidth(),top=source.getHeight(),right=-1,bottom=-1;
            for(int y=0;y<source.getHeight();y++)for(int x=0;x<source.getWidth();x++)
                if((source.getRGB(x,y)>>>24)!=0) {
                    left=Math.min(left,x);top=Math.min(top,y);right=Math.max(right,x);bottom=Math.max(bottom,y);
                }
            return right<left ? new Rectangle(0,0,source.getWidth(),source.getHeight())
                : new Rectangle(left,top,right-left+1,bottom-top+1);
        });
    }

    static boolean contains(Rectangle circle, int x, int y) {
        if (circle.width <= 0 || circle.height <= 0) return false;
        double dx = (x + .5 - circle.getCenterX()) / (circle.width / 2.0);
        double dy = (y + .5 - circle.getCenterY()) / (circle.height / 2.0);
        return dx * dx + dy * dy <= 1;
    }

    void paintBackground(Graphics2D graphics, Rectangle circle) {
        Graphics2D draw = (Graphics2D) graphics.create();
        try {
            draw.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            draw.setColor(new Color(14, 20, 24));
            draw.fill(new Ellipse2D.Double(circle.x + 2, circle.y + 2, circle.width - 4, circle.height - 4));
        } finally { draw.dispose(); }
    }

    void paint(Graphics2D g, Rectangle circle, OrbRim style) {
        if (style == OrbRim.NONE || circle.width < 10 || circle.width != circle.height) return;
        if (rim != style) {
            rim = style;
            frames.clear();
        }
        g.drawImage(frames.computeIfAbsent(circle.width, diameter -> create(diameter, style)), circle.x, circle.y, null);
    }

    private static BufferedImage create(int diameter, OrbRim style) {

        BufferedImage large = new BufferedImage(diameter * 8, diameter * 8, BufferedImage.TYPE_INT_ARGB_PRE);
        Graphics2D draw = large.createGraphics();
        try {
            draw.scale(8, 8);
            draw.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            band(draw, diameter, 0, 4, new Color(18, 23, 27));
            band(draw, diameter, .75, 2, style == OrbRim.DARK_GREY ? new Color(82, 86, 90) : new Color(139, 146, 152));
            band(draw, diameter, 2, 3, style == OrbRim.DARK_GREY ? new Color(38, 42, 46) : new Color(55, 61, 66));
            band(draw, diameter, 3, 4, new Color(12, 16, 19));
        } finally { draw.dispose(); }
        for (int size = large.getWidth() / 2; size >= diameter; size /= 2) {
            BufferedImage reduced = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB_PRE);
            draw = reduced.createGraphics();
            try {
                draw.setComposite(AlphaComposite.Src);
                draw.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                draw.drawImage(large, 0, 0, size, size, null);
            } finally { draw.dispose(); }
            large = reduced;
        }
        return large;
    }

    private static void band(Graphics2D g, int diameter, double outside, double inside, Color color) {
        Area ring = new Area(new Ellipse2D.Double(outside, outside, diameter - outside * 2, diameter - outside * 2));
        ring.subtract(new Area(new Ellipse2D.Double(inside, inside, diameter - inside * 2, diameter - inside * 2)));
        g.setColor(color);
        g.fill(ring);
    }
}
