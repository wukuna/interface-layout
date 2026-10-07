# Interface Layout

Interface Layout is an all-in-one RuneLite plugin for customizing the minimap, orbs, compass and menu stones. Choose a compact orb arrangement, position individual controls, turn menu rows into movable bars, create your own stone groups, and hide the elements you do not need.

Most features are independent, so you can use only the parts you want. Menu icons and their stone backgrounds move together, keeping their normal clicks and tooltips.

## Highlights

- **Free-position orbs** — move individual orbs and the compass independently of the minimap in resizable modes.
- **Compact orb layouts** — arrange orbs vertically, horizontally, in a wider row, or with a custom preset.
- **Movable menu bars** — keep native rows or start with vertical bars, then drag each bar wherever you want.
- **Individual menu stones** — detach stones, leave them on their own, or join them into separate rows and columns.
- **Individual visibility** — hide orbs, the compass, the minimap or specific menu tabs. Hidden menu stones also lose their backgrounds, and the remaining stones close the gap.
- **Saved layouts** — keep separate menu arrangements for Fixed, Resizable Classic and Resizable Modern.
- **Editing controls** — use drag handles, optional grid snapping and resets to adjust your layout.

## Moving menu stones and orbs

Enable **Edit interface** to show drag handles. Choose **Free Position** under **Map and orb visibility → Orb arrangement** to move orbs individually, and choose a vertical or free-position menu arrangement to detach and regroup stones.

> Moving menu bars as groups, splitting stones into separate groups, and positioning individual orbs and the compass. The recording uses an earlier settings panel; the option names below match the current release.

https://github.com/user-attachments/assets/fa3c1d99-eb0f-4b7d-9fb8-818fcd97df77

## Menu bars and stone groups

Open **Menu bars** and choose a **Menu arrangement**:

- **Native rows** keeps the game's row arrangement. The rows remain movable, including when individual tabs are hidden.
- **Vertical left** and **Vertical right** start the bars along the selected edge. These are starting positions; you can move the bars afterward.
- **Free position** lets you build your own arrangement. **Initial bar direction** chooses the starting direction of new bars; existing groups keep their direction.

### Moving a whole bar

Hold **Alt** and drag a menu bar, using RuneLite's normal overlay positioning controls. With **Edit interface** enabled, drag its handle without holding Alt.

**Stone spacing** sets the gap between visible stones. Hiding a stone closes its gap while keeping the group's saved position.

### Moving a single stone

Choose **Free position**, **Vertical left** or **Vertical right** first. Individual stone groups are not available in **Native rows**.

1. Hold **Alt + Shift** and drag a stone away from its bar. With **Edit interface** enabled, use **Shift-drag** instead.
2. Release it away from other stones to leave it on its own.
3. Drop it near another stone or group to join them.

Joining an existing bar preserves that bar's direction. Joining two separate stones side by side creates a row; joining them above or below each other creates a column. You can drag the resulting group as a whole or detach a stone again.

While editing, **Shift-right-click** a stone to return it to its main group.

## Menu tab visibility

Open **Menu tab visibility** to hide individual tabs, including Combat options, Skills, Quests, Inventory, Equipment, Prayer, Spellbook, Clan, Account Management, Friends, Logout, Settings, Emotes and Music.

Hiding a tab removes its icon and stone background. The remaining stones close the gap, and the bar stays movable. Show the tab again by clearing its visibility setting.

The **Menu bars** section also includes broader controls:

- **Hide combat–spellbook tabs** hides the first seven tabs wherever you have placed them.
- **Hide clan–music tabs** hides the second set of tabs wherever you have placed them.
- **Hide classic left border** and **Hide classic right border** remove the decorative borders in Resizable Classic.
- **Restore native menu shortcut** temporarily shows the original menu arrangement and visibility. Press it again to return to your saved layout.

The logout control depends on the game's display mode. **Fixed** and **Resizable Classic** have a Logout tab in the menu row. **Resizable Modern** uses the Logout-X beside the minimap instead.

## Map and orb visibility

Use **Map and orb visibility** to choose an orb arrangement and hide individual controls.

- **Hide minimap** removes the native minimap in resizable modes. In Free Position, the orbs keep their saved locations.
- **Hide compass** hides the compass independently.
- Individual orb settings hide HP, Prayer, Run, Special, XP and the other available map-side controls.
- **Hide Logout-X (Modern)** hides the minimap's logout control in Resizable Modern.

Showing a free-position orb or the compass again restores its saved location. A control that the game itself does not currently display will not appear just because its hide setting is off.

### Free-position orbs and compass

Choose **Orb arrangement → Free Position**, then enable **Edit interface**. Drag an outlined orb or the compass to its own position. The compass can move separately from the minimap.

Disable **Edit interface** or press **Escape** when you are finished. While editing, right-click an element's outline to reset its position.

Free Position moves the orbs and compass; it does not move the minimap itself. To put a separate map elsewhere, use **Show separate minimap**, described below.

### Compact orb arrangements

Choose **Vertical preset**, **Horizontal preset**, **Horizontal-wide preset** or **Custom preset**, then enable **Hide minimap** to activate the compact arrangement.

Open **Orb layout options** to adjust the preset:

- **Vertical anchor**, **Horizontal anchor** and **Vertical offset** control its placement.
- **Keep hidden orb slots** preserves the remaining orbs' slots when another orb is hidden.
- **Keep preset container size** preserves the preset's empty space after hiding orbs.
- **Swap preset orb slots** lets you enable Edit interface and drag HP, Prayer, Run or Special onto another orb to swap their slots.
- **Hide preset with side panel** hides the compact arrangement when the side panel is hidden in Resizable Modern.

Preset anchoring, gap handling and slot swapping apply to compact arrangements. Free Position uses each element's saved location.

**Prevent orb clickthrough** stops clicks on an orb from reaching the game behind it and applies to every orb arrangement.

## Separate minimap and shortcuts

**Show separate minimap**, under **Orb layout options**, displays a separate movable minimap while **Hide minimap** is enabled in a resizable mode. Use RuneLite's normal **Alt-drag** controls to position it.

This separate minimap does not support other plugins' minimap overlays, such as names, tile markers or lines. Keep the native minimap visible if you need those overlays.

- **Separate minimap toggle** adds a show/hide option to the minimap button.
- **Separate minimap Logout-X** adds a logout control to the separate map in Resizable Modern.

The **Minimap toggle button** section controls the optional eye button, its location and whether it requires a right-click. The **Keyboard shortcut** section lets you select a keybind and the action it controls: the button, native minimap, separate minimap or editing mode.

## Editing and saved layouts

**Editing controls** includes:

- **Snap grid** aligns dragged elements to a grid measured in pixels. Set it to **1** to disable snapping.
- **Reset positions and groups** clears saved positions and menu groups in every display mode. Your visibility choices are kept.

Menu positions and groups are saved separately for **Fixed**, **Resizable Classic** and **Resizable Modern**. Independent orb and compass positioning, hiding the native minimap, and the separate minimap are features for resizable modes; Fixed keeps its native gameframe constraints.

Legacy Compact Orbs and Menu Stones Hider settings are imported once per RuneLite profile. The original plugins do not need to remain installed.

## Compatibility

### Other orb, minimap and menu layout plugins

Disable other plugins that move or hide the same controls before using Interface Layout. Declared conflicts include **Compact Orbs**, **Menu Stones Hider**, **Fixed Resizable Hybrid**, **Orb Hider**, **Minimap Hider** and **Movable Orbs**.

### Minimap overlays

Use the native minimap when another plugin needs to draw on it. The separate minimap has the overlay limitation described above.

## Focused alternatives and original projects

Interface Layout combines and extends features from the projects below. If you only need one part of its feature set, these original plugins may be a better fit.

| Plugin | Author | Best fit |
| --- | --- | --- |
| [Compact Orbs](https://github.com/its-cue/compact-orbs) | cue | Compact orb arrangements and a separate minimap. |
| [Menu Stones Hider](https://github.com/Richardant/menustoneshider) | Richardant | Hiding native menu stones and decorations. |

## Support

If a layout does not behave as expected, check for another enabled plugin that moves or hides the same elements. Also check the game display mode and whether you selected a preset or Free Position.

[Open an issue](https://github.com/wukuna/interface-layout/issues) with the affected feature, display mode, steps to reproduce it, relevant settings and any overlapping plugins. A screenshot or short recording helps show positioning problems.

## License and attribution

Interface Layout is distributed under the [BSD-2-Clause license](LICENSE). The license attributes all contributing authors. See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) for the original projects and adapted code.
