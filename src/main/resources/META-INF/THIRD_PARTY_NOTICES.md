# Third-party attribution

Interface Layout adapts these BSD-2-Clause plugins:

- **Compact Orbs**, cue (its-cue), https://github.com/its-cue/compact-orbs
  - Reference commit: `43499544b000361346be6cad7e37d3d21f5f4767`.
  - Copyright (c) 2025, cue <https://github.com/its-cue>.
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

All authors are attributed in the single BSD-2-Clause LICENSE at the repository
root. The same license is included in the plugin JAR as META-INF/LICENSE, alongside
this provenance notice. Original source copyright/license headers remain intact.

New layout coordination, clipping expansion, state journals, free-position editing,
menu-stone placement and tests were added for Interface Layout. These received live
user testing and automated regression coverage; compatibility with every mode or
other plugin is not established. Plugin Hub approval remains pending.
