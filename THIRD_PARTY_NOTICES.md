# Third-party attribution

Interface Layout adapts these BSD-2-Clause plugins:

- **Compact Orbs**, cue (its-cue), https://github.com/its-cue/compact-orbs
  - Reference commit: `43499544b000361346be6cad7e37d3d21f5f4767`.
  - Copyright (c) 2025, its-cue (repository license); source headers also identify cue <https://github.com/its-cue>.
  - Orb configuration, controllers, widget models, offsets, slots, edit bindings,
    drag handling, detached minimap and migration helpers are adapted from it.
- **Menu Stones Hider**, Richardant, https://github.com/Richardant/menustoneshider
  - Reference commit: `5b8d32caa27e722df70f918f46933429f365159b`.
  - Copyright (c) 2025, Richardant.
  - Menu decoration visibility mappings and reload/hotkey behavior are adapted
    from this implementation.
- **RuneLite WidgetOverlay**, Tomas Slusny.
  - Copyright (c) 2018, Tomas Slusny <slusnucky@gmail.com>.
  - The detached minimap overlay retains adapted WidgetOverlay code.

- **Custom Game Tabs**, Frederick Campbell, https://github.com/FrederickCampbell/custom-game-tabs
  - Reference commit: `f7c861fe0a19995d401cabb3361a4812ed43a88d`.
  - Copyright (c) 2016-2017, Adam <Adam@sigterm.info>; Copyright (c) 2026, Frederick Campbell.
  - Native menu-operation projection and its MenuEntryAdded integration are adapted
    in MenuButtonOverlay. Replacement surfaces are implemented using Interface
    Layout's existing grouping and native Fixed/Classic/Modern artwork.
  - The original BSD-2-Clause license is retained in LICENSE-CUSTOM-GAME-TABS and
    packaged in META-INF/LICENSE-CUSTOM-GAME-TABS.

All authors are attributed in the single BSD-2-Clause LICENSE at the repository
root. The same license is included in the plugin JAR as META-INF/LICENSE, alongside
this provenance notice. Original source copyright/license headers remain intact.

New layout coordination, clipping expansion, state journals, free-position editing,
menu-stone placement and tests were added for Interface Layout. These received live
user testing and automated regression coverage; compatibility with every mode or
other plugin is not established. The initial plugin is available through the Plugin Hub; subsequent changes require their own review.
