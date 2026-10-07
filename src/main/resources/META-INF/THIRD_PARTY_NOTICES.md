# Third-party attribution

Interface Layout adapts, rather than independently recreates, these BSD-2-Clause plugins:

- **Compact Orbs**, cue, https://github.com/its-cue/compact-orbs
  - Reference commit: `43499544b000361346be6cad7e37d3d21f5f4767`.
  - Copyright (c) 2025, cue <https://github.com/its-cue>.
  - The orb controller, configuration, constants, widget models, offsets, slots,
    edit bindings, drag handling, detached minimap overlay and migration helper
    are adapted from this implementation. Original source license headers remain.
  - Full license: `licenses/Compact-Orbs.txt`.
- **Menu Stones Hider**, Richardant, https://github.com/Richardant/menustoneshider
  - Reference commit: `5b8d32caa27e722df70f918f46933429f365159b`.
  - Copyright (c) 2025, Richardant.
  - The modern/classic decoration visibility mappings and reload/hotkey behavior
    in the menu controller are adapted from this implementation.
  - Full license: `licenses/Menu-Stones-Hider.txt`.

Both license texts and this notice are included in the plugin JAR under `META-INF`.
New layout coordination, clipping expansion, state journals, free-position editing,
menu-stone placement and tests were added for Interface Layout. These additions
have received live user testing and automated regression coverage. This does not establish compatibility with every mode or other plugin; Plugin Hub approval remains pending.

The detached minimap overlay also retains RuneLite WidgetOverlay code with
Copyright (c) 2018, Tomas Slusny <slusnucky@gmail.com>. Its full BSD-2-Clause
notice is supplied in licenses/RuneLite-Overlay.txt and META-INF/licenses.


