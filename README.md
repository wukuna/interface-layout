# Interface Layout

Arrange RuneLite around the way you play. Move menu bars, detach tabs into rows or columns, position orbs and the compass, and choose which controls stay visible. Scale overlay menu buttons while keeping your resource-pack icons.

**Pending Hub update:** frame choices and always-on orb/minimap click protection are included in [the submitted update](https://github.com/runelite/plugin-hub/pull/20615).

## What you can do

- **Build menu layouts:** keep native rows, start with vertical bars, or create custom groups and detached buttons.
- **Scale overlay buttons:** adjust all menu buttons together from **25–200%**.
- **Keep your artwork:** **Resource pack** is the default icon source, including custom spellbooks. **Faithful contours** is an optional style and a fallback for missing pack replacements.
- **Position map controls:** move and anchor the native minimap, free orbs and compass in resizable modes.
- **Use circular rims:** Silver or Dark grey frames, or no custom rim, with optional number-only, icon-only or combined circular-orb contents.
- **Track orb timers:** Optional inner rings for HP regeneration, special energy and prayer drain, including Rapid Heal, Lightbearer and equipment prayer bonus. The outer prayer potion-dose reminder stays separate.
- **Save each display mode:** Fixed, Resizable Classic and Resizable Modern keep separate menu layouts.
- **Control visibility:** hide individual tabs, map controls and decorative borders, with shortcuts and resets.

## Getting started

1. Enable **Interface Layout**, then open its minimap icon in RuneLite's side panel. If hidden, enable **Show side panel icon** in the regular plugin settings.
2. In **Layout**, select **Normal**, **Free placement** or **Compact orbs**. Normal restores native positioning without deleting the saved free layout. Free placement has its own anchor points and also works with Custom UI Anchors.
3. Choose the menu-tab arrangement in **Layout**. Enable **Edit interface** for grouping guides. **Alt-drag** moves bars or controls; **Shift-drag** detaches or joins menu tabs while editing.
4. Use **Appearance** for overlay menu buttons, artwork, button size, orb appearance and the minimap frame. Related controls appear when their parent option is enabled.
5. Use **Visibility** to show or hide controls. A checked box means visible; hiding an element preserves its position.
6. Use **Timers** for Beta HP, Prayer and Special timer styles. Turn **Edit interface** off when finished.

Regular plugin settings retain editing, keyboard shortcuts and sidebar visibility. Existing settings and saved layouts are preserved. Screenshots and recordings below show the earlier settings arrangement; use these tab names for the current controls.

## Resource packs and Faithful contours

**Appearance → Menu buttons → Icon artwork → Resource pack** follows active Resource Packs and Interface Styles replacements, including your spellbooks. Missing replacements fall back to Faithful contours. Selecting **Faithful contours** uses the bundled artwork instead; active tab backgrounds still follow the current game or pack.

**Keep native icon size** keeps icons at normal size inside enlarged buttons. Below 100%, they shrink with the buttons. Packs must support the selected display mode. No resource pack is downloaded or redistributed by this plugin.

Native and overlay menu layouts are saved separately. Turning overlay buttons off restores the saved native layout.

<details>
<summary>Overlay menu buttons and icon-source settings</summary>

![Overlay menu buttons with Resource pack selected, resizing and native-size controls](docs/media/resource-pack-menu-settings.png)

Example from a local TEST build with Resource pack selected. The displayed artwork depends on the active pack.

</details>

## Free-position native minimap

In **Classic or Modern resizable**, choose **Layout → Positioning → Free placement** and leave **Visibility → Minimap** checked. Alt-drag the native minimap, or place it through **Custom UI Anchors** using the normal overlay placement system.

[![Free-position minimap, separate compass and circular orbs with the Dark grey rim](docs/media/free-minimap-compass-orbs.png)](docs/media/free-minimap-and-orb-controls.mp4)

[Watch the 26-second demonstration](docs/media/free-minimap-and-orb-controls.mp4): circular-orb display choices, visibility toggles and native-minimap movement with anchor guides. Recorded in a local TEST build; the documentation copy has no audio.

The native map keeps its content and navigation. Choose **Appearance → Minimap → Frame** to keep the **Native / resource pack** surround (the default), or use the selected **Shared circular rim**. Choose **None** for the rim to remove the custom border. The native surround moves with the map and retains its combined compass socket. The compass follows the map until moved independently; other orbs keep their own positions. The minimap frame does not require enabling circular-orb number-tab removal.

Hiding and showing the map retains its position. Resetting the element clears its custom position. Resetting the layout or disabling the plugin restores native layout behavior. Fixed mode retains its native constraints, and game-hidden controls remain hidden.

**Layout → Legacy separate minimap → Show separate minimap** is a different option available in Compact orbs while the native map is hidden. That separate map does not include other plugins’ minimap overlays; this limitation is not a reason to replace the visible native map in the new Free Position workflow.

## Movement, anchors and resets

| Control | Action |
| --- | --- |
| Alt-drag | Move a whole bar, detached tab or supported free-position control. |
| Shift-drag with Edit interface | Detach or join a stone in a custom menu arrangement. |
| Shift-right-click a stone | Return it to its main group. |
| Alt-right-click | Open RuneLite’s overlay reset controls for the element. |
| Reset positions and groups | Reset saved positions/groups after confirmation; retain visibility choices. |

If you changed RuneLite’s overlay movement hotkey, use that modifier instead of Alt. Free-position overlays work with Custom UI Anchors. Ordinary inventory interaction remains protected; orb and minimap click protection is always active, and allowing clicks through gaps between menu tabs does not turn inventory items into click-through space.

Free-position controls and menu groups follow their saved screen edge or center when the game canvas changes size. Explicit RuneLite origins and Custom UI Anchors keep controlling attached elements. Controls are kept within the available canvas; returning to the larger size restores their placement without saving the temporary clamped position. Changing monitors alone does not move the layout if the game canvas stays the same size. Existing absolute layouts learn their reference size when first loaded within the visible canvas, so open the update at your usual size before moving to a smaller display.

## Beta appearance and timers

**Appearance → Orb appearance** offers **Number tabs** or **Circles only**. **Visibility** contains the optional shield special orb and extra special-attack meter. The extra meter sits below the special orb in Circles only, separated from its timer. **Appearance → Equipment** contains the optional enhanced equipment panel and reduced tooltips; native equipment slots and the expanded equipment window remain unchanged.

In **Timers**, choose **Inset sweep**, **Tag meters**, **Quiet rims** or **Eight slices** independently for HP, Prayer and Special. All four styles work with Number tabs and Circles only. HP and Special show regeneration progress. Prayer's countdown empties counterclockwise toward the next point drained, follows active prayers and equipped prayer bonus, and stops when prayers are inactive.

Prayer **Tag meters** and **Eight slices** can display the actual remaining prayer amount, the next-point drain countdown, or both. The blue inset and native prayer symbol stay visible. Existing circular prayer rings retain their appearance until a different style is selected. Native low-prayer flashes and existing potion-dose reminders retain their settings. Timers do not require Regeneration Meter; timing is estimated, especially when enabling mid-session or rapidly changing prayers.

## Guide, compatibility and support

See the [full guide](docs/guide.md) for presets, individual visibility controls, equipment limitations, anchors and conflicting layout plugins. The guide retains an [example movement recording](docs/media/move-detach-reattach.mp4) from a local TEST build; it demonstrates menu grouping, not the new native-minimap movement.

[Report an issue or suggest a feature](https://github.com/wukuna/interface-layout/issues). Include the display mode, native/overlay menu setting, resource pack, anchor setup and affected control.

## License and attribution

See [LICENSE](LICENSE) and the [full attribution](docs/guide.md#license-and-attribution). Existing code and third-party notices are retained.
