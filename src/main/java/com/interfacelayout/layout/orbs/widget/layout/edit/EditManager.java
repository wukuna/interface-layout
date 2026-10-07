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

package com.interfacelayout.layout.orbs.widget.layout.edit;

import com.interfacelayout.InterfaceLayoutConfig;
import static com.interfacelayout.layout.orbs.OrbConstants.Layout.EDIT_MODE_BACKGROUND_OPACITY;
import static com.interfacelayout.layout.orbs.OrbConstants.Layout.EDIT_MODE_HIDDEN_OPACITY;
import static com.interfacelayout.layout.orbs.OrbConstants.Layout.LOGOUT_X_ICON_OPACITY;
import static com.interfacelayout.layout.orbs.OrbConstants.Layout.ORBS_CONTAINER_OFFSET_Y;
import com.interfacelayout.layout.orbs.OrbConstants.MenuOp;
import static com.interfacelayout.layout.orbs.OrbConstants.MenuOp.RESET_ALL_OP_INDEX;
import com.interfacelayout.layout.orbs.OrbController;
import com.interfacelayout.layout.orbs.widget.TargetWidget;
import com.interfacelayout.layout.orbs.widget.WidgetManager;
import com.interfacelayout.layout.orbs.widget.elements.Button;
import com.interfacelayout.layout.orbs.widget.elements.Compass;
import com.interfacelayout.layout.orbs.widget.elements.Minimap;
import com.interfacelayout.layout.orbs.widget.elements.Orbs;
import com.interfacelayout.layout.orbs.widget.layout.HideOrbConfig;
import com.interfacelayout.layout.orbs.widget.layout.HideOrbRegistry;
import java.util.HashMap;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.widgets.JavaScriptCallback;
import net.runelite.api.widgets.Widget;
import static net.runelite.api.widgets.WidgetConfig.DRAG;
import static net.runelite.api.widgets.WidgetConfig.DRAG_ON;
import net.runelite.api.widgets.WidgetPositionMode;
import net.runelite.api.widgets.WidgetType;
import net.runelite.client.util.ColorUtil;

@Slf4j
@Singleton
public class EditManager
{
	@Inject
	private Client client;

	@Inject
	private WidgetManager widgetManager;

	@Inject
	private OrbController manager;

	@Inject
	private InterfaceLayoutConfig config;

	@Inject
	private DragState dragState;

	@Inject
	private BindingManager bindingManager;

	@Inject
	private HideOrbRegistry hideConfig;

    @Inject private com.interfacelayout.util.NativeOrbStateStore nativeStates;

	private Widget editBackground;
	private Widget blackoutMinimapRight;
	private Widget blackoutMinimapLeft;
	private final Map<TargetWidget, Widget> handlers = new HashMap<>();

	//format: {modern, classic, fixed}
	public static final TargetWidget[][] EDIT_TARGETS =
		{
			//same for all display modes
			{Orbs.HP_ORB_CONTAINER},
			{Orbs.PRAYER_ORB_CONTAINER},
			{Orbs.RUN_ORB_CONTAINER},
			{Orbs.SPEC_ORB_CONTAINER},
			{Orbs.STORE_ORB_CONTAINER},
			{Orbs.ACTIVITY_ORB_CONTAINER},
			{Orbs.WORLD_MAP_CONTAINER},
			{Orbs.WIKI_ICON_CONTAINER},
			{Orbs.XP_DROPS_CONTAINER},

			//not visible in classic
			{Orbs.LOGOUT_X_ICON},

			//each button is different in the respective modes
			{Button.MINIMAP_BUTTON_MODERN, Button.MINIMAP_BUTTON_CLASSIC, Button.MINIMAP_BUTTON_FIXED},

			//compass when in compact-view
			{Minimap.MODERN_MAP_MINIMAP, Minimap.CLASSIC_MAP_MINIMAP}
		};

	public boolean blockEditing(TargetWidget target)
	{
		return (target.isLogoutX() && (manager.isClassicResizable() || manager.isFixedMode())) ||
			(target.isCompass() && !manager.isCompactLayout()) ||
			(target == Orbs.STORE_ORB_CONTAINER && manager.isStoreOrbDisabled()) ||
			(target == Orbs.ACTIVITY_ORB_CONTAINER && manager.isActivityOrbDisabled()) ||
			(target.isWiki() && manager.isWikiBannerDisabled() && !manager.isWikiPluginConfigEnabled());
	}

    public void syncVisibility(String key)
    {
        for (Binding binding : bindingManager.all())
        {
            TargetWidget target = getBoundTarget(binding);
            HideOrbConfig toggle = hideConfig.getByTarget(target);
            if (toggle == null || !toggle.getConfigKey().equals(key)) continue;
            boolean hidden = toggle.getGetter().get();
            binding.setHidden(hidden);
            setupHiddenOrbs(target, hidden);
            widgetManager.setTargetOpacity(target, hidden ? EDIT_MODE_HIDDEN_OPACITY : 0);
            setupHandlerActions(binding.getHandler(), target, toggle, hidden);
        }
    }

	public void toggleEditMode(boolean state)
	{
        if (state == manager.isEditingLayout) return;
        manager.saveConfig("editInterface", state);
        if (config.layout() == com.interfacelayout.layout.orbs.OrbLayout.FREE_POSITION)
        {
            if (!state && !handlers.isEmpty()) { closeEditMode(); return; }
            if (!state) clearEditChildren();
            manager.isEditingLayout = state && manager.isLoggedIn();
            return;
        }

		final Widget parent = widgetManager.getMapParent();
		if (parent == null || !state)
		{
			closeEditMode();
			return;
		}

		enableEditMode(parent);
	}

	private void enableEditMode(Widget parent)
	{
		bindingManager.clear();
		manager.isEditingLayout = true;
		disableMinimap();
		manager.hideWorldMap = false;
		manager.hideLogoutX = false;
		manager.rebuildLayout();
		updateBackground();
		parent.setNoClickThrough(true);

		for (TargetWidget[] targets : EDIT_TARGETS)
		{
			boolean classic = targets.length > 1 && manager.isClassicResizable() && targets[1] != null;
			boolean fixed = targets.length > 2 && manager.isFixedMode() && targets[2] != null;

			final TargetWidget target =
				fixed ? targets[2] : classic ? targets[1] : targets[0];

			if (blockEditing(target))
			{
				continue;
			}

			final Widget bound = widgetManager.getTargetWidget(target);
			if (bound == null || nativeStates.isHiddenByGame(bound))
			{
				continue;
			}

			final Widget handler = widgetManager.createHandler(parent,
				setHandlerX(bound, parent),
				setHandlerY(bound, parent),
				bound.getOriginalWidth(), bound.getOriginalHeight(),
				bound.getXPositionMode(), bound.getYPositionMode(),
				true);

			handlers.put(target, handler);

			final HideOrbConfig toggle = hideConfig.getByTarget(target);
			if (toggle != null)
			{
				bindingManager.bind(
					handler,
					targets[0],
					targets.length > 1 ? targets[1] : null,
					targets.length > 2 ? targets[2] : null,
					target.isLogoutX() ? Orbs.LOGOUT_X_STONE : null,
					toggle.getGetter().get());

				final Binding binding = bindingManager.getByHandler(handler);
				if (binding != null)
				{
					final boolean hidden = binding.isHidden();
					setupHiddenOrbs(target, hidden);
					setupHandlerActions(handler, target, toggle, hidden);

					boolean swapping = manager.enableOrbSwapping;

					if (manager.isCustomLayout()
						|| (swapping && Orbs.isSwappableOrb(bound.getId()))
						|| (!swapping && !manager.isCompactLayout() && !target.isLogoutX()))
					{
						//set draggable
						handler.setClickMask(DRAG | DRAG_ON);
					}
				}
			}
		}

		dragState.boundIndicator = widgetManager.createIndicator(parent);
	}

	private void setupHiddenOrbs(TargetWidget target, boolean isHidden)
	{
		if (!isHidden)
		{
			return;
		}

		widgetManager.setTargetOpacity(target, EDIT_MODE_HIDDEN_OPACITY);

		if (target.isCompass())
		{
			widgetManager.setTargetsHidden(false, Compass.values());
			widgetManager.setTargetsHidden(false,
				Minimap.MODERN_MAP_CONTAINER,
				Minimap.CLASSIC_MAP_CONTAINER);

			if (manager.isCompactLayout())
			{
				manager.compassFrame.setHidden(false);
			}
		}
		else if (target.isWiki())
		{
			manager.updateWikiBannerVisibility(false);
		}
		else
		{
			widgetManager.setHidden(target, false);
		}
	}

	private void setupHandlerActions(Widget handler, TargetWidget target, HideOrbConfig toggle, boolean isHidden)
	{
		handler.setAction(
			MenuOp.HANDLER_TOGGLE_OP_INDEX,
			manager.buildToggleOp(isHidden, toggle.getMenuName()));

		if (!manager.enableOrbSwapping && !manager.isCompactLayout() || manager.isCustomLayout())
		{
			handler.setAction(
				MenuOp.RESET_POSITION_OP_INDEX,
				manager.buildMenuOp(MenuOp.RESET, toggle.getMenuName()));
		}

		if (target.isMinimapButton())
		{
			handler.setAction(
				MenuOp.EDIT_MODE_OP_INDEX,
				manager.buildEditOp(manager.isEditingLayout));

			if (!manager.enableOrbSwapping || manager.isCustomLayout())
			{
				handler.setAction(
					RESET_ALL_OP_INDEX,
					ColorUtil.wrapWithColorTag(MenuOp.RESET_ALL, MenuOp.RED));
			}
		}

		//handle the menu actions: each handler can hide/show, or reset its bound widget
		//the minimap button can also reset all bound widget positions, and disable edit mode respectively
		handler.setOnOpListener((JavaScriptCallback) e ->
		{
			final Binding binding = bindingManager.getByHandler(handler);
			if (binding == null)
			{
				return;
			}

			switch (e.getOp() - 1)
			{
				case MenuOp.HANDLER_TOGGLE_OP_INDEX:
					binding.setHidden(!binding.isHidden());
					handler.setAction(MenuOp.HANDLER_TOGGLE_OP_INDEX, manager.buildToggleOp(binding.isHidden(), toggle.getMenuName()));
					widgetManager.setTargetOpacity(target, binding.isHidden() ? EDIT_MODE_HIDDEN_OPACITY : 0);
					break;

				case MenuOp.RESET_POSITION_OP_INDEX:
					manager.resetTargetsSavedPosition(binding, true);
					break;

				case RESET_ALL_OP_INDEX:
					manager.resetAllSavedPositions(true);
					break;

				case MenuOp.EDIT_MODE_OP_INDEX:
					toggleEditMode(false);
					break;
			}
		});
	}

	//handle whether edit-mode was triggered (save changes) or cancelled (config event, display mode changed, etc)
	private void closeEditMode()
	{
		manager.isEditingLayout = false;

		manager.hideWorldMap = config.hideWorld();
		manager.hideLogoutX = config.hideLogout();

		for (Binding binding : bindingManager.all())
		{
			final TargetWidget target = getBoundTarget(binding);
			final HideOrbConfig toggle = hideConfig.getByTarget(target);
			if (toggle != null)
			{
				if (binding.isHidden() != toggle.getGetter().get())
				{
					if (!manager.isUpdatingProfile)
					{
						manager.saveConfig(toggle.getConfigKey(), binding.isHidden());
					}
				}

				manager.hideOrbByConfig(toggle.getConfigKey());
			}

			widgetManager.setTargetOpacity(target, target.isLogoutX() ? LOGOUT_X_ICON_OPACITY : 0);
		}

		cleanupEditMode();
	}

	//restore the minimap to a clean state post-edit
	private void cleanupEditMode()
	{
		widgetManager.restoreMinimapRendering();
		manager.rebuildLayout();
		clearEditChildren();

		Widget parent = widgetManager.getMapParent();
		if (parent != null)
		{
			parent.setNoClickThrough(manager.isEditingLayout);
		}

		dragState.clear();
	}

	//should probably not do this, but i cba with minimap clicks
	private void disableMinimap()
	{
		widgetManager.removeMinimapRendering();

		if (!manager.isFixedMode())
		{
			return;
		}

		Widget parent = widgetManager.getMinimapMask();
		if (parent == null)
		{
			return;
		}

		//prevent visual artifacts while dragging over the empty space
		blackoutMinimapRight = parent.createChild(-1, WidgetType.RECTANGLE)
			.setOriginalX(10).setOriginalY(0)
			.setOriginalWidth(142).setOriginalHeight(152)
			.setTextColor(0)
			.setFilled(true);
		blackoutMinimapRight.revalidate();

		blackoutMinimapLeft = parent.createChild(-1, WidgetType.RECTANGLE)
			.setOriginalX(0).setOriginalY(36)
			.setOriginalWidth(10).setOriginalHeight(68)
			.setTextColor(0)
			.setFilled(true);
		blackoutMinimapLeft.revalidate();

	}

	//remove handlers, indicators, and background
	public void clearEditChildren()
	{
		for (Widget handler : handlers.values())
		{
			widgetManager.clearChild(handler);
		}

		handlers.clear();

		widgetManager.clearChild(dragState.boundIndicator);
		widgetManager.clearChild(editBackground);
		widgetManager.clearChild(blackoutMinimapRight);
		widgetManager.clearChild(blackoutMinimapLeft);

		dragState.boundIndicator = null;
		editBackground = null;
		blackoutMinimapRight = null;
		blackoutMinimapLeft = null;
	}

	private TargetWidget getBoundTarget(Binding binding)
	{
		return manager.isClassicResizable() && binding.getClassic() != null
			? binding.getClassic()
			: binding.getModern();
	}

	private int getBackgroundParentId()
	{
		return manager.isCompactLayout() || manager.isFixedMode()
			? Minimap.ORBS_UNIVERSE.getComponentId()
			: widgetManager.getMapParent().getId();
	}

	public void updateBackground()
	{
		final Widget parent = client.getWidget(getBackgroundParentId());
		if (parent == null)
		{
			return;
		}

		if (editBackground == null)
		{
			editBackground = parent.createChild(-1, WidgetType.RECTANGLE)
				.setXPositionMode(parent.getXPositionMode())
				.setYPositionMode(parent.getYPositionMode())
				.setWidthMode(parent.getWidthMode())
				.setHeightMode(parent.getHeightMode())
				.setTextColor(0xff0000)
				.setFilled(true)
				.setOpacity(EDIT_MODE_BACKGROUND_OPACITY);
		}

		if (!manager.isCompactLayout())
		{
			editBackground
				.setOriginalX(parent.getOriginalX())
				.setOriginalY(parent.getOriginalY())
				.setOriginalWidth(parent.getOriginalWidth())
				.setOriginalHeight(parent.getOriginalHeight());
		}

		editBackground.revalidate();
	}

	public int setHandlerX(Widget bound, Widget parent)
	{
		int x = bound.getOriginalX();
		if (bound.getParent() != parent)
		{
			if (bound.getXPositionMode() == WidgetPositionMode.ABSOLUTE_RIGHT)
			{
				x = x + (parent.getWidth() - bound.getParent().getWidth());
			}

			x += manager.getLayoutXOffset();
		}

		return x;
	}

	public int setHandlerY(Widget bound, Widget parent)
	{
		int y = bound.getOriginalY();
		if (bound.getParent() != parent)
		{
			if (!manager.isCompactLayout() && !manager.isFixedMode())
			{
				y += ORBS_CONTAINER_OFFSET_Y;
			}

			y += manager.getLayoutYOffset();
		}

		return y;
	}
}
