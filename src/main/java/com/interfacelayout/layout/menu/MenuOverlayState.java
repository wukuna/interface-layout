package com.interfacelayout.layout.menu;

import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.client.config.ConfigManager;

/** Two plugin-owned layout slots, scoped automatically by RuneLite profile. */
@Singleton
public class MenuOverlayState
{
    private static final String GROUP = "interfacelayout";
    private static final String ACTIVE = "menuOverlayStateActive";
    private static final String[] OPTIONS = {
        "menuLayout", "menuDirection", "menuSpacing", "menuRestoreLogoutTab",
        "allowClicksThroughGaps", "hideTopMenu", "hideBottomMenu", "hideLeftBar", "hideRightBar", "hiddenStones",
        "hideStone0", "hideStone1", "hideStone2", "hideStone3", "hideStone4", "hideStone5", "hideStone6",
        "hideStone7", "hideStone8", "hideStone9", "hideStone10", "hideStone11", "hideStone12", "hideStone13"
    };
    @Inject private ConfigManager configs;

    public void sync(boolean requested)
    {
        boolean active = Boolean.parseBoolean(configs.getConfiguration(GROUP, ACTIVE));
        if (active == requested) return;
        if (requested)
        {
            save("native");
            if (Boolean.parseBoolean(configs.getConfiguration(GROUP, "menuSnapshot_overlay_ready"))) load("overlay");
            else save("overlay");
        }
        else
        {
            save("overlay");
            if (Boolean.parseBoolean(configs.getConfiguration(GROUP, "menuSnapshot_native_ready"))) load("native");
        }
        configs.setConfiguration(GROUP, ACTIVE, Boolean.toString(requested));
    }

    private void save(String slot)
    {
        for (String key : OPTIONS) copy(key, "menuSnapshot_" + slot + "_" + key);
        configs.setConfiguration(GROUP, "menuSnapshot_" + slot + "_ready", "true");
    }

    private void load(String slot)
    {
        for (String key : OPTIONS) copy("menuSnapshot_" + slot + "_" + key, key);
    }

    public void seedMode(String mode)
    {
        String ready = "menuOverlaySeeded_" + mode;
        if (Boolean.parseBoolean(configs.getConfiguration(GROUP, ready))) return;
        for (String pattern : layoutKeys()) copy(pattern.replace("{mode}", mode), pattern.replace("{mode}", mode + "_overlay"));
        configs.setConfiguration(GROUP, ready, "true");
    }

    private void copy(String source, String destination)
    {
        String value = configs.getConfiguration(GROUP, source);
        if (value == null) configs.unsetConfiguration(GROUP, destination);
        else configs.setConfiguration(GROUP, destination, value);
    }

    private static List<String> layoutKeys()
    {
        List<String> keys = new ArrayList<>();
        keys.add("menuSeparateRows_{mode}"); keys.add("menuBottomGroup_{mode}"); keys.add("menuPosition_{mode}");
        for (int i = 0; i < 2; i++) keys.add("menuNativePosition_{mode}_" + i);
        for (int i = 0; i <= 14; i++)
            for (String prefix : new String[]{"menuGroup_", "menuOrder_", "menuHorizontal_", "menuGroupPosition_", "menuGeneration_"})
                keys.add(prefix + "{mode}_" + i);
        return keys;
    }
}
