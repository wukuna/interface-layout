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

package com.interfacelayout;
import com.interfacelayout.layout.orbs.OrbLayout;

import static com.interfacelayout.layout.orbs.OrbConstants.ConfigGroup.GROUP_NAME;
import com.interfacelayout.layout.orbs.OrbConstants.ConfigKeys;
import com.interfacelayout.layout.orbs.OrbConstants.Layout;
import com.interfacelayout.layout.orbs.widget.elements.Orbs;
import java.awt.event.KeyEvent;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Keybind;
import net.runelite.client.config.Range;

@ConfigGroup(GROUP_NAME)
public interface InterfaceLayoutConfig extends Config
{
	enum HorizontalAnchor
	{
		LEFT, RIGHT;

		public boolean isLeft()
		{
			return this == LEFT;
		}

		public boolean isRight()
		{
			return this == RIGHT;
		}
	}

	enum VerticalAnchor
	{
		TOP, BOTTOM;

		public boolean isTop()
		{
			return this == TOP;
		}

		public boolean isBottom()
		{
			return this == BOTTOM;
		}
	}

	@Getter
	@RequiredArgsConstructor
	enum TogglePlacement
	{
		DEFAULT(Layout.DEFAULT_MINIMAP_BUTTON_X, Layout.DEFAULT_MINIMAP_BUTTON_Y),
		ABOVE_XP(Layout.ABOVE_XP_MINIMAP_BUTTON_X, Layout.ABOVE_XP_MINIMAP_BUTTON_Y),
		BELOW_MAP(Layout.BELOW_MAP_MINIMAP_BUTTON_X, Layout.BELOW_MAP_MINIMAP_BUTTON_Y),
		BELOW_X(Layout.BELOW_X_MINIMAP_BUTTON_X, Layout.BELOW_X_MINIMAP_BUTTON_Y);

		private final int x;
		private final int y;
	}

	@Getter
	enum HotkeyOptions
	{
		MINIMAP_BUTTON,
		MINIMAP,
		DETACHED_MINIMAP,
		EDIT_MODE
	}

	//visible on the config panel
	@ConfigItem(
		keyName = ConfigKeys.ORB_LAYOUT,
		name = "Orb arrangement",
		description = "Free Position lets you Alt-drag individual orbs and the compass separately from the minimap. Preset arrangements apply when Hide minimap is enabled.",
		section = hideAndSwapUpdate,
		position = 0
	)
	default OrbLayout layout()
	{
		return OrbLayout.FREE_POSITION;
	}

	@ConfigSection(
		name = "Orb layout options",
		description = "Orb interaction, preset anchoring and gap handling. Preset options do not change Free Position.",
		closedByDefault = true,
		position = 7
	)
	String layout = "layout";

	@ConfigItem(
		keyName = ConfigKeys.ENABLE_NO_CLICKTHROUGH,
		name = "Prevent orb clickthrough",
		description = "Stop clicks on orbs from reaching the game behind them. Applies to every orb arrangement.",
		section = layout,
		position = 20
	)
	default boolean enableNoClickthrough()
	{
		return false;
	}

	@Range(min = 0, max = 200)
	@ConfigItem(
		keyName = ConfigKeys.VERTICAL_Y_ADJUSTMENT,
		name = "Vertical offset",
		description = "Move a compact preset upward by this many pixels. Does not change Free Position.",
		section = layout,
		position = 1
	)
	default int verticalYAdjustment()
	{
		return 0;
	}

	@ConfigItem(
		keyName = ConfigKeys.VERTICAL_ANCHOR,
		name = "Vertical anchor",
		description = "Top or bottom edge used by compact presets. Does not change Free Position.",
		section = layout,
		position = 2
	)
	default VerticalAnchor verticalAnchor()
	{
		return VerticalAnchor.BOTTOM;
	}

	@ConfigItem(
		keyName = ConfigKeys.HORIZONTAL_ANCHOR,
		name = "Horizontal anchor",
		description = "Left or right edge used by compact presets. Does not change Free Position.",
		section = layout,
		position = 3
	)
	default HorizontalAnchor horizontalAnchor()
	{
		return HorizontalAnchor.RIGHT;
	}

	@ConfigItem(
		keyName = ConfigKeys.DISABLE_REORDERING,
		name = "Keep hidden orb slots",
		description = "Keep preset orb slots in place when an orb is hidden. Free-position orbs always keep their saved positions.",
		section = layout,
		position = 4
	)
	default boolean disableReordering()
	{
		return false;
	}

	@ConfigItem(
		keyName = ConfigKeys.LEAVE_EMPTY_SPACE,
		name = "Keep preset container size",
		description = "Keep the preset container's empty space after hiding orbs. Does not change Free Position.",
		section = layout,
		position = 5
	)
	default boolean leaveEmptySpace()
	{
		return false;
	}

	@ConfigItem(
		keyName = ConfigKeys.ENABLE_ORB_SWAPPING,
		name = "Swap preset orb slots",
		description = "In preset arrangements, enable Edit interface and drag HP, Prayer, Run or Special onto another orb to swap slots. Free Position uses RuneLite Alt-drag instead.",
		section = layout,
		position = 6
	)
	default boolean enableOrbSwapping()
	{
		return false;
	}

	@ConfigItem(
		keyName = ConfigKeys.HIDE_MINIMAP_WITH_SIDE_PANEL,
		name = "Hide preset with side panel",
		description = "Hide the minimap, compass and orbs with the side panel, with or without Hide minimap enabled (resizable-modern only).",
		section = layout,
		position = 7
	)
	default boolean hideMinimapWithSidePanel() { return false; }

	@ConfigSection(
		name = "Minimap toggle button",
		description = "Optional eye button for hiding the minimap and toggling editing.",
		closedByDefault = true,
		position = 8
	)
	String button = "button";

	@ConfigItem(
		keyName = ConfigKeys.MINIMAP_TOGGLE_BUTTON,
		name = "Hide the toggle button",
		description = "Hide/show the minimap toggle button",
		section = button,
		position = 0
	)
	default boolean hideMinimapToggle()
	{
		return false;
	}

	@ConfigItem(
		keyName = ConfigKeys.RIGHT_CLICK_TOGGLE_BUTTONS,
		name = "Right click the toggle button",
		description = "Deprioritizes the toggle menu so it requires a right-click to interact with",
		section = button,
		position = 1
	)
	default boolean rightClickToggleButtons()
	{
		return false;
	}

	@ConfigItem(
		keyName = ConfigKeys.MINIMAP_BUTTON_PLACEMENT,
		name = "Toggle location",
		description = "Select the desired location of the toggle button while the minimap is visible",
		section = button,
		position = 2
	)
	default TogglePlacement minimapTogglePlacement()
	{
		return TogglePlacement.DEFAULT;
	}

	@ConfigSection(
		name = "Keyboard shortcut",
		description = "Choose a shortcut and the action it controls.",
		closedByDefault = true,
		position = 9
	)
	String hotkey = "hotkey";

	@ConfigItem(
		keyName = ConfigKeys.HOTKEY_KEYBIND,
		name = "Keybind",
		description = "Shortcut for the selected action. Use a modifier such as Shift or Ctrl to avoid normal game keys.",
		section = hotkey,
		position = 0
	)
	default Keybind hotkeyKeybind()
	{
		return new Keybind(KeyEvent.VK_INSERT, KeyEvent.SHIFT_DOWN_MASK);
	}

	@ConfigItem(
		keyName = ConfigKeys.HOTKEY_TOGGLE_OPTION,
		name = "Select toggle",
		description = "Select what the hotkey will control",
		section = hotkey,
		position = 1
	)
	default HotkeyOptions toggleOption()
	{
		return HotkeyOptions.EDIT_MODE;
	}

	@ConfigItem(
		keyName = ConfigKeys.ENABLE_MINIMAP_OVERLAY,
		name = "Show separate minimap",
		description = "Show a separate minimap while Hide minimap is enabled in a resizable mode. Free Position moves orbs and the compass, not the minimap itself.<br>" +
			"Warning: this minimap is not supported by plugins that display overlays on the minimap (names, marker tiles, lines, etc.)",
		section = layout,
		position = 30
	)
	default boolean showMinimapInCompactView()
	{
		return false;
	}

	@ConfigItem(
		keyName = ConfigKeys.ENABLE_OVERLAY_TOGGLE_OPTION,
		name = "Separate minimap toggle",
		description = "Display an option on the minimap button to hide/show the separate minimap",
		section = layout,
		position = 31
	)
	default boolean showToggleOnMinimapButton()
	{
		return true;
	}

	@ConfigItem(
		keyName = ConfigKeys.ENABLE_LOGOUT_X_OVERLAY,
		name = "Separate minimap Logout-X",
		description = "Show a functional Logout-X on the separate minimap (only works in resizable-modern) <br>",
		section = layout,
		position = 32
	)
	default boolean showOverlayLogoutX()
	{
		return false;
	}

	@ConfigSection(
		name = "Map and orb visibility",
		description = "Choose an orb arrangement and hide or show the minimap, compass and orbs. Preset details are under Orb layout options.",
		closedByDefault = true,
		position = 6
	)
	String hideAndSwapUpdate = "hideAndSwapUpdate";
	// Keep the original key so the eye button, hotkey and imported settings agree.
	@ConfigItem(
		keyName = ConfigKeys.MINIMAP,
		name = "Hide minimap",
		description = "Hide the native minimap in resizable modes. Preset arrangements also compact the orbs; Free Position keeps each saved orb position. Use Show separate minimap under Orb layout options if you want a map elsewhere.",
		section = hideAndSwapUpdate,
		position = 1
	)
	default boolean hideMinimap()
	{
		return false;
	}

	@ConfigItem(
		keyName = ConfigKeys.COMPASS,
		name = "Hide compass",
		description = "Hide this control. Show it again here; Free Position keeps the saved location.",
		section = hideAndSwapUpdate,
		position = 2
	)
	default boolean hideCompass()
	{
		return false;
	}

	//orb swapping
	@ConfigItem(
		keyName = ConfigKeys.HP_ORB_SLOT,
		name = "Compact HP slot",
		description = "",
		hidden = true
	)
	default Orbs orbInHPSlot()
	{
		return Orbs.HP_ORB_CONTAINER;
	}

	@ConfigItem(
		keyName = ConfigKeys.PRAYER_ORB_SLOT,
		name = "Compact Prayer slot",
		description = "",
		hidden = true
	)
	default Orbs orbInPrayerSlot()
	{
		return Orbs.PRAYER_ORB_CONTAINER;
	}

	@ConfigItem(
		keyName = ConfigKeys.RUN_ORB_SLOT,
		name = "Compact Run slot",
		description = "",
		hidden = true
	)
	default Orbs orbInRunSlot()
	{
		return Orbs.RUN_ORB_CONTAINER;
	}

	@ConfigItem(
		keyName = ConfigKeys.SPECIAL_ORB_SLOT,
		name = "Compact Spec slot",
		description = "",
		hidden = true
	)
	default Orbs orbInSpecialSlot()
	{
		return Orbs.SPEC_ORB_CONTAINER;
	}

	@ConfigItem(
		keyName = ConfigKeys.HP_ORB_SLOT_VANILLA,
		name = "Vanilla HP slot",
		description = "",
		hidden = true
	)
	default Orbs orbInHpSlotVanilla()
	{
		return Orbs.HP_ORB_CONTAINER;
	}

	@ConfigItem(
		keyName = ConfigKeys.PRAYER_ORB_SLOT_VANILLA,
		name = "Vanilla Prayer slot",
		description = "",
		hidden = true
	)
	default Orbs orbInPrayerSlotVanilla()
	{
		return Orbs.PRAYER_ORB_CONTAINER;
	}

	@ConfigItem(
		keyName = ConfigKeys.RUN_ORB_SLOT_VANILLA,
		name = "Vanilla Run slot",
		description = "",
		hidden = true
	)
	default Orbs orbInRunSlotVanilla()
	{
		return Orbs.RUN_ORB_CONTAINER;
	}

	@ConfigItem(
		keyName = ConfigKeys.SPECIAL_ORB_SLOT_VANILLA,
		name = "Vanilla Spec slot",
		description = "",
		hidden = true
	)
	default Orbs orbInSpecialSlotVanilla()
	{
		return Orbs.SPEC_ORB_CONTAINER;
	}

	//orb hiding
	@ConfigItem(
		keyName = ConfigKeys.HIDE_HP,
		name = "Hide HP",
		description = "Hide this control. Show it again here; Free Position keeps the saved location.",
		section = hideAndSwapUpdate,
		position = 3
	)
	default boolean hideHp()
	{
		return false;
	}

	@ConfigItem(
		keyName = ConfigKeys.HIDE_PRAYER,
		name = "Hide Prayer",
		description = "Hide this control. Show it again here; Free Position keeps the saved location.",
		section = hideAndSwapUpdate,
		position = 4
	)
	default boolean hidePray()
	{
		return false;
	}

	@ConfigItem(
		keyName = ConfigKeys.HIDE_RUN,
		name = "Hide Run",
		description = "Hide this control. Show it again here; Free Position keeps the saved location.",
		section = hideAndSwapUpdate,
		position = 5
	)
	default boolean hideRun()
	{
		return false;
	}

	@ConfigItem(
		keyName = ConfigKeys.HIDE_SPEC,
		name = "Hide Special",
		description = "Hide this control. Show it again here; Free Position keeps the saved location.",
		section = hideAndSwapUpdate,
		position = 6
	)
	default boolean hideSpec()
	{
		return false;
	}

	@ConfigItem(
		keyName = ConfigKeys.HIDE_XP,
		name = "Hide XP",
		description = "Hide this control. Show it again here; Free Position keeps the saved location.",
		section = hideAndSwapUpdate,
		position = 7
	)
	default boolean hideXp()
	{
		return false;
	}

	@ConfigItem(
		keyName = ConfigKeys.HIDE_WORLD,
		name = "Hide World Map",
		description = "Hide this control. Show it again here; Free Position keeps the saved location.",
		section = hideAndSwapUpdate,
		position = 8
	)
	default boolean hideWorld()
	{
		return false;
	}

	@ConfigItem(
		keyName = ConfigKeys.HIDE_STORE,
		name = "Hide Store",
		description = "Hide this control. Show it again here; Free Position keeps the saved location.",
		section = hideAndSwapUpdate,
		position = 9
	)
	default boolean hideStore()
	{
		return false;
	}

	@ConfigItem(
		keyName = ConfigKeys.HIDE_ACTIVITY,
		name = "Hide Activity Advisor",
		description = "Hide this control. Show it again here; Free Position keeps the saved location.",
		section = hideAndSwapUpdate,
		position = 10
	)
	default boolean hideActivity()
	{
		return false;
	}

	@ConfigItem(
		keyName = ConfigKeys.HIDE_WIKI,
		name = "Hide Wiki banner",
		description = "Hide this control. Show it again here; Free Position keeps the saved location.",
		section = hideAndSwapUpdate,
		position = 11
	)
	default boolean hideWiki()
	{
		return false;
	}

	@ConfigItem(
		keyName = ConfigKeys.HIDE_LOGOUT_X,
		name = "Hide Logout-X (Modern)",
		description = "Hide the X beside the minimap in Resizable Modern. Fixed and Resizable Classic use the Logout menu tab instead.",
		section = hideAndSwapUpdate,
		position = 12
	)
	default boolean hideLogout()
	{
		return false;
	}

	@ConfigItem(
		keyName = ConfigKeys.HIDE_GRID,
		name = "Hide Grid Master (Legacy)",
		description = "",
		hidden = true
	)
	default boolean hideGrid()
	{
		return false;
	}

    @ConfigSection(name = "Menu bars", description = "Arrange and move the menu tabs independently of the orbs", position = 4)
    String menu = "menu";
    @ConfigItem(keyName = "menuLayout", name = "Menu arrangement", description = "Alt-drag bars in any arrangement. Vertical left/right are initial positions, not locks. Use Shift-drag to detach or join stones in vertical or Free position groups.", section = menu, position = 0)
    default com.interfacelayout.layout.menu.MenuStoneController.Layout menuLayout() { return com.interfacelayout.layout.menu.MenuStoneController.Layout.VANILLA_HORIZONTAL; }
    @ConfigItem(keyName = "menuDirection", name = "Initial bar direction", description = "Initial direction in Free position. Existing bars keep their direction. Joining two separate stones side-by-side makes a row; above or below makes a column.", section = menu, position = 1)
    default com.interfacelayout.layout.menu.MenuStoneController.Direction menuDirection() { return com.interfacelayout.layout.menu.MenuStoneController.Direction.VERTICAL; }
    @Range(min = 0, max = 20)
    @ConfigItem(keyName = "menuSpacing", name = "Stone spacing", description = "Pixels between visible stones. Hidden stones close the gap without changing the group's saved position.", section = menu, position = 2)
    default int menuSpacing() { return 0; }
    @ConfigItem(keyName = "hideTopMenu", name = "Hide combat–spellbook tabs", description = "Hide Combat, Skills, Quests, Inventory, Equipment, Prayer and Spellbook wherever they are placed.", section = menu, position = 3)
    default boolean hideTopMenu() { return false; }
    @ConfigItem(keyName = "hideBottomMenu", name = "Hide clan–music tabs", description = "Hide Clan, Account Management, Friends, Logout, Settings, Emotes and Music wherever they are placed.", section = menu, position = 4)
    default boolean hideBottomMenu() { return false; }
    @ConfigItem(keyName = "hideLeftBar", name = "Hide classic left border", description = "Hide the decorative left border in resizable Classic mode.", section = menu, position = 5)
    default boolean hideLeftBar() { return false; }
    @ConfigItem(keyName = "hideRightBar", name = "Hide classic right border", description = "Hide the decorative right border in resizable Classic mode.", section = menu, position = 6)
    default boolean hideRightBar() { return false; }
    @Range(min = 0, max = 16383)
    @ConfigItem(keyName = "hiddenStones", hidden = true, name = "Hidden stone mask", description = "Optional individual visibility: bit 0 through bit 13 correspond to native stone order.", section = menu, position = 6)
    default int hiddenStones() { return 0; }
    @ConfigItem(keyName = "showMenuToggle", name = "Restore native menu shortcut", description = "Toggle a temporary view of the original menu tabs. Press again to return to your saved layout.", section = menu, position = 8)
    default Keybind showMenuToggle() { return Keybind.NOT_SET; }
    @ConfigSection(name = "Editing controls", description = "RuneLite Alt-drag moves bars, detached stones and free orbs. Shift-drag detaches or joins stones. Edit interface shows guidance and enables preset orb swapping.", position = 3)
    String editor = "interfaceEditor";
    @ConfigItem(keyName = "editInterface", name = "Edit interface", description = "Show editing outlines and guidance; enable preset orb slot swapping. RuneLite Alt-drag and Shift-drag stone grouping also work with this off.", position = 0)
    default boolean editInterface() { return false; }
    @Range(min = 1, max = 32)
    @ConfigItem(keyName = "editGrid", name = "Stone grouping grid", description = "Snap Shift-dragged stones to this pixel grid; 1 disables snapping. RuneLite controls Alt-drag positioning.", section = editor, position = 1)
    default int editGrid() { return 1; }
    @ConfigItem(keyName = "resetLayoutPositions", name = "Reset positions and groups", description = "Check once to clear saved positions and menu groups in every display mode. Visibility choices are kept.", section = editor, position = 2)
    default boolean resetLayoutPositions() { return false; }
    @ConfigSection(name = "Menu tab visibility", description = "Hide a tab, its icon and background; remaining tabs close the gap", closedByDefault = true, position = 5)
    String individualStones = "individualStones";
    @ConfigItem(keyName = "hideStone0", name = "Combat options", description = "Hide this tab, its icon and background. Remaining tabs close the gap and keep their saved position.", section = individualStones, position = 0)
    default boolean hideCombatStone() { return false; }
    @ConfigItem(keyName = "hideStone1", name = "Skills", description = "Hide this tab, its icon and background. Remaining tabs close the gap and keep their saved position.", section = individualStones, position = 1)
    default boolean hideSkillsStone() { return false; }
    @ConfigItem(keyName = "hideStone2", name = "Quests", description = "Hide this tab, its icon and background. Remaining tabs close the gap and keep their saved position.", section = individualStones, position = 2)
    default boolean hideQuestsStone() { return false; }
    @ConfigItem(keyName = "hideStone3", name = "Inventory", description = "Hide this tab, its icon and background. Remaining tabs close the gap and keep their saved position.", section = individualStones, position = 3)
    default boolean hideInventoryStone() { return false; }
    @ConfigItem(keyName = "hideStone4", name = "Equipment", description = "Hide this tab, its icon and background. Remaining tabs close the gap and keep their saved position.", section = individualStones, position = 4)
    default boolean hideEquipmentStone() { return false; }
    @ConfigItem(keyName = "hideStone5", name = "Prayer", description = "Hide this tab, its icon and background. Remaining tabs close the gap and keep their saved position.", section = individualStones, position = 5)
    default boolean hidePrayerStone() { return false; }
    @ConfigItem(keyName = "hideStone6", name = "Spellbook", description = "Hide this tab, its icon and background. Remaining tabs close the gap and keep their saved position.", section = individualStones, position = 6)
    default boolean hideSpellbookStone() { return false; }
    @ConfigItem(keyName = "hideStone7", name = "Clan", description = "Hide this tab, its icon and background. Remaining tabs close the gap and keep their saved position.", section = individualStones, position = 7)
    default boolean hideClanStone() { return false; }
    @ConfigItem(keyName = "hideStone8", name = "Account Management", description = "Hide this tab, its icon and background. Remaining tabs close the gap and keep their saved position.", section = individualStones, position = 8)
    default boolean hideAccountManagementStone() { return false; }
    @ConfigItem(keyName = "hideStone9", name = "Friends", description = "Hide this tab, its icon and background. Remaining tabs close the gap and keep their saved position.", section = individualStones, position = 9)
    default boolean hideFriendsStone() { return false; }
    @ConfigItem(keyName = "hideStone10", name = "Logout tab / Logout-X", description = "Fixed and Resizable Classic: hide the Logout tab in the bottom row. Resizable Modern: hide the Logout-X beside the minimap; that mode has no separate Logout tab in its native bottom row.", section = individualStones, position = 10)
    default boolean hideLogoutStone() { return false; }
    @ConfigItem(keyName = "hideStone11", name = "Settings", description = "Hide this tab, its icon and background. Remaining tabs close the gap and keep their saved position.", section = individualStones, position = 11)
    default boolean hideSettingsStone() { return false; }
    @ConfigItem(keyName = "hideStone12", name = "Emotes", description = "Hide this tab, its icon and background. Remaining tabs close the gap and keep their saved position.", section = individualStones, position = 12)
    default boolean hideEmotesStone() { return false; }
    @ConfigItem(keyName = "hideStone13", name = "Music", description = "Hide this tab, its icon and background. Remaining tabs close the gap and keep their saved position.", section = individualStones, position = 13)
    default boolean hideMusicStone() { return false; }
}
