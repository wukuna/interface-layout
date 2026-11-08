package com.interfacelayout.layout.menu;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class MenuIconArtworkTest
{
    @Test public void faithfulContoursKeepCustomShapesColoursPaddingAndHoles()
    {
        BufferedImage source = new BufferedImage(20, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = source.createGraphics();
        graphics.setColor(Color.CYAN); graphics.fillRect(3, 2, 14, 12);
        graphics.setComposite(java.awt.AlphaComposite.Clear); graphics.fillRect(8, 6, 4, 4);
        graphics.setComposite(java.awt.AlphaComposite.SrcOver); graphics.setColor(Color.ORANGE); graphics.fillRect(4, 3, 3, 3);
        graphics.dispose();
        MenuIconArtwork icon = MenuIconArtwork.trace(source);
        for (int scale : new int[]{1, 2, 4})
        {
            BufferedImage result = icon.render(20 * scale, 16 * scale);
            assertEquals(0, result.getRGB(scale, scale));
            assertEquals(0, result.getRGB(10 * scale, 8 * scale));
            assertEquals(Color.CYAN.getRGB(), result.getRGB(15 * scale, 10 * scale));
            assertEquals(Color.ORANGE.getRGB(), result.getRGB(5 * scale, 4 * scale));
        }
        assertEquals(0, source.getRGB(10, 8));
        assertEquals(Color.CYAN.getRGB(), source.getRGB(15, 10));
    }

    @Test public void translucentAndDetailedPackArtworkRetainsItsOriginalPixels()
    {
        BufferedImage source = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) source.setRGB(x, y, 0x8014c8a0);
        BufferedImage enlarged = MenuIconArtwork.trace(source).render(64, 64);
        assertEquals(128, enlarged.getRGB(32, 32) >>> 24);
        Color color = new Color(enlarged.getRGB(32, 32), true);
        assertTrue(Math.abs(color.getGreen() - 200) <= 1);
        assertTrue(Math.abs(color.getBlue() - 160) <= 1);
        for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) source.setRGB(x, y, 0xff000000 | ((y * 16 + x) * 0x010101));
        BufferedImage detailed = MenuIconArtwork.trace(source).render(16, 16);
        assertArrayEquals(source.getRGB(0, 0, 16, 16, null, 0, 16), detailed.getRGB(0, 0, 16, 16, null, 0, 16));
    }

    @Test public void noOpaqueBackdropIsAddedToEmptyOrColouredArtwork()
    {
        BufferedImage empty = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB);
        BufferedImage transparent = MenuIconArtwork.trace(empty).render(32, 32);
        for (int pixel : transparent.getRGB(0, 0, 32, 32, null, 0, 32)) assertEquals(0, pixel);
        empty.setRGB(4, 4, Color.CYAN.getRGB());
        BufferedImage cyan = MenuIconArtwork.trace(empty).render(32, 32);
        for (int pixel : cyan.getRGB(0, 0, 32, 32, null, 0, 32))
            if ((pixel >>> 24) > 64) { assertEquals(0, pixel & 0xff0000); assertTrue((pixel & 0xffff) > 0xf0f0); }
    }

    @Test public void cacheBuildsOffCallerAndKeepsPackReplacementsSeparate()
    {
        List<Runnable> jobs = new ArrayList<>();
        MenuIconCache cache = new MenuIconCache(jobs::add);
        BufferedImage red = solid(Color.RED), blue = solid(Color.BLUE);
        assertNull(cache.get(red, 32, 32));
        assertNull(cache.get(red, 32, 32));
        assertEquals(1, jobs.size());
        red.setRGB(4, 4, Color.GREEN.getRGB());
        assertNull(cache.get(blue, 32, 32));
        jobs.remove(0).run();
        assertEquals(Color.RED.getRGB(), cache.get(red, 32, 32).getRGB(16, 16));
        assertEquals(Color.BLUE.getRGB(), cache.get(blue, 32, 32).getRGB(16, 16));
        assertSame(cache.get(blue, 32, 32), cache.get(blue, 32, 32));
        assertTrue(jobs.isEmpty());
    }

    @Test public void clearDiscardsQueuedImagesAndAllowsImmediateRestart()
    {
        List<Runnable> jobs = new ArrayList<>();
        MenuIconCache cache = new MenuIconCache(jobs::add);
        BufferedImage image = solid(Color.RED);
        cache.get(image, 32, 32); cache.clear();
        image.setRGB(0, 0, 8, 8, solid(Color.GREEN).getRGB(0, 0, 8, 8, null, 0, 8), 0, 8);
        assertNull(cache.get(image, 32, 32));
        jobs.remove(0).run();
        assertEquals(Color.GREEN.getRGB(), cache.get(image, 32, 32).getRGB(16, 16));
        cache.clear(); assertNull(cache.get(image, 32, 32)); assertEquals(1, jobs.size());
    }

    @Test public void cacheBoundsItsSourcesSizesAndPendingWork()
    {
        List<Runnable> jobs = new ArrayList<>();
        MenuIconCache cache = new MenuIconCache(jobs::add);
        BufferedImage first = solid(Color.RED), last = null;
        cache.get(first, 32, 32);
        for (int i = 0; i < 40; i++) { last = solid(Color.BLUE); cache.get(last, 32, 32); }
        assertEquals(1, jobs.size()); jobs.remove(0).run();
        assertEquals(Color.BLUE.getRGB(), cache.get(last, 32, 32).getRGB(16, 16));
        assertNull(cache.get(first, 32, 32)); jobs.remove(0).run();
        for (int size = 16; size <= 20; size++) cache.get(first, size, size);
        jobs.remove(0).run();
        assertNull(cache.get(first, 16, 16));
        assertNull(cache.get(first, 100000, 2)); assertNull(cache.get(first, 0, 2));
        assertEquals(1, jobs.size());
    }

    private static BufferedImage solid(Color color)
    {
        BufferedImage image = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++) image.setRGB(x, y, color.getRGB());
        return image;
    }
}
