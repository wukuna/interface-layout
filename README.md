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

## Sizing (Beta)

Open **Sizing (Beta)** to shrink the outer menu stones and HUD orbs/buttons to **50-100%** of their normal size. **Resize menu tabs** and **Resize orb group** set the size for each family. Individual controls are relative to that group: for example, an 80% group with a 75% individual control gives 60% of normal. The final size cannot fall below 50% or exceed 100%. All defaults are 100%.

Only the outer controls change. The inventory, equipment, combat-options, prayer and spellbook panel contents, the special attack bar, the minimap and 3D components keep their native size. The Prayer orb and Prayer/Spellbook **tab buttons** are separate from the icons inside those panels. Orb numbers keep their normal size unless optional text sizing is enabled.

The controls retain their native actions and shrink their visual and clickable bounds together. Menu rows repack around the new sizes; stone spacing remains measured in game pixels. Orb positions stay where they were saved, so shrink and reposition them as needed. Existing game-hidden controls stay hidden, and window/bank/PIN protection still applies to stones. Stretched Mode magnifies this result afterward.

Set the sizing controls back to 100% to restore normal sizes. Disabling the plugin restores owned native geometry; saved size preferences remain available when re-enabling it. Sizing is a Beta feature; restore a control to 100% if it does not display or behave as expected.

### Orb text sizing (Beta)

In **Sizing (Beta)**, enable **Scale orb text/numbers (Beta)** to shrink HP, Prayer, Run and Special attack numbers with their orbs. This is off by default. It uses RuneLite fonts for smaller numeric labels and preserves the current values and colors. At 100%, text stays native. Unknown fonts or interactive labels stay native too.

### Resizing Orb Timers (Beta)

The warning is inside **Sizing (Beta)**, above the orb resizing controls. Its checkbox is informational only and does not change any behavior.

**WARNING: If you use orb timers, turn OFF their original circular overlays before using resized orbs. Otherwise those rings will stay at their original size. Keep the timer plugins enabled.**

- **Regeneration Meter:** turn off **Show hitpoints regen** and/or **Show spec. attack regen** for the rings you replace.
- **Special Attack Timers:** turn off **Show Circular Overlay**.
- In Interface Layout, enable **Resizing Orb Timers (Beta)** and the HP and/or special attack ring you want.

These are Interface Layout's own replacement rings. They follow the orb's position and size, including at 100%, and hide with its native visibility and overlapping supported windows. No other plugin's settings or overlays are changed. Notifications, infoboxes and other timer features can stay enabled.

The HP ring uses the enabled Regeneration Meter plugin's public timing. For special attack, enabled Special Attack Timers takes priority; otherwise the ring uses Regeneration Meter. The Special Attack Timers replacement tracks regeneration independently, including Lightbearer and supported wave pauses. If enabled mid-cycle, it waits for the next observed regeneration or known cycle start before drawing, rather than showing a guessed time. It does not replace Surge potion cooldowns or other timer features. Disabling both timer plugins removes the corresponding replacement rings. This compatibility is Beta; report any timing, visibility or alignment problems along with your timer settings.

## Moving menu stones and orbs

Enable **Edit interface** to show editing outlines and allow **Shift-drag** stone grouping. RuneLite **Alt-drag** moves whole bars, detached stones, free orbs and the compass with Edit interface on or off. Choose **Free Position** under **Map and orb visibility → Orb arrangement** to move orbs individually, and choose a vertical or free-position menu arrangement to detach and regroup stones.

> Moving menu bars as groups, splitting stones into separate groups, and positioning individual orbs and the compass. The recording uses an earlier settings panel; the option names below match the current release.

https://github.com/user-attachments/assets/fa3c1d99-eb0f-4b7d-9fb8-818fcd97df77

## Menu bars and stone groups

Open **Menu bars** and choose a **Menu arrangement**:

- **Native rows** keeps the game's row arrangement. The rows remain movable, including when individual tabs are hidden.
- **Vertical left** and **Vertical right** start the bars along the selected edge. These are starting positions; you can move the bars afterward.
- **Free position** lets you build your own arrangement. **Initial bar direction** chooses the starting direction of new bars; existing groups keep their direction.

In Fixed and both resizable modes, **Free position** starts with separate top and bottom groups. Older combined defaults are split too, including moved bars; explicitly saved custom memberships, detached groups and ordering are retained. Choosing Column starts the default groups beside each other. In Resizable Classic, an untouched **Native rows** layout keeps the game's original stone artwork, spacing and icon alignment. Moving a native row with zero spacing translates it as a whole.

### Moving a whole bar

Hold **Alt** and drag a menu bar, using RuneLite's normal overlay positioning controls. The same control works with **Edit interface** on or off. If you changed RuneLite's overlay movement hotkey, use that key instead of Alt.

**Stone spacing** sets the gap between visible stones. Hiding a stone closes its gap while keeping the group's saved position.

**Allow clicks through gaps** is enabled by default. Disable it to block clicks through the spacing between visible, clickable stones in a moved bar without selecting a neighboring tab. Overlapping supported game windows take priority. Native menu protection covers supported world actions; unfamiliar menu entries are left available rather than removed. Clicks on the stones, outside the bar, and selections in an already-open right-click menu remain unchanged.

### Moving a single stone

Choose **Free position**, **Vertical left** or **Vertical right** first. Individual stone groups are not available in **Native rows**.

1. Enable **Edit interface**, then hold **Shift** and drag a stone away from its bar. Use Shift alone; Alt uses RuneLite's whole-bar movement.
2. Release it away from other stones to leave it on its own.
3. Drop it near another stone or group to join them.

Joining an existing bar preserves that bar's direction. Joining two separate stones side by side creates a row; joining them above or below each other creates a column. You can drag the resulting group as a whole or detach a stone again.

**Shift-right-click** a stone to return it to its main group.

Stone grouping has a five-pixel movement threshold and shows a proposed-position outline while dragging. Membership and position are saved only when you release. Focus loss, native interface rebuilds, or display-mode changes cancel the pending edit without changing the saved group. Stretched Mode uses RuneLite's translated native canvas coordinates, so grouping does not depend on which plugin was enabled first. Existing layouts are preserved; there is no separate stretched layout or migration.

## Menu tab visibility

Moved stones temporarily give way to overlapping Bank/PIN, Collection Log, skill-guide and Settings windows. Stones outside those windows stay available. Moving, resizing or closing a supported window restores uncovered stones in their saved positions; the plugin does not move or resize the windows themselves.

This also covers Combat Achievements overview, tasks, boss lists/details and rewards, plus quest journals, achievement-diary task windows, journal scrolls and quest-completion screens. Only visible window areas count; hidden windows and the ordinary quest sidebar do not hide stones.

Banking can hide tab icons as part of the game's normal behavior. Interface Layout preserves stone backgrounds that the game still displays without forcing hidden icons back on or making them clickable. Bank and PIN windows keep their native positions and sizing. Window protection covers the interfaces listed above; it does not promise coverage for every game interface.

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

Choose **Orb arrangement → Free Position**, then **Alt-drag** an orb or the compass to its own position. Enable **Edit interface** if you want additional outlines and guidance. The compass can move separately from the minimap.

Use RuneLite's **Alt-right-click** to reset an element's position. Disable **Edit interface** or press **Escape** to hide editing guidance when you are finished.

Free Position moves the orbs and compass; it does not move the minimap itself. To put a separate map elsewhere, use **Show separate minimap**, described below.

Moving another orb preserves the native Wiki and world-map button positions, even if you have not moved those buttons. Their positions are only saved when you move them yourself. Game-hidden controls remain hidden.

### Compact orb arrangements

Choose **Vertical preset**, **Horizontal preset**, **Horizontal-wide preset** or **Custom preset**, then enable **Hide minimap** to activate the compact arrangement.

Open **Orb layout options** to adjust the preset:

- **Vertical anchor**, **Horizontal anchor** and **Vertical offset** control its placement.
- **Keep hidden orb slots** preserves the remaining orbs' slots when another orb is hidden.
- **Keep preset container size** preserves the preset's empty space after hiding orbs.
- **Swap preset orb slots** lets you enable Edit interface and drag HP, Prayer, Run or Special onto another orb to swap their slots.
- **Hide preset with side panel** hides the minimap, compass and orbs when the side panel is hidden in Resizable Modern, including the normal minimap with **Hide minimap** disabled. Reopening the side panel restores their previous visibility. Fixed and Resizable Classic are unchanged.

Preset anchoring, gap handling and slot swapping apply to compact arrangements. Free Position uses each element's saved location.

**Prevent orb and minimap clickthrough** stops clicks on orbs and the minimap from reaching the game behind them. Minimap navigation still works, including the detached minimap in compact layouts. This applies to every orb arrangement and uses the existing clickthrough setting.

## Separate minimap and shortcuts

**Show separate minimap**, under **Orb layout options**, displays a separate movable minimap while **Hide minimap** is enabled in a resizable mode. Use RuneLite's normal **Alt-drag** controls to position it.

This separate minimap does not support other plugins' minimap overlays, such as names, tile markers or lines. Keep the native minimap visible if you need those overlays.

- **Separate minimap toggle** adds a show/hide option to the minimap button.
- **Separate minimap Logout-X** adds a logout control to the separate map in Resizable Modern.

The **Minimap toggle button** section controls the optional eye button, its location and whether it requires a right-click. The **Keyboard shortcut** section lets you select a keybind and the action it controls: the button, native minimap, separate minimap or editing mode.

## Editing and saved layouts

**Editing controls** includes:

- **Stone grouping grid** aligns Shift-dragged stones to a grid measured in pixels. Set it to **1** to disable snapping. RuneLite handles snapping for Alt-drag movement.
- **Reset positions and groups** asks for confirmation before clearing saved positions and menu groups in every display mode. Choose No or close the dialog to cancel. Your visibility choices are kept.

Menu positions and groups are saved separately for **Fixed**, **Resizable Classic** and **Resizable Modern**. Independent orb and compass positioning, hiding the native minimap, and the separate minimap are features for resizable modes; Fixed keeps its native gameframe constraints.

Legacy Compact Orbs and Menu Stones Hider settings are imported once per RuneLite profile. The original plugins do not need to remain installed.

## Compatibility

### Other orb, minimap and menu layout plugins

Disable other plugins that move or hide the same controls before using Interface Layout. Declared conflicts include **Compact Orbs**, **Menu Stones Hider**, **Fixed Resizable Hybrid**, **Orb Hider**, **Minimap Hider** and **Movable Orbs**.

### Custom UI Anchors

Whole bars, detached stones, free orbs and the compass expose separate RuneLite movement overlays. Custom UI Anchors can capture those overlays using the normal overlay movement control. Use Shift-drag to change stone groups. Hidden controls retain their saved positions, while a newly created group gets a new identity.

### Minimap overlays

Use the native minimap when another plugin needs to draw on it. The separate minimap has the overlay limitation described above.

## Focused alternatives and original projects

Interface Layout combines and extends features from the projects below. If you only need one part of its feature set, these original plugins may be a better fit.

| Plugin | Author | Best fit |
| --- | --- | --- |
| [Compact Orbs](https://github.com/its-cue/compact-orbs) | cue | Compact orb arrangements and a separate minimap. |
| [Menu Stones Hider](https://github.com/Richardant/menustoneshider) | Richardant | Hiding native menu stones and decorations. |
| [Special Attack Timers](https://github.com/AnuVen/special-attack-timers) | Anuraag Venkat | Full special attack timers, notifications, infoboxes and Surge potion cooldowns. Its regeneration clock informed the BETA replacement ring. |
| [Regeneration Meter](https://github.com/runelite/runelite/tree/master/runelite-client/src/main/java/net/runelite/client/plugins/regenmeter) | RuneLite contributors | HP and special attack regeneration tracking; supplies public timing to the BETA replacement rings. |

## Support

If a layout does not behave as expected, check for another enabled plugin that moves or hides the same elements. Also check the game display mode and whether you selected a preset or Free Position.

[Open an issue](https://github.com/wukuna/interface-layout/issues) with the affected feature, display mode, steps to reproduce it, relevant settings and any overlapping plugins. A screenshot or short recording helps show positioning problems.

## License and attribution

Interface Layout is distributed under the [BSD-2-Clause license](LICENSE). The license attributes all contributing authors. See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) for the original projects and adapted code.

Ctrl+M while the World Map button is hidden is marked **Beta** in the Hide World Map setting tooltip. This shortcut combination has not been verified in-game; show the button again if the shortcut stops working.
