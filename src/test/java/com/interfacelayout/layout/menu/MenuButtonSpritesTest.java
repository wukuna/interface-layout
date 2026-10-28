package com.interfacelayout.layout.menu;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import net.runelite.api.Client;
import net.runelite.api.SpritePixels;
import net.runelite.api.widgets.Widget;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class MenuButtonSpritesTest
{
    private final MenuButtonSprites images = new MenuButtonSprites();
    private final Client client = mock(Client.class);
    private final Map<Integer, SpritePixels> overrides = new HashMap<>();

    @Before public void setup() throws Exception
    {
        Field field = MenuButtonSprites.class.getDeclaredField("client");
        field.setAccessible(true); field.set(images, client);
        when(client.getSpriteOverrides()).thenReturn(overrides);
        when(client.getWidgetSpriteOverrides()).thenReturn(new HashMap<>());
    }

    @Test public void croppedShieldRetainsNativeCanvasAndOffsets()
    {
        SpritePixels source = sprite(16, 18, 26, 26, 5, 5, Color.CYAN);
        when(client.getSprites(null, 8605, 0)).thenReturn(new SpritePixels[]{source});
        BufferedImage image = images.getSprite(8605);
        assertEquals(26, image.getWidth()); assertEquals(26, image.getHeight());
        assertEquals(0, image.getRGB(4, 5)); assertEquals(Color.CYAN.getRGB(), image.getRGB(5, 5));
        assertEquals(Color.CYAN.getRGB(), image.getRGB(20, 22)); assertEquals(0, image.getRGB(21, 22));
        assertSame(image, images.getSprite(8605)); verify(source, times(1)).toBufferedImage();
    }

    @Test public void packReplacementAndRemovalInvalidateOnlyTheirOwnImages()
    {
        SpritePixels original = sprite(26, 26, 26, 26, 0, 0, Color.BLACK);
        when(client.getSprites(null, 8605, 0)).thenReturn(new SpritePixels[]{original});
        BufferedImage vanilla = images.getSprite(8605);
        overrides.put(8605, sprite(16, 18, 26, 26, 5, 5, Color.BLUE));
        BufferedImage blue = images.getSprite(8605); assertEquals(Color.BLUE.getRGB(), blue.getRGB(5, 5));
        overrides.put(8605, sprite(16, 18, 26, 26, 5, 5, Color.GREEN));
        assertEquals(Color.GREEN.getRGB(), images.getSprite(8605).getRGB(5, 5));
        overrides.remove(8605); assertSame(vanilla, images.getSprite(8605));
        images.clear(); assertNotNull(images.getSprite(8605)); verify(original, times(2)).toBufferedImage();
    }

    @Test public void standaloneSpritesDoNotChangeExistingWidgetCropBehavior()
    {
        SpritePixels original = sprite(16, 18, 26, 26, 5, 5, Color.CYAN);
        when(client.getSprites(null, 8605, 0)).thenReturn(new SpritePixels[]{original});
        Widget widget = mock(Widget.class); when(widget.getSpriteId()).thenReturn(8605);
        assertEquals(16, images.get(widget).getWidth()); assertEquals(26, images.getSprite(8605).getWidth());
        assertEquals(16, images.get(widget).getWidth());
    }

    @Test public void unavailableSpritesRetryAndInvalidPackDimensionsFailClosed()
    {
        when(client.getSprites(null, 8605, 0)).thenReturn(null);
        assertNull(images.getSprite(8605));
        SpritePixels original = sprite(26, 26, 26, 26, 0, 0, Color.CYAN);
        when(client.getSprites(null, 8605, 0)).thenReturn(new SpritePixels[]{original});
        assertNotNull(images.getSprite(8605));
        SpritePixels invalid = sprite(16, 18, 100000, 26, 5, 5, Color.CYAN);
        overrides.put(8605, invalid); assertNull(images.getSprite(8605)); verify(invalid, never()).toBufferedImage();
        overrides.put(8605, sprite(16, 18, 26, 26, -1, 5, Color.CYAN)); assertNull(images.getSprite(8605));
        overrides.put(8605, sprite(16, 18, 26, 26, 20, 5, Color.CYAN)); assertNull(images.getSprite(8605));
    }

    private static SpritePixels sprite(int width, int height, int maxWidth, int maxHeight, int x, int y, Color color)
    {
        SpritePixels sprite = mock(SpritePixels.class);
        when(sprite.getWidth()).thenReturn(width); when(sprite.getHeight()).thenReturn(height);
        when(sprite.getMaxWidth()).thenReturn(maxWidth); when(sprite.getMaxHeight()).thenReturn(maxHeight);
        when(sprite.getOffsetX()).thenReturn(x); when(sprite.getOffsetY()).thenReturn(y);
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int row = 0; row < height; row++) for (int column = 0; column < width; column++) image.setRGB(column, row, color.getRGB());
        when(sprite.toBufferedImage()).thenReturn(image); return sprite;
    }
}
