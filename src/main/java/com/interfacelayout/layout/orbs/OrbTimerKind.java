package com.interfacelayout.layout.orbs;

import com.interfacelayout.InterfaceLayoutConfig;
import java.util.function.Predicate;
import net.runelite.api.SpriteID;

enum OrbTimerKind
{
    HP_NEXT("Hitpoints: next restoration", "N", SpriteID.MINIMAP_ORB_HITPOINTS_ICON, InterfaceLayoutConfig::hpNextInfoBox),
    HP_FULL("Hitpoints: full recovery", "F", SpriteID.MINIMAP_ORB_HITPOINTS_ICON, InterfaceLayoutConfig::hpFullInfoBox),
    RUN_FULL("Run energy: full recovery", "F", SpriteID.MINIMAP_ORB_RUN_ICON, InterfaceLayoutConfig::runFullInfoBox),
    RUN_EMPTY("Run energy: time until empty", "E", SpriteID.MINIMAP_ORB_RUN_ICON, InterfaceLayoutConfig::runEmptyInfoBox),
    PRAYER_EMPTY("Prayer: time until empty", "E", SpriteID.MINIMAP_ORB_PRAYER_ICON, InterfaceLayoutConfig::prayerEmptyInfoBox),
    PRAYER_RESTORE("Prayer: next potion restoration", "+", SpriteID.MINIMAP_ORB_PRAYER_ICON, InterfaceLayoutConfig::prayerRegenerationInfoBox),
    SPECIAL_NEXT("Special energy: next 10%", "N", SpriteID.MINIMAP_ORB_SPECIAL_ICON, InterfaceLayoutConfig::specialNextInfoBox),
    SPECIAL_FULL("Special energy: full recovery", "F", SpriteID.MINIMAP_ORB_SPECIAL_ICON, InterfaceLayoutConfig::specialFullInfoBox);

    final String title;
    final String badge;
    final int sprite;
    final Predicate<InterfaceLayoutConfig> enabled;

    OrbTimerKind(String title, String badge, int sprite, Predicate<InterfaceLayoutConfig> enabled)
    {
        this.title = title;
        this.badge = badge;
        this.sprite = sprite;
        this.enabled = enabled;
    }
}
