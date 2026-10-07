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
import com.interfacelayout.InterfaceLayoutConfig;

import com.interfacelayout.InterfaceLayoutConfig.TogglePlacement;
import com.interfacelayout.layout.orbs.OrbConstants.ConfigGroup;
import com.interfacelayout.layout.orbs.OrbConstants.ConfigKeys;
import com.interfacelayout.layout.orbs.OrbConstants.Layout;
import com.interfacelayout.layout.orbs.OrbConstants.MenuOp;
import com.interfacelayout.layout.orbs.OrbConstants.Script;
import com.interfacelayout.layout.orbs.OrbConstants.Sprite;
import com.interfacelayout.layout.orbs.OrbConstants.VarClient;
import com.interfacelayout.layout.orbs.OrbConstants.Varbit;
import com.interfacelayout.layout.orbs.OrbConstants.Widgets.Classic;
import com.interfacelayout.layout.orbs.OrbConstants.Widgets.MinimapOverlay;
import com.interfacelayout.layout.orbs.OrbConstants.Widgets.Modern;
import com.interfacelayout.util.ValueKey;
import com.interfacelayout.layout.orbs.widget.TargetWidget;
import com.interfacelayout.layout.orbs.widget.WidgetManager;
import com.interfacelayout.layout.orbs.widget.elements.Button;
import com.interfacelayout.layout.orbs.widget.elements.Compass;
import com.interfacelayout.layout.orbs.widget.elements.Minimap;
import com.interfacelayout.layout.orbs.widget.elements.Orbs;
import com.interfacelayout.layout.orbs.widget.layout.HideOrbConfig;
import com.interfacelayout.layout.orbs.widget.layout.HideOrbRegistry;
import com.interfacelayout.layout.orbs.widget.layout.edit.Binding;
import com.interfacelayout.layout.orbs.widget.layout.edit.BindingManager;
import com.interfacelayout.layout.orbs.widget.layout.edit.DragState;
import com.interfacelayout.layout.orbs.widget.layout.edit.EditManager;
import com.interfacelayout.layout.orbs.widget.layout.slot.SlotManager;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Menu;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.Point;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.JavaScriptCallback;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetPositionMode;
import net.runelite.api.widgets.WidgetSizeMode;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatColorType;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.util.ColorUtil;

@Slf4j
@Singleton
public class OrbController
{
    @Inject private OrbPositionStore positions;
    @Inject private OrbConfigMigration migration;
    @Inject private com.interfacelayout.util.NativeOrbStateStore nativeStates;
    @Inject private com.interfacelayout.layout.GameframeCoordinator gameframe;
	@Inject
	private ChatMessageManager chatMessageManager;

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ConfigManager configManager;

	@Inject
	private InterfaceLayoutConfig config;

	@Inject
	private WidgetManager widgetManager;

	@Inject
	private SlotManager slotManager;

	@Inject
	private HideOrbRegistry orbHidden;

	@Inject
	private EditManager editManager;

	@Inject
	private DragState dragState;

	@Inject
	private BindingManager bindingManager;

	public boolean wikiPluginBannerExists;

	public boolean snapCornerRepositioned;
	public boolean isUpdatingProfile;
	public volatile boolean isEditingLayout;
	public boolean hideWorldMap;
	public boolean hideLogoutX;
	public boolean enableNoClickThrough;
	public boolean enableOrbSwapping;
	public boolean hasSeenWikiWarning;
	public boolean isLoggingIn;
	public boolean isCutsceneActive;
	public boolean detachedMinimapParentIsReady;

	public Widget compassFrame;
	public Widget minimapButton;
	private Widget overlayCompass;
	private Widget overlayCompassLayer;
	private Widget overlayCompassNoClick;
	private Widget overlayCompassMenuOp;
	private Widget overlayMinimap;
	private Widget overlayMinimapFrame;
	private Widget overlayLogoutXStone;
	private Widget overlayLogoutXIcon;
	public volatile boolean active;
    private final java.util.List<Widget> overlayNoClickChildren = new java.util.ArrayList<>();
    private final Map<TargetWidget, Widget> noClickThroughChildren = new HashMap<>();

	//update on startup, onWidgetLoaded, and onScriptPostFired
	public void update(int scriptId)
	{
		createCustomChildren();

		if (scriptId == Script.FORCE_UPDATE)
		{
			rebuildLayout();
			return;
		}

		widgetManager.remapTargetsByScriptId(scriptId, Orbs.values());
		updateNoClickThrough();
	}

	//rebuild the layout from state/config
	public void rebuildLayout()
	{
		boolean swapping = getCurrentLayout() != OrbLayout.FREE_POSITION && config.enableOrbSwapping();
		if (swapping != enableOrbSwapping)
		{
			enableOrbSwapping = swapping;
			slotManager.update();
		}
		gameframe.prepareNativeLayout();
		if (getCurrentLayout() == OrbLayout.FREE_POSITION)
		{
			nativeStates.restore();
			hideAllOrbsByConfig();
			createCustomChildren();
			updateCustomChildren();
			updateNoClickThrough();
			gameframe.refresh();
			return;
		}
		updateCustomChildren();

		if (isFixedMode())
		{
			widgetManager.setHidden(MinimapOverlay.UNIVERSE, true);
			widgetManager.remapTargets(Orbs.values());
			updateNoClickThrough();
			gameframe.refresh();
			return;
		}

		setupMinimapContainer(false);
		widgetManager.setTargetsHidden(isCompactLayout() && isCompassHidden(), Compass.values());
		widgetManager.remapTargets(Compass.values());
		widgetManager.remapTargets(Orbs.values());
		updateNoClickThrough();

		if (!isClassicResizable() && !isFixedMode())
		{
			hideLogout();
		}
		gameframe.refresh();
	}

	//only called on plugin shutdown
	public void reset()
	{
		if (isEditingLayout)
		{
			//save configs on shutdown
			isUpdatingProfile = false;
			editManager.toggleEditMode(false);
		}

		hasSeenWikiWarning = false;
		hideWorldMap = false;
		hideLogoutX = false;
		enableNoClickThrough = false;
		isUpdatingProfile = false;
		enableOrbSwapping = false;

		bindingManager.clear();
		orbHidden.clear();

		clearCustomChildren();

		resetVisibility();
		resetPositioning();
		resetNoClickThrough();

		//save for last to prevent potential NPE with offsets
		slotManager.clear();
        nativeStates.restore();
	}

	//used for profile swap/full config reset
	public void rebuild(boolean reset)
	{
		if (isEditingLayout)
		{
			//do not save configs
			editManager.toggleEditMode(false);
		}

		hasSeenWikiWarning = false;
		hideWorldMap = config.hideWorld();
		hideLogoutX = config.hideLogout();
		enableNoClickThrough = config.enableNoClickthrough();
		enableOrbSwapping = getCurrentLayout() != OrbLayout.FREE_POSITION && config.enableOrbSwapping();
		isUpdatingProfile = false;

		//update based on config
		hideAllOrbsByConfig();
		slotManager.update();

		if (reset)
		{
			resetSavedPositionConfigs();
		}
		rebuildLayout();
	}

	private void resetVisibility()
	{
		widgetManager.setTargetsHidden(false, Orbs.values());
		widgetManager.setTargetsHidden(false, Compass.values());
		hideMinimapOnTabClose(false);
		updateWikiBannerVisibility(false);
		widgetManager.setTargetsHidden(hideStonesAndIcons(), Orbs.LOGOUT_X_ICON, Orbs.LOGOUT_X_STONE);
	}

	private void resetPositioning()
	{
		if (isFixedMode())
		{
			widgetManager.remapTargets(true, Script.FORCE_UPDATE, Orbs.WORLD_MAP_CONTAINER);
		}

		setupMinimapContainer(true);

		widgetManager.remapTargets(true, Script.FORCE_UPDATE, Orbs.values());
		widgetManager.remapTargets(true, Script.FORCE_UPDATE, Compass.values());
	}

	private void resetNoClickThrough()
	{
		for (TargetWidget orb : Orbs.SWAPPABLE_ORBS)
		{
			widgetManager.setNoClickThrough(orb.getComponentId(), false);
			handleVanillaNoClickThrough(orb, false);
			widgetManager.clearChild(noClickThroughChildren.remove(orb));
		}
	}

	//toggle the minimap visibility, and update related widgets when using the custom toggle button
	public void onMinimapToggle()
	{
		saveConfig(ConfigKeys.MINIMAP, !isMinimapHidden());
		rebuildLayout();
		hideMinimapOnTabClose(config.hideMinimapWithSidePanel());
	}

	public void setupMinimapContainer(boolean toDefault)
	{
		if (!toDefault && getCurrentLayout() == OrbLayout.FREE_POSITION) return;
		widgetManager.setTargetsHidden(!toDefault && isCompactLayout(), Minimap.COMPONENTS);
		widgetManager.remapTargets(toDefault, Script.FORCE_UPDATE, Minimap.CONTAINERS);
		widgetManager.revalidate(Minimap.COMPONENTS);

		//TODO - seems like revalidation causes the width/height issue (on the orbsContainer)
		//Orb.UNIVERSE parent width/height mode seems to cause an issue when setting dimensions
		//causing the Orbs.UNIVERSE container be set to the dimensions of the tli (GAMEFRAME)
		//this should alleviate that by setting the size temporarily until restored by a clientscript (clicking a side panel tab, etc)
		if (!isFixedMode() && !isMinimapMinimized())
		{
			Widget orbsContainer = client.getWidget(Minimap.ORBS_UNIVERSE.getComponentId());
			Widget parent = client.getWidget((isClassicResizable() ? Minimap.CLASSIC_ORBS_CONTAINER : Minimap.MODERN_ORBS_CONTAINER).getComponentId());
			if (orbsContainer != null && parent != null)
			{
				widgetManager.updateValue(orbsContainer::getWidth, orbsContainer::setWidth, parent.getWidth());
				widgetManager.updateValue(orbsContainer::getHeight, orbsContainer::setHeight, parent.getHeight());
			}
		}
	}

	public void hideMinimapOnTabClose(boolean hide)
	{
		if (!isFixedMode() && !isClassicResizable())
		{
			//wait for the wiki plugin to create its banner
			// - side panel appears to be flagged as 'closed' on world-hop(other?), which will hide the minimap container
			//   and cause the wiki plugin to return early when it tries to create its widget
			if (isWikiPluginConfigEnabled() && !wikiPluginBannerExists)
			{
				Widget container = widgetManager.getTargetWidget(Orbs.WIKI_ICON_CONTAINER);
				if (container == null)
				{
					return;
				}

				if (!widgetManager.hasChildren(container))
				{
					return;
				}

				wikiPluginBannerExists = true;
			}

			updateLogoutXPosition();

			if (isCutsceneActive || isMinimapMinimized() || isMinimapPluginConfigEnabled())
			{
				return;
			}

			boolean hidden = hide && isCompactLayout() && isSidePanelHidden();

			//only hide the parent not the children (do not use setTargetsHidden/setHidden(target...))
			widgetManager.setHidden(Minimap.MODERN_MAP_MINIMAP.getComponentId(), hidden);
			widgetManager.setHidden(Minimap.MODERN_ORBS_CONTAINER.getComponentId(), hidden);
		}
	}

	public boolean hideMinimapWithSidePanel()
	{
		return config.hideMinimapWithSidePanel();
	}

	public void updateNoClickThrough()
	{
		if (isFixedMode())
		{
			return;
		}

		boolean noClick = enableNoClickThrough ||
			(enableOrbSwapping && isCompactLayout() && !getCurrentLayout().isCustom());

		for (TargetWidget orb : Orbs.SWAPPABLE_ORBS)
		{
			widgetManager.setNoClickThrough(orb.getComponentId(), noClick && isCompactLayout());

			if (!isCompactLayout())
			{
				handleVanillaNoClickThrough(orb, noClick);
			}
			else
			{
				widgetManager.clearChild(noClickThroughChildren.remove(orb));
			}
		}
	}

	//TODO
	//in non-compact layouts, delegate the noClickThrough flag to the button widget instead of the layer/backing,
	//since under certain configurations when orb swapping, it may prevent a click where they overlap
	//if noClickThrough is enabled, and the button is hidden - create a noClick child in its place
	private void handleVanillaNoClickThrough(TargetWidget target, boolean noClickThrough)
	{
		Widget layer = client.getWidget(target.getComponentId());
		Widget backing = client.getWidget(target.getBackingId());
		Widget button = client.getWidget(target.getButtonId());
		if (layer == null || backing == null || button == null)
		{
			return;
		}

		widgetManager.setNoClickThrough(backing.getId(), !noClickThrough);
		widgetManager.setNoClickThrough(button.getId(), noClickThrough);

		if (!config.enableNoClickthrough() || !button.isHidden())
		{
			widgetManager.clearChild(noClickThroughChildren.remove(target));
			return;
		}

		Widget noClick = noClickThroughChildren.get(target);
        if (noClick != null && noClick.getParent() != layer)
        {
            widgetManager.clearChild(noClick);
            noClickThroughChildren.remove(target);
            noClick = null;
        }
		if (noClick == null)
		{
			noClick = widgetManager.createNoClick(layer, button);
			noClick.setNoClickThrough(true);
			noClick.revalidate();

			noClickThroughChildren.put(target, noClick);
		}
	}

	//TODO - test if stale state can happen to the XP orb when hovering into the HP orb in safe mode (poisoned)
	//orb swapping seems to have introduced a possible de-sync under certain configurations, where an orbs backing frame
	//will remain in a hovered state when moving into another orbs bounds (overlapped) while triggering the graphic swapper script (44)
	//before it applied the correct state to the previous orb - to resolve this, ignore non-orb related swaps and reset the stale
	//backing sprite if it exists
	// - test example: swap order [HP, SPEC, RUN, PRAY], default view
	// - hovering RUN -> PRAY = never becomes stale
	// - hovering PRAY -> RUN = can become stale
	public void resolveOrbFrameMismatch()
	{
		if (isCompactLayout() || !enableOrbSwapping)
		{
			return;
		}

		final int id = client.getIntStack()[1] - 1;

		if (!Orbs.isSwappableOrb(id))
		{
			return;
		}

		final Point mouse = client.getMouseCanvasPosition();

		for (TargetWidget target : Orbs.SWAPPABLE_ORBS)
		{
			final Widget backing = client.getWidget(target.getBackingId());
			final Widget button = client.getWidget(target.getButtonId());

			if (backing == null || button == null)
			{
				continue;
			}

			final int spriteId = backing.getSpriteId();
			final boolean hovering = button.getBounds().contains(mouse.getX(), mouse.getY());

			if (!hovering && spriteId == Sprite.FRAME_HOVERED)
			{
				backing.setSpriteId(Sprite.FRAME);
			}
		}
	}

	//create the compass frame and toggle buttons, clearing them if the parent id changed,
	//and only creating widgets if missing from the current parent
	public void createCustomChildren()
	{
		Widget parent = widgetManager.getOrbsParent();
		if (parent == null)
		{
			return;
		}

		if (minimapButton != null && (minimapButton.getParent() != parent || !widgetManager.isAttachedChild(minimapButton)))
		{
			widgetManager.clearChild(minimapButton);
            minimapButton = null;
		}

		if (minimapButton == null)
		{
			minimapButton = widgetManager.createMinimapButton(parent);
			minimapButton.setAction(MenuOp.OP_INDEX_0, buildToggleOp(isMinimapHidden(), MenuOp.MINIMAP_OP));
			minimapButton.setOnOpListener(
				(JavaScriptCallback) e ->
				{
					switch (e.getOp() - 1)
					{
						case MenuOp.OP_INDEX_0:
							if (!isFixedMode())
							{
								onMinimapToggle();
								minimapButton.setSpriteId(widgetManager.getSpriteId(!isMinimapHidden()));
							}
							break;

						case MenuOp.EDIT_MODE_OP_INDEX:
							editManager.toggleEditMode(!isEditingLayout);
							break;
					}
				}
			);
			minimapButton.setOnMouseOverListener(
				(JavaScriptCallback) e ->
				{
					minimapButton.setOpacity(Layout.OPACITY_HOVER);
				}
			);
			minimapButton.setOnMouseLeaveListener(
				(JavaScriptCallback) e ->
					minimapButton.setOpacity(Layout.OPACITY)
			);
		}

		updateMinimapToggleButton();
		//compass frame moved to the compass container
		parent = client.getWidget(isClassicResizable() ? Classic.COMPASS_PARENT : Modern.COMPASS_PARENT);
		if (parent == null)
		{
			if (compassFrame != null)
			{
				widgetManager.clearChild(compassFrame);
            compassFrame = null;
			}
			return;
		}

		if (compassFrame != null && (compassFrame.getParent() != parent || !widgetManager.isAttachedChild(compassFrame)) || isFixedMode())
		{
			widgetManager.clearChild(compassFrame);
            compassFrame = null;
		}

		if (compassFrame == null && !isFixedMode())
		{
			compassFrame = widgetManager.createCompassFrame(parent);
		}

		updateCustomChildren();
	}

	public void updateCustomChildren()
	{
		updateCompassFrameChild();
		updateMinimapToggleButton();
		widgetManager.setHidden(MinimapOverlay.UNIVERSE, hideMinimapOverlay());
	}

	private void updateCompassFrameChild()
	{
		if (compassFrame == null)
		{
			return;
		}

		compassFrame.setHidden(!isCompactLayout() || isCompassHidden() || isMinimapMinimized());
	}

	public void updateMinimapToggleButton()
	{
		if (minimapButton == null)
		{
			return;
		}

		minimapButton.setHidden(config.hideMinimapToggle() || isMinimapMinimized());
		minimapButton.setSpriteId(widgetManager.getSpriteId(!isMinimapHidden()));
		if (!config.rightClickToggleButtons() || isFixedMode())
		{
			int index = MenuOp.OP_INDEX_0;
			if (isFixedMode())
			{
				index = MenuOp.EDIT_MODE_OP_INDEX;
			}
			minimapButton.setAction(
				index == MenuOp.OP_INDEX_0
					? MenuOp.EDIT_MODE_OP_INDEX
					: MenuOp.OP_INDEX_0, "");

			minimapButton.setAction(index,
				isFixedMode()
					? buildEditOp(isEditingLayout)
					: buildToggleOp(isMinimapHidden(), MenuOp.MINIMAP_OP));
		}

		minimapButton.setNoClickThrough(!config.rightClickToggleButtons());
		widgetManager.remapTargets(
			Button.MINIMAP_BUTTON_MODERN,
			Button.MINIMAP_BUTTON_CLASSIC,
			Button.MINIMAP_BUTTON_FIXED
		);
	}

	//clear any created children and reset previous parent id
	public void clearCustomChildren()
	{
        noClickThroughChildren.values().forEach(widgetManager::clearChild);
        noClickThroughChildren.clear();
		widgetManager.clearChild(minimapButton);
		widgetManager.clearChild(compassFrame);
		widgetManager.clearChild(overlayLogoutXIcon);
		widgetManager.clearChild(overlayLogoutXStone);

		//reset & clear the minimap overlay
		configureMinimapOverlayContainer(false);
        overlayNoClickChildren.forEach(widgetManager::clearChild);
        overlayNoClickChildren.clear();
		final Widget parent = client.getWidget(MinimapOverlay.UNIVERSE);
		if (parent != null)
		{
			// Remove only our children; preserve children owned by other plugins.
            widgetManager.clearChild(overlayCompass);
            widgetManager.clearChild(overlayCompassLayer);
            widgetManager.clearChild(overlayCompassNoClick);
            widgetManager.clearChild(overlayCompassMenuOp);
            widgetManager.clearChild(overlayMinimap);
            widgetManager.clearChild(overlayMinimapFrame);
		}

		overlayCompass = null;
		overlayCompassLayer = null;
		overlayCompassNoClick = null;
		overlayCompassMenuOp = null;
		overlayMinimap = null;
		overlayMinimapFrame = null;
		overlayLogoutXStone = null;
		overlayLogoutXIcon = null;

		widgetManager.clearChild(minimapButton);
            minimapButton = null;
		widgetManager.clearChild(compassFrame);
            compassFrame = null;
	}

	//initial setup for the minimap overlay
	//@enabled - for startup/shutdown behaviour
	private void configureMinimapOverlayContainer(boolean enabled)
	{
		Widget parent = client.getWidget(MinimapOverlay.UNIVERSE);
		if (parent == null)
		{
			return;
		}

		nativeStates.capture(parent);
        if (enabled && overlayMinimap != null)
		{
			return;
		}

		if (!enabled)
		{
			parent.setForcedPosition(-1, -1);
			widgetManager.setSelfHidden(parent, false);
		}

		parent.setOriginalWidth(enabled ? Layout.MinimapOverlay.CONTAINER_WIDTH : 0);
		parent.setOriginalHeight(enabled ? Layout.MinimapOverlay.CONTAINER_HEIGHT : 0);
		parent.setWidthMode(enabled ? WidgetSizeMode.ABSOLUTE : WidgetSizeMode.MINUS);
		parent.setHeightMode(enabled ? WidgetSizeMode.ABSOLUTE : WidgetSizeMode.MINUS);
		parent.setXPositionMode(enabled ? WidgetPositionMode.ABSOLUTE_RIGHT : WidgetPositionMode.ABSOLUTE_CENTER);
		parent.setYPositionMode(enabled ? WidgetPositionMode.ABSOLUTE_TOP : WidgetPositionMode.ABSOLUTE_CENTER);
		parent.revalidate();

		detachedMinimapParentIsReady = true;
	}

	//create necessary widgets for the minimap overlay
	private void createMinimapOverlayChildren()
	{
		final Widget parent = client.getWidget(MinimapOverlay.UNIVERSE);
		if (parent == null)
		{
			return;
		}

		for (int index = 0; index < Layout.MinimapOverlay.NO_CLICK_Y.length; index++)
		{
			final Widget mapNoClick = widgetManager.createOverlayNoClick(parent,
				Layout.MinimapOverlay.NO_CLICK_Y[index],
				Layout.MinimapOverlay.NO_CLICK_WIDTH[index],
				Layout.MinimapOverlay.NO_CLICK_HEIGHT[index]
			);
            overlayNoClickChildren.add(mapNoClick);
		}

		overlayCompass = widgetManager.createOverlayCompass(parent);
		overlayCompassLayer = widgetManager.createOverlayCompassLayer(parent);
		overlayCompassNoClick = widgetManager.createOverlayCompassNoClick(overlayCompassLayer);
		overlayCompassMenuOp = widgetManager.createOverlayCompassMenuOp(overlayCompassLayer);
		overlayMinimap = widgetManager.createOverlayMinimap(parent);
		overlayMinimapFrame = widgetManager.createOverlayMinimapFrame(parent);
		overlayLogoutXStone = widgetManager.createOverlayLogoutXStone(parent, hideOverlayLogoutX());
		overlayLogoutXIcon = widgetManager.createOverlayLogoutXIcon(parent, hideOverlayLogoutX());
	}

	public void setupMinimapOverlay()
	{
		configureMinimapOverlayContainer(true);
		clientThread.invokeLater(() ->
		{
            if (!active) return true;
			if (detachedMinimapParentIsReady)
			{
				createMinimapOverlayChildren();
				detachedMinimapParentIsReady = false;
				return true;
			}
			return false;
		});
	}

	public void updateWikiBannerVisibility(boolean hidden)
	{
		Widget container = widgetManager.getTargetWidget(Orbs.WIKI_ICON_CONTAINER);
		if (container == null)
		{
			return;
		}

		Widget banner = widgetManager.getTargetWidget(Orbs.WIKI_VANILLA_CONTAINER);
		if (widgetManager.hasChildren(container))
		{
			banner = container.getChild(0);
		}

		if (banner == null)
		{
			return;
		}

		//vanilla banner should be hidden if the in-game setting is disabled
		if (isWikiBannerDisabled() && !widgetManager.hasChildren(container) && !isEditingLayout)
		{
			hidden = true;
		}

		widgetManager.setSelfHidden(banner, hidden);
	}

	public String buildEditOp(boolean hidden)
	{
		return (hidden ? MenuOp.SAVE : MenuOp.ENABLE) + " "
			+ (hidden ? MenuOp.CHANGES : MenuOp.EDIT_MODE);
	}

	public String buildToggleOp(boolean hidden, String target)
	{
		return buildMenuOp(hidden ? MenuOp.SHOW : MenuOp.HIDE, target);
	}

	public String buildMenuOp(String action, String target)
	{
		return action + " " + ColorUtil.wrapWithColorTag(target, MenuOp.MENU_TARGET);
	}

	public void addCustomMenuEntries(MenuEntry entry)
	{
		if (entry.getType() != MenuAction.CC_OP)
		{
			return;
		}

		Menu menu = client.getMenu();
		Widget widget = entry.getWidget();
		if (widget == null)
		{
			return;
		}

		if (widget == overlayLogoutXStone)
		{
			menu.createMenuEntry(1)
				.setOption(MenuOp.WORLD_SWITCHER_OP)
				.setDeprioritized(false)
				.setType(MenuAction.RUNELITE)
				.onClick(e ->
				{
					Widget w = client.getWidget(InterfaceID.Worldswitcher.BUTTONS);
					if (w == null)
					{
						client.openWorldHopper();
					}
				});
		}
		else if (widget == minimapButton)
		{
			if (!isFixedMode())
			{
				if (!isEditingLayout)
				{
					if (config.rightClickToggleButtons())
					{
						entry
							.setOption(buildToggleOp(isMinimapHidden(), MenuOp.MINIMAP_OP))
							.setType(MenuAction.CC_OP_LOW_PRIORITY)
							.setDeprioritized(true);
					}

					if (isMinimapHidden() && config.showToggleOnMinimapButton())
					{
						menu.createMenuEntry(-2)
							.setOption(buildToggleOp(!config.showMinimapInCompactView(), MenuOp.DETACHED_OP))
							.setDeprioritized(config.rightClickToggleButtons())
							.setType(MenuAction.RUNELITE_LOW_PRIORITY)
							.onClick(e ->
								saveConfig(ConfigKeys.ENABLE_MINIMAP_OVERLAY, !config.showMinimapInCompactView())
							);
					}
				}

				int index = isCompactLayout() && config.showToggleOnMinimapButton() ? -3 : -2;
				menu.createMenuEntry(index)
					.setOption(buildEditOp(isEditingLayout))
					.setForceLeftClick(false)
					.setDeprioritized(config.rightClickToggleButtons())
					.setType(MenuAction.RUNELITE_LOW_PRIORITY)
					.onClick(e ->
						editManager.toggleEditMode(!isEditingLayout)
					);
			}
		}
	}

	public OrbLayout getCurrentLayout()
	{
		return config.layout();
	}

	//invert for readability
	public boolean isFixedMode()
	{
		return !client.isResized();
	}

	public boolean isClassicResizable()
	{
		if (!isLoggedIn() || isFixedMode())
		{
			return false;
		}

		final int classicArrangement = 0;
		return client.getVarbitValue(Varbit.RESIZABLE_STONE_ARRANGEMENT) == classicArrangement;
	}

	public boolean isLoggedIn()
	{
		return client.getGameState() == GameState.LOGGED_IN;
	}

	public boolean isCompactLayout()
	{
		return getCurrentLayout() != OrbLayout.FREE_POSITION && !isFixedMode() && isMinimapHidden() && !isMinimapMinimized();
	}

	public boolean isCustomLayout()
	{
		return getCurrentLayout().isCustom() && isCompactLayout();
	}

	public boolean isVanillaCustom()
	{
		return getCurrentLayout() != OrbLayout.FREE_POSITION && !enableOrbSwapping && !isCompactLayout() && !isMinimapMinimized();
	}

	public boolean isAnchorLeft()
	{
		return config.horizontalAnchor().isLeft();
	}

	public boolean isAnchorRight()
	{
		return config.horizontalAnchor().isRight();
	}

	public boolean isAnchorTop()
	{
		return config.verticalAnchor().isTop();
	}

	public boolean isAnchorBottom()
	{
		return config.verticalAnchor().isBottom();
	}

	public boolean allowReordering()
	{
		return !config.disableReordering();
	}

	public boolean isMinimapHidden()
	{
		if (isFixedMode())
		{
			return false;
		}

		return config.hideMinimap();
	}

	public boolean isCompassHidden()
	{
		return config.hideCompass();
	}

	public boolean isXpDropHidden()
	{
		return config.hideXp();
	}

	public boolean isWikiHidden()
	{
		return config.hideWiki();
	}

	public boolean isStoreHidden()
	{
		return config.hideStore();
	}

	public TogglePlacement getTogglePlacement()
	{
		return config.minimapTogglePlacement();
	}

	public boolean shouldOffsetXpOrb()
	{
		return !isFixedMode() && (enableNoClickThrough || enableOrbSwapping);
	}

	public boolean hideMinimapToggle()
	{
		return config.hideMinimapToggle();
	}

	public boolean hideMinimapOverlay()
	{
		return !(isMinimapOverlayEnabled() && isMinimapHidden() && !isMinimapMinimized()) || isFixedMode()
            || !isLoggedIn() || gameframe.isHiddenByGame(widgetManager.getMinimapMask());
	}

	public boolean isMinimapOverlayEnabled()
	{
		return config.showMinimapInCompactView();
	}

	//prevent unintended state changes for the logout-x since it is treated as a side icon/stone, i.e. don't unhide when it should be hidden
	public void hideLogout()
	{
		if (isFixedMode() || isMinimapMinimized())
		{
			return;
		}

		boolean hidden = hideLogoutX && !isOverlayLogoutVisible() || hideStonesAndIcons();

		widgetManager.setTargetsHidden(hidden, Orbs.LOGOUT_X_STONE, Orbs.LOGOUT_X_ICON);
	}

	//offset the logout-x instead of using the hidden-flag (similarly to the world map) when the overlays
	//logout-x is visible (retaining the FPS/Ping status offset so it doesn't block the overlays logout-x)
	public void updateLogoutXPosition()
	{
		if (isFixedMode() || isMinimapMinimized())
		{
			return;
		}
		widgetManager.remapTargets(Orbs.LOGOUT_X_STONE, Orbs.LOGOUT_X_ICON);
	}

	public void updateLogoutXOverlay()
	{
		if (overlayLogoutXStone == null || overlayLogoutXIcon == null)
		{
			return;
		}

		widgetManager.syncSprite(overlayLogoutXStone, Modern.LOGOUT_X_STONE);

		overlayLogoutXStone.setHidden(hideOverlayLogoutX());
		overlayLogoutXIcon.setHidden(hideOverlayLogoutX());
	}

	//restrict the overlays logout-x visibility to the same conditions as the original
	private boolean hideOverlayLogoutX()
	{
		if (widgetManager.getOrbsParent() == null)
		{
			return true;
		}

		return !config.showOverlayLogoutX() || isClassicResizable() || hideStonesAndIcons();
	}

	//similar to how the cs2 script hides/shows the logout-x by referencing the containers hidden state
	private boolean hideStonesAndIcons()
	{
		Widget w = client.getWidget(isClassicResizable() ? Classic.SIDE_TOP : Modern.SIDE_MOVABLE);
		if (w == null)
		{
			return false;
		}

		return w.isHidden();
	}

	//use offset-hiding instead of using the hidden-flag for the logout-x
	public boolean offsetLogoutX()
	{
		return isOverlayLogoutVisible() && !isMinimapMinimized();
	}

	boolean isOverlayLogoutVisible()
	{
		return isCompactLayout() &&
			isMinimapOverlayEnabled() && config.showOverlayLogoutX();
	}

	public boolean isActivityOrbDisabled()
	{
		final int activityOrbVisible = 0;
		return client.getVarbitValue(Varbit.ACTIVITY_ORB_TOGGLE) != activityOrbVisible;
	}

	public boolean isStoreOrbDisabled()
	{
		final int storeOrbVisible = 1;
		return client.getVarbitValue(Varbit.STORE_ORB_TOGGLE) != storeOrbVisible;
	}

	public boolean isWikiBannerDisabled()
	{
		final int wikiIconVisible = 0;
		return client.getVarbitValue(Varbit.WIKI_ICON_TOGGLE) != wikiIconVisible;
	}

	public boolean isMinimapMinimized()
	{
		//login screen NPE when 'loading interfaces %' occurs post update for the MINIMAP_TOGGLE varbit?
		if (!isLoggedIn())
		{
			return false;
		}

		final int minimapMinimized = 1;
		return client.getVarbitValue(Varbit.MINIMAP_TOGGLE) == minimapMinimized;
	}

	public boolean isCutsceneActive()
	{
		final int cutsceneActive = 1;
		return client.getVarbitValue(Varbit.CUTSCENE_STATUS) == cutsceneActive;
	}

	public boolean isSidePanelHidden()
	{
		if (!isLoggedIn())
		{
			return false;
		}

		final int sidePanelHidden = -1;
		return client.getVarcIntValue(VarClient.SIDE_PANEL_ID) == sidePanelHidden;
	}

	public int getLayoutXOffset()
	{
		return isCompactLayout() && isAnchorRight()
			? getCurrentLayout().getRightOffset() : 0;
	}

	public int getLayoutYOffset()
	{
		return isCompactLayout() && isAnchorBottom()
			? getCurrentLayout().getBottomOffset() : 0;
	}

	public int clampVerticalY()
	{
		int y = 0;
		int limit = 0;

		if (isAnchorBottom() && !isEditingLayout)
		{
			y += config.verticalYAdjustment();

			switch (getCurrentLayout())
			{
				case VERTICAL:
					if (isCompassHidden() && (hideLogoutX || isClassicResizable()))
					{
						limit = 38 + slotManager.getHiddenSize();
					}

					if (y > limit)
					{
						y = limit;
					}
					break;

				case HORIZONTAL:
					if (hideLogoutX || isClassicResizable() || allowReordering() && hideWorldMap)
					{
						limit = 16;
					}
				case HORIZONTAL_WIDE:
					if (y > getCurrentLayout().getBottomOffset() + limit)
					{
						y = getCurrentLayout().getBottomOffset() + limit;
					}
					break;
			}
		}

		return y;
	}

	public void hideAllOrbsByConfig()
	{
		orbHidden.configKeys().forEach(this::hideOrbByConfig);
	}

    public void refreshVisibility()
    {
        hideAllOrbsByConfig();
        if (!isEditingLayout)
        {
            widgetManager.setTargetsHidden(isCompactLayout() && isCompassHidden(), Compass.values());
            widgetManager.setTargetsHidden(isCompactLayout(), Minimap.COMPONENTS);
            hideMinimapOnTabClose(config.hideMinimapWithSidePanel());
        }
        updateCustomChildren();
    }

	public void hideOrbByConfig(String key)
	{
		if (isUpdatingProfile || (isEditingLayout && getCurrentLayout() != OrbLayout.FREE_POSITION) || key.equals(ConfigKeys.COMPASS))
		{
			return;
		}

		switch (key)
		{
			case ConfigKeys.HIDE_WORLD:
				hideWorldMap = config.hideWorld();
				if (getCurrentLayout() == OrbLayout.FREE_POSITION)
				{
					widgetManager.setTargetsHidden(hideWorldMap, Orbs.WORLD_MAP_CONTAINER, Orbs.WORLD_MAP_TOOLTIP);
					break;
				}
				widgetManager.remapTargets(Orbs.WORLD_MAP_CONTAINER);
				break;

			case ConfigKeys.HIDE_WIKI:
				updateWikiBannerVisibility(config.hideWiki());
				warnWikiPluginConflict();

				if (getCurrentLayout().isHorizontal() && isMinimapHidden())
				{
					updateMinimapToggleButton();
				}
				break;

			case ConfigKeys.HIDE_LOGOUT_X:
				hideLogoutX = config.hideLogout();

				if (!isClassicResizable())
				{
					hideLogout();
					updateLogoutXPosition();
				}
				break;

			default:
				HideOrbConfig orbConfig = orbHidden.getByConfig(key);
				if (orbConfig != null)
				{
					widgetManager.setTargetsHidden(
						orbConfig.getGetter().get(),
						orbConfig.getTargets()
					);
				}

				if (key.equals(ConfigKeys.HIDE_STORE)
					&& config.minimapTogglePlacement() == TogglePlacement.BELOW_MAP
					&& !isMinimapHidden()
					&& isLoggedIn())
				{
					updateMinimapToggleButton();
				}
				break;
		}
	}

	public boolean isSnapCornerRepositioned()
	{
		final java.awt.Point p = configManager.getConfiguration(
			ConfigGroup.Core.RUNELITE,
			ConfigKeys.Core.SNAPCORNER_PREFIX + OverlayPosition.CANVAS_TOP_RIGHT + ConfigKeys.Core.SNAPCORNER_CONFIG_LOCATION,
			java.awt.Point.class);

		return p != null;
	}

	public boolean isWikiPluginConfigEnabled()
	{
		return isPluginConfigEnabled(
			ConfigKeys.Core.WIKI_PLUGIN,
			ConfigGroup.Core.WIKI, ConfigKeys.Core.SHOW_WIKI_MINIMAP_BUTTON);
	}

	public boolean isMinimapPluginConfigEnabled()
	{
		return isPluginConfigEnabled(
			ConfigKeys.Core.MINIMAP_PLUGIN,
			ConfigGroup.Core.MINIMAP, ConfigKeys.Core.HIDE_MINIMAP);
	}

	private boolean isPluginConfigEnabled(String pluginKey, String configGroup, String configKey)
	{
		boolean isPluginEnabled = Boolean.TRUE.equals(
			configManager.getConfiguration(ConfigGroup.Core.RUNELITE, pluginKey, Boolean.class));

		boolean isConfigEnabled = Boolean.TRUE.equals(
			configManager.getConfiguration(configGroup, configKey, Boolean.class));

		return isPluginEnabled && isConfigEnabled;
	}

	public void warnWikiPluginConflict()
	{
		if (!hasSeenWikiWarning && isWikiPluginConfigEnabled() && config.hideWiki())
		{
			sendMessage(msg ->
				msg
					.append("the ")
					.append(ChatColorType.HIGHLIGHT)
					.append("`Hide Wiki banner` ")
					.append(ChatColorType.NORMAL)
					.append("setting is overriding the Wiki plugin's ")
					.append(ChatColorType.HIGHLIGHT)
					.append("`Show wiki button under minimap` ")
					.append(ChatColorType.NORMAL)
					.append("setting.")
			);

			hasSeenWikiWarning = true;
		}
	}

	private void sendMessage(Consumer<ChatMessageBuilder> consumer)
	{
		if (!isLoggedIn())
		{
			return;
		}

		ChatMessageBuilder builder = new ChatMessageBuilder()
			.append("[")
			.append(ChatColorType.HIGHLIGHT)
			.append("Interface Layout")
			.append(ChatColorType.NORMAL)
			.append("] ");

		consumer.accept(builder);

		String input = builder.build();

		chatMessageManager.queue(QueuedMessage.builder()
			.type(ChatMessageType.CONSOLE)
			.runeLiteFormattedMessage(input)
			.build());
	}

	public boolean useSavedPosition(Widget widget, int index) { return positions.useSavedPosition(widget, index); }
    public void resetTargetsSavedPosition(Binding binding, boolean remap) { positions.resetTargetsSavedPosition(binding, remap); }
    public void resetAllSavedPositions(boolean remap) { positions.resetAllSavedPositions(remap); }
    public void resetSavedPositionConfigs() { positions.resetSavedPositionConfigs(); }
    public int getSavedPosition(Widget widget, int index, ValueKey key) { return positions.getSavedPosition(widget, index, key); }
    public void saveCurrentLayoutPosition(Widget widget, Binding binding) { positions.saveCurrentLayoutPosition(widget, binding); }
    public String getCurrentPrefix() { return positions.getCurrentPrefix(); }
public <T> void saveConfig(String key, T value)
	{
		configManager.setConfiguration(ConfigGroup.GROUP_NAME, key, value);
	}

	public void migrateConfigs() { migration.migrateConfigs(); }
}
