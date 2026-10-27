package com.interfacelayout.layout.menu;

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

    public void clear() { originals.clear(); replacements.clear(); }

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
