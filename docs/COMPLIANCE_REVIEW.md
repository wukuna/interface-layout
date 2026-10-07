# Submission compliance review

Reviewed October 6, 2026 for the initial 1.0.0 source submission.

## Rules consulted

- [Plugin Hub submission and standard build requirements](https://github.com/runelite/plugin-hub/blob/master/README.md)
- [Plugin Hub Review](https://github.com/runelite/runelite/wiki/Plugin-Hub-Review)
- [Rejected or rolled back features](https://github.com/runelite/runelite/wiki/Rejected-or-Rolled-Back-Features)
- [Jagex third-party client guidelines](https://secure.runescape.com/m=news/third-party-client-guidelines?oldschool=1)
- [Plugin Hub disallowed API list](https://github.com/runelite/plugin-hub-tooling/blob/master/package/src/main/resources/net/runelite/pluginhub/packager/disallowed-apis.txt)

## Scope and security

Production code uses RuneLite widget, overlay, configuration, input-listener and
event APIs. It does not use reflection, native libraries, subprocesses, filesystem
access, network requests, credentials, automatic gameplay inputs or packet APIs.
Migration reads only the predecessor plugins' configuration groups and writes
only this plugin's group. Menu callbacks edit plugin settings, reset positions or
open existing native UI. The detached Logout control opens the native panel.

## Gameframe restrictions

Movement targets native orbs, compass and top-level menu tab icons/backgrounds.
It does not target content under combat options, inventory, equipment, spellbook
or prayer panels. Inventory backgrounds and three-dimensional viewport click
zones are not removed, moved or resized.

Treating navigation tabs separately from controls inside their panels is an
implementation assessment, not official acceptance. Reviewers must determine
whether the scope meets client rules, including Prayer/Special Attack orb movement.

Native visibility ownership is recorded separately from plugin hiding. Layout
script hooks restore owned changes before scripts and capture game visibility
afterward. Game-hidden minimap/Special Attack widgets remain hidden during editing
and shutdown. Detached minimap rendering checks game-hidden ancestors and
minimized-map state.

## Restoration and lifecycle

State journals restore owned geometry, visibility, parents and child arrays.
Clipping expansion retains sibling positions and dimensions. Obsolete queued
startup tasks are canceled by a generation guard, including rapid re-enabling.

## Packaging and licensing

The manifest uses `build=standard`. Production sources target Java 11 against
RuneLite 1.13.1, the current Plugin Hub version at review time. No extra production
runtime dependencies are required. Compact Orbs, Menu Stones Hider and RuneLite
overlay author notices are retained in source and consolidated into one BSD-2-Clause LICENSE, also packaged as META-INF/LICENSE.
Publication includes source, tests, build/wrapper files, icon, licenses and public
documentation. Local launchers, credentials, client homes, logs, caches and built
artifacts are excluded.

## Validation and limits

74 tests passed against RuneLite 1.13.1, covering geometry, grouping, backgrounds,
native row compaction, visibility, dependency injection, migration and lifecycle.
A separate standard-build reproduction validates production packaging.
Mocks cannot establish live hit testing or compatibility with every plugin. User
testing informed layout fixes; the manual checklist describes further coverage.

No blocking irregularity was found in the source review. Official acceptance
requires Plugin Hub review and its automated checks. Acceptance of predecessor
plugins does not establish approval for this implementation.
