package com.interfacelayout.layout.menu;

import java.awt.Color;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import javax.imageio.ImageIO;

public final class MenuIconThemes
{
    public enum Theme
    {
        CURRENT("Current artwork"),
        DARK("Dark Mode - original accents"),
        BLUSH("Pink Mode - blush and pearl"),
        DARK_SUBTLE("Dark Mode - subtle accents");

        private final String label;
        Theme(String label) { this.label = label; }
        @Override public String toString() { return label; }
    }

    enum Icon
    {
        COMBAT("combat", 0xbba262, 168, 774),
        SKILLS("stats", 0xc5a350, 775, 898),
        QUESTS("quests", 0x888bc2, 776, 899),
        INVENTORY("inventory", 0xbba262, 777, 900),
        EQUIPMENT("equipment", 0x7f909d, 778, 901),
        PRAYER("prayer", 0x9aaeb3, 779, 902),
        MAGIC("magic", 0xb63e78, 780, 903),
        ANCIENT("magic_spellbook_ancient_magicks", 0x794371, 1580, 1583),
        LUNAR("magic_spellbook_lunar", 0xe0bbc9, 1581, 1584),
        ARCEUUS("magic_spellbook_arceuus", 0xc96f43, 1708, 1711),
        CHAT("clan_chat", 0xbca351, 781, 904),
        CLAN("clan_channel", 0x8585c2, 2307),
        GUEST_CLAN("guest_clan_channel", 0x78a89b, 2308),
        CHANNEL("Chat-channel", 0x91a7aa, 2303, 2304),
        ACCOUNT("account_management", 0x9689ae, 1709, 1710, 1712, 2305, 2306),
        FRIENDS("friends", 0x91a876, 782, 905),
        IGNORES("ignores", 0xaf7c87, 783, 906),
        SETTINGS("options", 0x769d9b, 785, 908),
        EMOTES("emotes", 0x7fa7a0, 786, 909),
        MUSIC("music", 0xb5a27e, 787, 910),
        DIARY("quests_green_achievement_diaries", 0x91a771, 1298, 1299),
        MINIGAMES("quests_red_minigames", 0xb77973, 1053, 1056, 3563),
        KOUREND("quests_purple_kourend", 0x9b88b7, 1413, 1414),
        RAIDING("quests_brown_raiding_party", 0xad8b68, 1579, 1582),
        SUMMARY("combat_achievements", 0xad8b68, 2309),
        GROUP_IRONMAN("regular_group_ironman", 0x7f9eb7, 3561),
        HARDCORE_IRONMAN("hardcore_group_ironman", 0xb77973, 3562),
        UNRANKED_IRONMAN("unranked_group_ironman", 0x91a771, 3565),
        SAILING("sailing", 0x769d9b, 5808),
        LEAGUES("leagues", 0xbba262, 5810);

        final String resource;
        final int accent;
        final int[] sprites;
        Icon(String resource, int accent, int... sprites)
        { this.resource = resource; this.accent = accent; this.sprites = sprites; }
        boolean book() { return this == MAGIC || this == ANCIENT || this == LUNAR || this == ARCEUUS; }
        boolean medallion()
        { return this == QUESTS || this == DIARY || this == MINIGAMES || this == KOUREND || this == RAIDING || this == SUMMARY; }
    }

    private static final Map<Integer, Icon> SPRITES = new HashMap<>();
    static { for (Icon icon : Icon.values()) for (int sprite : icon.sprites) SPRITES.put(sprite, icon); }
    private final Map<Theme, Map<Icon, BufferedImage>> images = new EnumMap<>(Theme.class);

    public synchronized BufferedImage get(int spriteId, Theme theme)
    {
        if (theme == null || theme == Theme.CURRENT) return null;
        Icon icon = SPRITES.get(spriteId);
        if (icon == null) return null;
        Map<Icon, BufferedImage> themed = images.computeIfAbsent(theme, key -> new EnumMap<>(Icon.class));
        if (!themed.containsKey(icon))
        {
            BufferedImage result = null;
            try (InputStream stream = MenuIconThemes.class.getResourceAsStream("/menu-icons/" + icon.resource + ".png"))
            {
                if (stream != null)
                {
                    BufferedImage original = ImageIO.read(stream);
                    if (original != null) result = recolor(original, icon, theme);
                }
            }
            catch (IOException unavailable) { }
            themed.put(icon, result);
        }
        return themed.get(icon);
    }

    public synchronized void clear() { images.clear(); }

    static BufferedImage recolor(BufferedImage original, Icon icon, Theme theme)
    {
        if (theme == Theme.CURRENT || icon.book() && theme != Theme.BLUSH) return original;
        int width = original.getWidth(), height = original.getHeight();
        Rectangle bounds = visibleBounds(original);
        BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++)
        {
            int pixel = original.getRGB(x, y), alpha = pixel >>> 24;
            if (alpha == 0) continue;
            int rgb = pixel & 0xffffff;
            double luminance = ((rgb >> 16) * .299 + ((rgb >> 8) & 255) * .587 + (rgb & 255) * .114) / 255.0;
            float[] hsb = Color.RGBtoHSB(rgb >> 16, (rgb >> 8) & 255, rgb & 255, null);
            double u = (x - bounds.x + .5) / Math.max(1, bounds.width);
            double v = (y - bounds.y + .5) / Math.max(1, bounds.height);
            if (theme == Theme.DARK)
            {
                if (hsb[1] > .15f) rgb = Color.HSBtoRGB(hsb[0], hsb[1] * .82f, hsb[2] * .94f) & 0xffffff;
            }
            else if (theme == Theme.DARK_SUBTLE)
            {
                boolean accent = accent(icon, u, v, luminance, hsb[0], hsb[1]);
                rgb = accent ? shade(icon.accent, luminance) : ramp(luminance, 0x090b0d, 0x42484d, 0xa4aaae);
            }
            else
            {
                double blushLight = Math.pow(luminance, .55);
                rgb = ramp(blushLight, 0x69364f, 0xd595b2, 0xfff2f5);
                if (icon.book() && hsb[1] > .20f && luminance > .08)
                    rgb = ramp(luminance, blend(0x391e35, icon.accent, .3), icon.accent, blend(icon.accent, 0xffe6ed, .25));
                else if (hsb[1] > .20f && luminance > .08)
                {
                    int tint = hsb[0] > .08f && hsb[0] < .19f ? 0xe6bb87
                        : icon == Icon.DIARY || icon == Icon.EMOTES && hsb[0] > .18f && hsb[0] < .48f ? 0xa6b48a
                        : icon == Icon.QUESTS || icon == Icon.KOUREND ? 0xb6a0cf : 0xd49ab5;
                    rgb = ramp(blushLight, 0x69364f, tint, 0xfff3f5);
                }
            }
            result.setRGB(x, y, alpha << 24 | rgb);
        }
        return result;
    }

    private static boolean accent(Icon icon, double u, double v, double light, float hue, float saturation)
    {
        if (light < .08) return false;
        if (icon.medallion()) return Math.hypot(u - .5, v - .5) < .12;
        switch (icon)
        {
            case COMBAT: return saturation > .25 && v > .52;
            case SKILLS: return saturation > .25 && v < .20;
            case INVENTORY: return saturation > .25;
            case EQUIPMENT: return u > .42 && u < .58 && v > .23 && v < .40;
            case PRAYER: return Math.hypot(u - .5, v - .5) < .09;
            case ACCOUNT: return Math.hypot(u - .73, v - .72) < .10;
            case FRIENDS: case IGNORES: return u > .25 && u < .75 && v > .62 && v < .82 && light < .42;
            case SETTINGS: return u > .37 && u < .60 && v > .39 && v < .63 && light > .20;
            case EMOTES: return hue > .25 && hue < .45 && light > .20 && v > .48;
            case MUSIC: return u > .3 && u < .70 && v > .30 && v < .8 && light > .50;
            case CHAT: case CLAN: case GUEST_CLAN: case CHANNEL:
                return saturation < .30 && light > .15
                    && (u > .35 && v > .07 && v < .19 || u < .57 && v > .48 && v < .60);
            default: return saturation > .2 && u > .35 && u < .65 && v > .35 && v < .65;
        }
    }

    private static int shade(int color, double light)
    { return ramp(light, blend(0x0d1014, color, .25), color, blend(color, 0xe0e2df, .35)); }

    private static int ramp(double light, int shadow, int middle, int highlight)
    { return light < .5 ? blend(shadow, middle, light * 2) : blend(middle, highlight, (light - .5) * 2); }

    private static int blend(int first, int second, double amount)
    {
        int r = (int) Math.round((first >> 16) * (1 - amount) + (second >> 16) * amount);
        int g = (int) Math.round(((first >> 8) & 255) * (1 - amount) + ((second >> 8) & 255) * amount);
        int b = (int) Math.round((first & 255) * (1 - amount) + (second & 255) * amount);
        return r << 16 | g << 8 | b;
    }

    private static Rectangle visibleBounds(BufferedImage image)
    {
        int left = image.getWidth(), top = image.getHeight(), right = -1, bottom = -1;
        for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++)
            if ((image.getRGB(x, y) >>> 24) != 0)
            { left = Math.min(left, x); top = Math.min(top, y); right = Math.max(right, x); bottom = Math.max(bottom, y); }
        return right < left ? new Rectangle(0, 0, image.getWidth(), image.getHeight())
            : new Rectangle(left, top, right - left + 1, bottom - top + 1);
    }
}
