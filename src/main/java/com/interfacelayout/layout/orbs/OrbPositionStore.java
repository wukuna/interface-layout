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

import com.interfacelayout.layout.orbs.OrbConstants.ConfigGroup;
import com.interfacelayout.layout.orbs.OrbConstants.ConfigKeys;
import com.interfacelayout.layout.orbs.widget.TargetWidget;
import com.interfacelayout.layout.orbs.widget.WidgetManager;
import com.interfacelayout.layout.orbs.widget.elements.Orbs;
import com.interfacelayout.layout.orbs.widget.layout.edit.Binding;
import com.interfacelayout.layout.orbs.widget.layout.edit.BindingManager;
import com.interfacelayout.layout.orbs.widget.layout.edit.EditManager;
import com.interfacelayout.util.ValueKey;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.widgets.Widget;
import net.runelite.client.config.ConfigManager;

@Singleton
public class OrbPositionStore
{
    @Inject private OrbController manager;
    @Inject private WidgetManager widgetManager;
    @Inject private EditManager editManager;
    @Inject private BindingManager bindingManager;
    @Inject private ConfigManager configManager;
public boolean useSavedPosition(Widget widget, int index)
	{
		if (manager.isCompactLayout() || manager.enableOrbSwapping)
		{
			return false;
		}

		if (widget == null)
		{
			return false;
		}

		for (TargetWidget[] targets : EditManager.EDIT_TARGETS)
		{
			boolean classic = targets.length > 1 && manager.isClassicResizable() && targets[1] != null;
			boolean fixed = targets.length > 2 && manager.isFixedMode() && targets[2] != null;

			final TargetWidget target =
				fixed ? targets[2] : classic ? targets[1] : targets[0];

			if (editManager.blockEditing(target))
			{
				continue;
			}

			if (widget.getId() == target.getComponentId()
				&& index == target.getArrayId())
			{
				return true;
			}
		}

		return false;
	}

	public void resetTargetsSavedPosition(Binding binding, boolean remap)
	{
		if (manager.isFixedMode())
		{
			clearSavedPosition(binding.getFixed());
			if (binding.getFixed() == null)
			{
				clearSavedPosition(binding.getModern());
				clearSavedPosition(binding.getRelated());
			}
		}
		else
		{
			clearSavedPosition(binding.getClassic());
			clearSavedPosition(binding.getModern());
			clearSavedPosition(binding.getRelated());
		}

		if (remap)
		{
			TargetWidget target = binding.get(manager);
			if (target == null)
			{
				return;
			}

			widgetManager.remapTargets(target);

			Widget bound = widgetManager.getTargetWidget(target);
			if (bound != null)
			{
				Widget handler = binding.getHandler();
				if (handler != null)
				{
					int x = editManager.setHandlerX(bound, handler.getParent());
					int y = editManager.setHandlerY(bound, handler.getParent());

					binding.getHandler().setOriginalX(x);
					binding.getHandler().setOriginalY(y);
					binding.getHandler().revalidate();

					if (binding.getRelated() != null)
					{
						Widget related = widgetManager.getTargetWidget(binding.getRelated());
						if (related != null) related.setOriginalX(x);
						if (related != null) related.setOriginalY(y);
						if (related != null) related.revalidate();
					}
				}
			}
		}
	}

	private void clearSavedPosition(TargetWidget target)
	{
		if (target == null)
		{
			return;
		}

		String x = getSavedKey(target, ValueKey.X);
		String y = getSavedKey(target, ValueKey.Y);

		configManager.unsetConfiguration(ConfigGroup.GROUP_NAME, x);
		configManager.unsetConfiguration(ConfigGroup.GROUP_NAME, y);
	}

	//remap should only be false for profile swaps / full config resets
	public void resetAllSavedPositions(boolean remap)
	{
		for (Binding binding : bindingManager.all())
		{
			resetTargetsSavedPosition(binding, remap);
		}

		if (!remap)
		{
			//may be unnecessary since bindings are cleared on the next edit-mode
			bindingManager.clear();
		}
	}

	public void resetSavedPositionConfigs()
	{
		String[] prefixes =
			{
				ConfigKeys.CUSTOM_LAYOUT_PREFIX,
				ConfigKeys.VANILLA_LAYOUT_PREFIX,
				ConfigKeys.FIXED_LAYOUT_PREFIX
			};

		for (TargetWidget[] targets : EditManager.EDIT_TARGETS)
		{
			for (TargetWidget target : targets)
			{
				if (target == null)
				{
					continue;
				}

				for (String prefix : prefixes)
				{
					configManager.unsetConfiguration(ConfigGroup.GROUP_NAME,
						buildSavedKey(prefix, target.getComponentId(), target.getArrayId(), ValueKey.X));

					configManager.unsetConfiguration(ConfigGroup.GROUP_NAME,
						buildSavedKey(prefix, target.getComponentId(), target.getArrayId(), ValueKey.Y));
				}
			}
		}
	}

	public int getSavedPosition(Widget widget, int index, ValueKey key)
	{
		if ((manager.isCompactLayout() && !manager.getCurrentLayout().isCustom()))
		{
			return -1;
		}
		else if (widget.getId() == Orbs.WORLD_MAP_CONTAINER.getComponentId())
		{
			if (manager.hideWorldMap)
			{
				return -1;
			}
		}
		else if (widget.getId() == Orbs.LOGOUT_X_ICON.getComponentId() ||
			widget.getId() == Orbs.LOGOUT_X_STONE.getComponentId())
		{
			if (manager.hideLogoutX)
			{
				return -1;
			}
		}

		String configKey = buildSavedKey(getCurrentPrefix(), widget.getId(), index, key);
		Integer value = configManager.getConfiguration(ConfigGroup.GROUP_NAME, configKey, Integer.class);
		return value != null ? value : -1;
	}

	public void saveCurrentLayoutPosition(Widget bound, Binding binding)
	{
		if (bound == null || binding == null)
		{
			return;
		}

		int x = bound.getOriginalX();
		int y = bound.getOriginalY();

		if (manager.isFixedMode())
		{
			savePosition(binding.getFixed(), x, y);
			if (binding.getFixed() == null)
			{
				savePosition(binding.getModern(), x, y);
			}
			return;
		}

		savePosition(binding.getClassic(), x, y);
		savePosition(binding.getModern(), x, y);
		savePosition(binding.getRelated(), x, y);
	}

	private void savePosition(TargetWidget target, int x, int y)
	{
		if (target != null)
		{
			manager.saveConfig(getSavedKey(target, com.interfacelayout.util.ValueKey.X), x);
			manager.saveConfig(getSavedKey(target, com.interfacelayout.util.ValueKey.Y), y);
		}
	}

	private String getSavedKey(TargetWidget target, ValueKey key)
	{
		int id = target.getComponentId();
		int index = target.getArrayId();
		return buildSavedKey(getCurrentPrefix(), id, index, key);
	}

	private String buildSavedKey(String prefix, int componentId, int index, ValueKey key)
	{
		String suffix = key == com.interfacelayout.util.ValueKey.X ? "_x" : "_y";
		String id = componentId + "_" + index;
		return prefix + id + suffix;
	}

	public String getCurrentPrefix()
	{
		return manager.isCompactLayout() ? ConfigKeys.CUSTOM_LAYOUT_PREFIX :
			!manager.isFixedMode() ? ConfigKeys.VANILLA_LAYOUT_PREFIX : ConfigKeys.FIXED_LAYOUT_PREFIX;
	}

	
}