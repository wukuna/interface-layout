# Interface Layout

A RuneLite plugin for arranging native gameframe orbs, the compass and menu tabs.
It combines preset orb layouts with individual positioning and draggable menu
groups. Native icons, stone backgrounds, clicks and tooltips stay together.

**Plugin Hub review pending.** This repository does not claim RuneLite or Jagex approval.

## Controls

Disable other plugins that move or hide the same widgets. Declared conflicts include
Compact Orbs, Menu Stones Hider and overlapping orb/minimap plugins.

### Orbs and compass

Choose **Map and orb visibility → Orb arrangement → Free Position** and enable
**Edit interface**. Drag an outlined orb or compass. Disable editing or press Escape
to lock the layout. Right-click an outline while editing to reset that element.
Optional grid snapping uses canvas pixels.

**Hide minimap** is independent of Free Position and preserves saved positions.
For Vertical, Horizontal, Horizontal-Wide or Custom preset layouts, enable Hide
minimap to activate the compact arrangement. Preset slot swapping is under
**Orb layout options**. Independent compass/orb positioning and hiding the minimap
apply to resizable modes; Fixed retains inherited compact-layout restrictions.

### Menu tabs

Select **Menu bars → Vertical left/right** or **Free position**. **Alt-drag** a
whole bar using RuneLite's normal overlay controls. **Alt+Shift-drag** a stone to
detach it; drop near another stone to join its group. With **Edit interface**
enabled, Shift-drag works without Alt. Joining preserves the group's orientation.
Shift-right-click a stone while editing returns it to the main group.

**Native rows** retains the game's row arrangement. Hiding a stone hides its
background and compacts the row; the row stays draggable. Logout tab and Logout-X
are separate settings; availability depends on gameframe mode.

Positions are saved separately for Fixed, Resizable Classic and Resizable Modern.
**Reset positions and groups** clears every mode's arrangements. Configure the
show-interface hotkey to temporarily restore native menu layout and visibility.
Legacy Compact Orbs/Menu Stones Hider settings are imported once per profile.

## Build

Use JDK 11 and the included Gradle wrapper:

```text
./gradlew test jar -PruneLiteVersion=1.13.1
./gradlew run
```

On Windows use `gradlew.bat`. Dependencies default to RuneLite `latest.release`;
the override above reproduces submission validation. Plugin Hub uses
`build=standard`; development tasks are not used for Hub packaging.
Tests use mocked widgets/services and do not automate RuneScape gameplay.
For Jagex Accounts use RuneLite's
[development-client guidance](https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts).

## Compatibility and review

This plugin moves gameframe navigation tabs, not items, equipment slots, spells,
prayers or combat controls inside panels. Game-imposed hidden widget states are
preserved. No gameplay input or server actions are generated automatically.

Native movement expands clipping ancestors while retaining sibling coordinates
and dimensions. Overlapping widget movers are unsupported: the Widget API cannot
read an existing forced position. Detached minimap rendering may conflict with
other minimap overlays. See [the compliance review](docs/COMPLIANCE_REVIEW.md) and
[manual verification checklist](docs/MANUAL_VERIFICATION.md).

## Credits

Adapted from [Compact Orbs](https://github.com/its-cue/compact-orbs) by cue and
[Menu Stones Hider](https://github.com/Richardant/menustoneshider) by Richardant.
Neither reference plugin needs to be installed. Original BSD-2-Clause notices
remain in source and packaged resources. See [LICENSE](LICENSE) and
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
