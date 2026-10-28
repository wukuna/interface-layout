package com.interfacelayout.layout.menu;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.SpritePixels;
import net.runelite.api.widgets.Widget;

/** Read RuneLite's public sprite overrides; never read a resource pack's files. */
@Singleton
public class MenuButtonSprites
{
    @Inject private Client client;
    private final Map<Integer, BufferedImage> originals = boundedMap();
    private final Map<Integer, BufferedImage> paddedOriginals = boundedMap();
    private final Map<Integer, OverrideImage> replacements = boundedMap();

    public BufferedImage get(Widget widget)
    {
        if (widget == null) return null;
        int spriteId = widget.getSpriteId();
        SpritePixels replacement = client.getWidgetSpriteOverrides().get(widget.getId());
        if (replacement == null) replacement = client.getSpriteOverrides().get(spriteId);
        if (replacement != null)
        {
            OverrideImage cached = replacements.get(widget.getId());
            // Packs replace SpritePixels on reload. Do not retain the previous
            // pack merely because the sprite ID has not changed.
            if (cached == null || cached.source != replacement)
            {
                cached = new OverrideImage(replacement, replacement.toBufferedImage());
                replacements.put(widget.getId(), cached);
            }
            return cached.image;
        }
        replacements.remove(widget.getId());
        if (spriteId < 0) return null;
        BufferedImage image = originals.get(spriteId);
        if (image == null)
        {
            SpritePixels[] sprites = client.getSprites(client.getIndexSprites(), spriteId, 0);
            if (sprites != null && sprites.length > 0 && sprites[0] != null)
            {
                image = sprites[0].toBufferedImage();
                originals.put(spriteId, image);
            }
        }
        return image;
    }

    /** Resolve an additional native sprite without borrowing another widget's override. */
    public BufferedImage getSprite(int spriteId)
    {
        if (spriteId < 0) return null;
        int key = ~spriteId; // Separate sprite keys from nonnegative packed widget IDs.
        SpritePixels replacement = client.getSpriteOverrides().get(spriteId);
        if (replacement != null)
        {
            OverrideImage cached = replacements.get(key);
            if (cached == null || cached.source != replacement)
            {
                cached = new OverrideImage(replacement, paddedImage(replacement));
                replacements.put(key, cached);
            }
            return cached.image;
        }
        replacements.remove(key);
        BufferedImage image = paddedOriginals.get(spriteId);
        if (image == null)
        {
            SpritePixels[] loaded = client.getSprites(client.getIndexSprites(), spriteId, 0);
            if (loaded != null && loaded.length > 0 && loaded[0] != null)
            {
                image = paddedImage(loaded[0]);
                if (image != null) paddedOriginals.put(spriteId, image);
            }
        }
        return image;
    }

    public void clear() { originals.clear(); paddedOriginals.clear(); replacements.clear(); }

    private static BufferedImage paddedImage(SpritePixels sprite)
    {
        // Native sprites can store a cropped image inside a larger transparent
        // canvas. Scaling that crop would enlarge and misalign the shield icon.
        int width = sprite.getMaxWidth(), height = sprite.getMaxHeight();
        int x = sprite.getOffsetX(), y = sprite.getOffsetY();
        if (width <= 0 || height <= 0 || width > 512 || height > 512
            || x < 0 || y < 0 || (long) x + sprite.getWidth() > width
            || (long) y + sprite.getHeight() > height) return null;
        BufferedImage crop = sprite.toBufferedImage();
        if (crop == null) return null;
        if (x == 0 && y == 0 && crop.getWidth() == width && crop.getHeight() == height) return crop;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try { graphics.drawImage(crop, x, y, null); }
        finally { graphics.dispose(); }
        return image;
    }

    private static <T> Map<Integer, T> boundedMap()
    {
        return new LinkedHashMap<Integer, T>(64, 0.75f, true)
        {
            @Override protected boolean removeEldestEntry(Map.Entry<Integer, T> entry)
            { return size() > 64; }
        };
    }

    private static final class OverrideImage
    {
        final SpritePixels source;
        final BufferedImage image;
        OverrideImage(SpritePixels source, BufferedImage image) { this.source = source; this.image = image; }
    }
}
