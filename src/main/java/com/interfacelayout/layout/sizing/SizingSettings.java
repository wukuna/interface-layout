package com.interfacelayout.layout.sizing;

import com.interfacelayout.InterfaceLayoutConfig;
import javax.inject.Inject;
import javax.inject.Singleton;

/** User-selected shrink factors. Only explicit outer HUD controls have sizing settings. */
@Singleton
public class SizingSettings
{
    @Inject private InterfaceLayoutConfig config;
    public static int percent(int value) { return value == 0 ? 100 : Math.max(50, Math.min(100, value)); }
    public static int combine(int group, int individual) { return Math.max(50, (percent(group) * percent(individual) + 50) / 100); }
    public static int pixels(int value, int percent) { return value <= 0 ? 0 : Math.max(1, (int) Math.round(value * percent / 100.0)); }
    public static int offset(int value, int percent) { return (int) Math.round(value * percent / 100.0); }
    public int stone(int index)
    {
        int individual;
        switch (index)
        {
            case 0: individual = config.sizeStoneCombat(); break;
            case 1: individual = config.sizeStoneSkills(); break;
            case 2: individual = config.sizeStoneQuests(); break;
            case 3: individual = config.sizeStoneInventory(); break;
            case 4: individual = config.sizeStoneEquipment(); break;
            case 5: individual = config.sizeStonePrayer(); break;
            case 6: individual = config.sizeStoneSpellbook(); break;
            case 7: individual = config.sizeStoneClan(); break;
            case 8: individual = config.sizeStoneAccount(); break;
            case 9: individual = config.sizeStoneFriends(); break;
            case 10: individual = config.sizeStoneLogout(); break;
            case 11: individual = config.sizeStoneSettings(); break;
            case 12: individual = config.sizeStoneEmotes(); break;
            case 13: individual = config.sizeStoneMusic(); break;
            default: return 100;
        }
        return combine(config.menuGroupSize(), individual);
    }
    public int orb(int index)
    {
        int individual;
        switch (index)
        {
            case 0: individual = config.sizeOrbHp(); break;
            case 1: individual = config.sizeOrbPrayer(); break;
            case 2: individual = config.sizeOrbRun(); break;
            case 3: individual = config.sizeOrbSpec(); break;
            case 4: individual = config.sizeOrbXp(); break;
            case 5: individual = config.sizeOrbStore(); break;
            case 6: individual = config.sizeOrbActivity(); break;
            case 7: individual = config.sizeOrbMap(); break;
            case 8: individual = config.sizeOrbWiki(); break;
            default: return 100;
        }
        return combine(config.orbGroupSize(), individual);
    }
}
