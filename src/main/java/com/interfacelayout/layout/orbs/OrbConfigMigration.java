/*
 * Copyright (c) 2025, cue <https://github.com/its-cue>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.interfacelayout.layout.orbs;

import com.interfacelayout.InterfaceLayoutConfig.HorizontalAnchor;
import com.interfacelayout.InterfaceLayoutConfig.VerticalAnchor;
import com.interfacelayout.InterfaceLayoutConfig.HotkeyOptions;
import com.interfacelayout.layout.orbs.OrbConstants.ConfigGroup;
import com.interfacelayout.layout.orbs.OrbConstants.ConfigKeys;
import com.interfacelayout.util.MigrateConfig;
import java.util.function.Function;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.Keybind;

@Singleton
public class OrbConfigMigration
{
    @Inject private OrbController manager;
    @Inject private ConfigManager configManager;
public void migrateConfigs()
	{
		Integer version = configManager.getConfiguration(
			ConfigGroup.GROUP_NAME, ConfigKeys.CONFIG_VERSION, Integer.class);

		if (version == null)
		{
			version = 0;
		}

		if (version < ConfigGroup.CONFIG_VERSION)
		{
			migrateConfigs(
				new MigrateConfig<>(
					"hotkeyToggle",
					ConfigKeys.HOTKEY_KEYBIND,
					Keybind.class,
					Function.identity()
				),
				new MigrateConfig<>(
					"hotkeyMinimap",
					ConfigKeys.HOTKEY_TOGGLE_OPTION,
					Boolean.class,
					enabled ->
					{
						if (enabled)
						{
							return HotkeyOptions.MINIMAP;
						}
						return null;
					}
				),
				new MigrateConfig<>(
					"verticalPosition",
					ConfigKeys.HORIZONTAL_ANCHOR,
					HorizontalAnchor.class,
					HorizontalAnchor::name
				),
				new MigrateConfig<>(
					"horizontalPosition",
					ConfigKeys.VERTICAL_ANCHOR,
					VerticalAnchor.class,
					VerticalAnchor::name
				),
				new MigrateConfig<>(
					"enableVerticalHeightOffset",
					ConfigKeys.VERTICAL_Y_ADJUSTMENT,
					Boolean.class,
					enabled ->
					{
						if (enabled && manager.getCurrentLayout().isVertical())
						{
							//max offset possible from the old config
							return 35;
						}
						return null;
					})
			);

			removeOldConfigs();

			manager.saveConfig(ConfigKeys.CONFIG_VERSION, ConfigGroup.CONFIG_VERSION);
		}
	}

	private void migrateConfigs(MigrateConfig<?, ?>... configs)
	{
		for (MigrateConfig<?, ?> config : configs)
		{
			if (config.write(configManager))
			{
				config.unset(configManager);
			}
		}
	}

	private static final String[] REMOVED_KEYS = {
		//removed in 1.1 (d2f5247)
		"hideToggle",

		//removed in 1.7.5 (e60c595)
		"enableWorldMapOverlay",
		"enableXPDropOverlay",

		//removed in 1.9.0 (83a3a2d)
		"hideCompassButton"
	};

	private void removeOldConfigs()
	{
		for (String key : REMOVED_KEYS)
		{
			configManager.unsetConfiguration(ConfigGroup.GROUP_NAME, key);
		}
	}

}