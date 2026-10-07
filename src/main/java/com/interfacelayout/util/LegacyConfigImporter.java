package com.interfacelayout.util;

import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.client.config.ConfigManager;

/** Copies existing values once per profile; never changes the source plugin configurations. */
@Singleton
public class LegacyConfigImporter
{
    @Inject private ConfigManager configs;
    public void importOnce()
    {
        if (Boolean.TRUE.equals(configs.getConfiguration("interfacelayout", "legacyImported", Boolean.class))) return;
        copyGroup("compactorbs");
        copyGroup("menustoneshider");
        configs.setConfiguration("interfacelayout", "legacyImported", true);
    }
    private void copyGroup(String group)
    {
        String prefix = group + ".";
        for (String fullKey : configs.getConfigurationKeys(prefix))
        {
            String key = fullKey.substring(prefix.length());
            if (configs.getConfiguration("interfacelayout", key) != null) continue;
            String value = configs.getConfiguration(group, key);
            if (value != null) configs.setConfiguration("interfacelayout", key, value);
        }
    }
}
