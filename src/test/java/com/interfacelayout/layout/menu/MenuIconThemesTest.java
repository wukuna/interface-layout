package com.interfacelayout.layout.menu;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import javax.imageio.ImageIO;
import org.junit.Test;
import static org.junit.Assert.*;

public class MenuIconThemesTest
{
    @Test public void everyBundledThemePreservesOriginalSilhouettesAndPadding() throws Exception
    {
        MenuIconThemes themes = new MenuIconThemes();
        for (MenuIconThemes.Icon icon : MenuIconThemes.Icon.values())
        {
            BufferedImage original = original(icon);
            int[] pixels = pixels(original);
            for (MenuIconThemes.Theme theme : MenuIconThemes.Theme.values())
            {
                if (theme == MenuIconThemes.Theme.CURRENT) continue;
                BufferedImage result = themes.get(icon.sprites[0], theme);
                assertNotNull(icon + " " + theme, result);
                assertEquals(original.getWidth(), result.getWidth()); assertEquals(original.getHeight(), result.getHeight());
                int[] colored = pixels(result);
                for (int i = 0; i < pixels.length; i++) assertEquals(icon + " alpha at " + i, pixels[i] >>> 24, colored[i] >>> 24);
                assertSame(result, themes.get(icon.sprites[0], theme));
            }
            assertArrayEquals(pixels, pixels(original));
        }
    }

    @Test public void bothDarkThemesRetainAllFourCustomSpellbooksExactly() throws Exception
    {
        MenuIconThemes themes = new MenuIconThemes();
        for (MenuIconThemes.Icon icon : MenuIconThemes.Icon.values()) if (icon.book())
        {
            assertArrayEquals(pixels(original(icon)), pixels(themes.get(icon.sprites[0], MenuIconThemes.Theme.DARK)));
            assertArrayEquals(pixels(original(icon)), pixels(themes.get(icon.sprites[0], MenuIconThemes.Theme.DARK_SUBTLE)));
        }
    }

    @Test public void blushBooksArePinkAndRemainDistinct() throws Exception
    {
        MenuIconThemes themes = new MenuIconThemes();
        Set<Integer> hashes = new HashSet<>();
        for (MenuIconThemes.Icon icon : MenuIconThemes.Icon.values()) if (icon.book())
        {
            BufferedImage pink = themes.get(icon.sprites[0], MenuIconThemes.Theme.BLUSH);
            assertFalse(Arrays.equals(pixels(original(icon)), pixels(pink)));
            hashes.add(Arrays.hashCode(pixels(pink)));
            int colored = 0, rose = 0;
            for (int pixel : pixels(pink)) if ((pixel >>> 24) != 0)
            {
                colored++;
                int r = (pixel >> 16) & 255, g = (pixel >> 8) & 255;
                if (r > g + 10) rose++;
            }
            assertTrue(icon + " should have a blush body", rose > colored * .7);
        }
        assertEquals(4, hashes.size());
    }

    @Test public void pinkBookCoverAccentsHaveStrongerContrastThanTheirBlushBodies() throws Exception
    {
        MenuIconThemes themes = new MenuIconThemes();
        java.util.List<Color> accents = new java.util.ArrayList<>();
        for (MenuIconThemes.Icon icon : new MenuIconThemes.Icon[]{MenuIconThemes.Icon.MAGIC, MenuIconThemes.Icon.ANCIENT, MenuIconThemes.Icon.ARCEUUS})
        {
            int[] source = pixels(original(icon)), pink = pixels(themes.get(icon.sprites[0], MenuIconThemes.Theme.BLUSH));
            int count = 0, red = 0, green = 0, blue = 0; double accentLight = 0, bodyLight = 0; int bodyCount = 0;
            for (int i = 0; i < source.length; i++) if ((source[i] >>> 24) != 0)
            {
                float[] hsb = Color.RGBtoHSB((source[i] >> 16) & 255, (source[i] >> 8) & 255, source[i] & 255, null);
                if (hsb[1] > .2 && hsb[2] > .2)
                {
                    count++; accentLight += ((pink[i] >> 16) & 255) * .299 + ((pink[i] >> 8) & 255) * .587 + (pink[i] & 255) * .114;
                    red += (pink[i] >> 16) & 255; green += (pink[i] >> 8) & 255; blue += pink[i] & 255;
                }
                else if (hsb[1] < .15 && hsb[2] > .3)
                { bodyCount++; bodyLight += ((pink[i] >> 16) & 255) * .299 + ((pink[i] >> 8) & 255) * .587 + (pink[i] & 255) * .114; }
            }
            assertTrue(count > 4 && bodyCount > 4);
            accents.add(new Color(red / count, green / count, blue / count));
            assertTrue(icon + " cover motif should contrast against blush", bodyLight / bodyCount - accentLight / count > 35);
        }
        for (int i = 0; i < accents.size(); i++) for (int j = i + 1; j < accents.size(); j++)
        {
            Color first = accents.get(i), second = accents.get(j);
            double distance = Math.sqrt(Math.pow(first.getRed()-second.getRed(),2)
                + Math.pow(first.getGreen()-second.getGreen(),2) + Math.pow(first.getBlue()-second.getBlue(),2));
            assertTrue("book motifs must have visibly different colors", distance > 40);
        }
    }

    @Test public void subtleDarkReducesColoredAreaWithoutRemovingAccentIdentity()
    {
        MenuIconThemes themes = new MenuIconThemes();
        for (int sprite : new int[]{898, 776, 1299, 3563})
        {
            int original = saturated(themes.get(sprite, MenuIconThemes.Theme.DARK));
            int subtle = saturated(themes.get(sprite, MenuIconThemes.Theme.DARK_SUBTLE));
            assertTrue("accent remains for " + sprite, subtle > 0);
            assertTrue("colored area reduced for " + sprite, subtle < original / 2);
        }
    }

    @Test public void subtleClanAccentsStayOnCapsAndEmoteAccentsStayOnTrousers() throws Exception
    {
        MenuIconThemes themes = new MenuIconThemes();
        BufferedImage clan = themes.get(781, MenuIconThemes.Theme.DARK_SUBTLE);
        int capAccent = 0;
        for (int y = 0; y < clan.getHeight(); y++) for (int x = 0; x < clan.getWidth(); x++)
        {
            int pixel = clan.getRGB(x, y); if ((pixel >>> 24) == 0) continue;
            int spread = spread(pixel);
            if (y >= 10 && y <= 16 || y >= 21 && y <= 29) assertTrue("face stays grey at " + x + "," + y, spread < 25);
            if (spread >= 25) capAccent++;
        }
        assertTrue(capAccent > 0);
        BufferedImage source = original(MenuIconThemes.Icon.EMOTES);
        BufferedImage emote = themes.get(909, MenuIconThemes.Theme.DARK_SUBTLE);
        int trouserAccent = 0;
        for (int y = 0; y < emote.getHeight(); y++) for (int x = 0; x < emote.getWidth(); x++)
        {
            int pixel = emote.getRGB(x, y); if ((pixel >>> 24) == 0) continue;
            if (spread(pixel) >= 25)
            {
                assertEquals("accent is an existing green trouser highlight", 0xff1d5b28, source.getRGB(x, y));
                trouserAccent++;
            }
        }
        assertTrue(trouserAccent > 0);
    }

    private static int spread(int rgb)
    {
        int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
        return Math.max(r, Math.max(g, b)) - Math.min(r, Math.min(g, b));
    }

    @Test public void spriteAliasesUseSameArtworkAndUnknownStatesFallBack()
    {
        MenuIconThemes themes = new MenuIconThemes();
        for (MenuIconThemes.Icon icon : MenuIconThemes.Icon.values())
            for (int sprite : icon.sprites)
                assertSame(themes.get(icon.sprites[0], MenuIconThemes.Theme.BLUSH), themes.get(sprite, MenuIconThemes.Theme.BLUSH));
        assertNull(themes.get(780, MenuIconThemes.Theme.CURRENT));
        assertNull(themes.get(-1, MenuIconThemes.Theme.DARK));
        assertNull(themes.get(999999, MenuIconThemes.Theme.BLUSH));
        assertNull(themes.get(780, null));
    }

    @Test public void clearingAndSwitchingThemesDoNotRetainStaleResults()
    {
        MenuIconThemes themes = new MenuIconThemes();
        BufferedImage dark = themes.get(780, MenuIconThemes.Theme.DARK);
        BufferedImage pink = themes.get(780, MenuIconThemes.Theme.BLUSH);
        assertNotSame(dark, pink);
        assertSame(dark, themes.get(780, MenuIconThemes.Theme.DARK));
        themes.clear();
        BufferedImage reloaded = themes.get(780, MenuIconThemes.Theme.DARK);
        assertNotSame(dark, reloaded); assertArrayEquals(pixels(dark), pixels(reloaded));
    }

    @Test public void recoloringDoesNotMutateSharedSourceOrLosePartialAlpha()
    {
        BufferedImage image = new BufferedImage(3, 3, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(1, 1, 0x80909090); image.setRGB(2, 1, 0xfff0f0f0);
        BufferedImage pink = MenuIconThemes.recolor(image, MenuIconThemes.Icon.INVENTORY, MenuIconThemes.Theme.BLUSH);
        assertEquals(0x80909090, image.getRGB(1, 1));
        assertEquals(128, pink.getRGB(1, 1) >>> 24); assertEquals(0, pink.getRGB(0, 0));
        assertNotEquals(image.getRGB(1, 1), pink.getRGB(1, 1));
    }

    static BufferedImage original(MenuIconThemes.Icon icon) throws Exception
    {
        try (java.io.InputStream stream = MenuIconThemes.class.getResourceAsStream("/menu-icons/" + icon.resource + ".png"))
        { assertNotNull(stream); return ImageIO.read(stream); }
    }
    static int[] pixels(BufferedImage image)
    { return image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth()); }
    private static int saturated(BufferedImage image)
    {
        int count = 0;
        for (int pixel : pixels(image)) if ((pixel >>> 24) != 0)
        {
            float[] hsb = Color.RGBtoHSB((pixel >> 16) & 255, (pixel >> 8) & 255, pixel & 255, null);
            if (hsb[1] > .22 && hsb[2] > .15) count++;
        }
        return count;
    }
}
