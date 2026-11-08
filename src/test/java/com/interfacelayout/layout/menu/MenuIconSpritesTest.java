package com.interfacelayout.layout.menu;

import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import org.junit.Test;
import static org.junit.Assert.*;

public class MenuIconSpritesTest
{
    @Test public void bundledIconsPreserveOriginalSilhouettesAndPadding() throws Exception
    {
        MenuIconSprites sprites = new MenuIconSprites();
        for (MenuIconSprites.Icon icon : MenuIconSprites.Icon.values())
        {
            BufferedImage original = original(icon);
            int[] pixels = pixels(original);
            BufferedImage result = sprites.get(icon.sprites[0]);
            assertNotNull(icon.toString(), result);
            assertEquals(original.getWidth(), result.getWidth()); assertEquals(original.getHeight(), result.getHeight());
            int[] colored = pixels(result);
            for (int i = 0; i < pixels.length; i++) assertEquals(icon + " alpha at " + i, pixels[i] >>> 24, colored[i] >>> 24);
            assertSame(result, sprites.get(icon.sprites[0]));
            assertArrayEquals(pixels, pixels(original));
        }
    }

    @Test public void darkIconsRetainAllFourCustomSpellbooksExactly() throws Exception
    {
        MenuIconSprites sprites = new MenuIconSprites();
        for (MenuIconSprites.Icon icon : MenuIconSprites.Icon.values()) if (icon.book())
        {
            assertArrayEquals(pixels(original(icon)), pixels(sprites.get(icon.sprites[0])));
        }
    }

    @Test public void spriteAliasesUseSameArtworkAndUnknownStatesFallBack()
    {
        MenuIconSprites sprites = new MenuIconSprites();
        for (MenuIconSprites.Icon icon : MenuIconSprites.Icon.values())
            for (int sprite : icon.sprites)
                assertSame(sprites.get(icon.sprites[0]), sprites.get(sprite));
        assertNull(sprites.get(-1));
        assertNull(sprites.get(999999));
    }

    @Test public void clearingDoesNotRetainStaleResults()
    {
        MenuIconSprites sprites = new MenuIconSprites();
        BufferedImage dark = sprites.get(780);
        assertSame(dark, sprites.get(780));
        sprites.clear();
        BufferedImage reloaded = sprites.get(780);
        assertNotSame(dark, reloaded); assertArrayEquals(pixels(dark), pixels(reloaded));
    }

    @Test public void recoloringDoesNotMutateSharedSourceOrLosePartialAlpha()
    {
        BufferedImage image = new BufferedImage(3, 3, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(1, 1, 0x80b06030); image.setRGB(2, 1, 0xfff0f0f0);
        BufferedImage recolored = MenuIconSprites.recolor(image, MenuIconSprites.Icon.INVENTORY);
        assertEquals(0x80b06030, image.getRGB(1, 1));
        assertEquals(128, recolored.getRGB(1, 1) >>> 24); assertEquals(0, recolored.getRGB(0, 0));
        assertNotEquals(image.getRGB(1, 1), recolored.getRGB(1, 1));
    }

    static BufferedImage original(MenuIconSprites.Icon icon) throws Exception
    {
        try (java.io.InputStream stream = MenuIconSprites.class.getResourceAsStream("/menu-icons/" + icon.resource + ".png"))
        { assertNotNull(stream); return ImageIO.read(stream); }
    }
    static int[] pixels(BufferedImage image)
    { return image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth()); }
}
