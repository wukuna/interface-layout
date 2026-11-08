package com.interfacelayout.layout.menu;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import javax.imageio.ImageIO;

public final class MenuIconSprites
{
    enum Icon
    {
        COMBAT("combat", 168, 774),
        SKILLS("stats", 775, 898),
        QUESTS("quests", 776, 899),
        INVENTORY("inventory", 777, 900),
        EQUIPMENT("equipment", 778, 901),
        PRAYER("prayer", 779, 902),
        MAGIC("magic", 780, 903),
        ANCIENT("magic_spellbook_ancient_magicks", 1580, 1583),
        LUNAR("magic_spellbook_lunar", 1581, 1584),
        ARCEUUS("magic_spellbook_arceuus", 1708, 1711),
        CHAT("clan_chat", 781, 904),
        CLAN("clan_channel", 2307),
        GUEST_CLAN("guest_clan_channel", 2308),
        CHANNEL("Chat-channel", 2303, 2304),
        ACCOUNT("account_management", 1709, 1710, 1712, 2305, 2306),
        FRIENDS("friends", 782, 905),
        IGNORES("ignores", 783, 906),
        SETTINGS("options", 785, 908),
        EMOTES("emotes", 786, 909),
        MUSIC("music", 787, 910),
        DIARY("quests_green_achievement_diaries", 1298, 1299),
        MINIGAMES("quests_red_minigames", 1053, 1056, 3563),
        KOUREND("quests_purple_kourend", 1413, 1414),
        RAIDING("quests_brown_raiding_party", 1579, 1582),
        SUMMARY("combat_achievements", 2309),
        GROUP_IRONMAN("regular_group_ironman", 3561),
        HARDCORE_IRONMAN("hardcore_group_ironman", 3562),
        UNRANKED_IRONMAN("unranked_group_ironman", 3565),
        SAILING("sailing", 5808),
        LEAGUES("leagues", 5810);

        final String resource;
        final int[] sprites;
        Icon(String resource, int... sprites)
        { this.resource = resource; this.sprites = sprites; }
        boolean book() { return this == MAGIC || this == ANCIENT || this == LUNAR || this == ARCEUUS; }
    }

    private static final Map<Integer, Icon> SPRITES = new HashMap<>();
    static { for (Icon icon : Icon.values()) for (int sprite : icon.sprites) SPRITES.put(sprite, icon); }
    private final Map<Icon, BufferedImage> images = new EnumMap<>(Icon.class);

    public synchronized BufferedImage get(int spriteId)
    {
        Icon icon = SPRITES.get(spriteId);
        if (icon == null) return null;
        if (!images.containsKey(icon))
        {
            BufferedImage result = null;
            try (InputStream stream = MenuIconSprites.class.getResourceAsStream("/menu-icons/" + icon.resource + ".png"))
            {
                if (stream != null)
                {
                    BufferedImage original = ImageIO.read(stream);
                    if (original != null) result = recolor(original, icon);
                }
            }
            catch (IOException unavailable) { }
            images.put(icon, result);
        }
        return images.get(icon);
    }

    public synchronized void clear() { images.clear(); }

    static BufferedImage recolor(BufferedImage original, Icon icon)
    {
        if (icon.book()) return original;
        int width = original.getWidth(), height = original.getHeight();
        BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++)
        {
            int pixel = original.getRGB(x, y), alpha = pixel >>> 24;
            if (alpha == 0) continue;
            int rgb = pixel & 0xffffff;
            float[] hsb = Color.RGBtoHSB(rgb >> 16, (rgb >> 8) & 255, rgb & 255, null);
            if (hsb[1] > .15f) rgb = Color.HSBtoRGB(hsb[0], hsb[1] * .82f, hsb[2] * .94f) & 0xffffff;
            result.setRGB(x, y, alpha << 24 | rgb);
        }
        return result;
    }
}
