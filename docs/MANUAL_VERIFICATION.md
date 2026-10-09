# Manual verification checklist

Use a development client with overlapping widget movers disabled. Human testing
is required; do not automate gameplay.

- Toggle the plugin while logged out/in in all three gameframe modes. Resize and
  change modes; confirm native restoration on disable and no freeze.
- Move each Free Position orb and compass in both resizable modes. Check native
  click, hover and tooltip behavior, minimap/orb visibility, profile persistence
  and restart persistence.
- Check compact presets, Custom positions, swapping and visibility with Hide
  minimap enabled. Confirm settings accurately describe each arrangement.
- Alt-drag native/horizontal/vertical bars. Hide one stone on each native row;
  confirm background hiding, row compaction and continued dragging.
- Alt+Shift-drag stones to detach/join groups, including leaving and returning
  without releasing. Confirm stable orientation, backgrounds and native clicks.
- Repeat with Edit interface and Shift-drag. Reset elements/groups and return
  stones to the main group.
- Check Logout tab and Logout-X in modes where each native control exists.
- Check game-hidden minimap and Special Attack states; never reveal game-hidden
  widgets, including during editing and disable.
- Disable/re-enable rapidly and switch profiles; check overlay/listener cleanup
  and cancellation of old queued work.

Overlapping widget movers are unsupported. Mock tests do not replace live
rendering and hit-testing verification.

## Issue #1 hotfix checks (pending live verification)

- In Resizable Modern, enable **Hide preset with side panel** with **Hide minimap**
  disabled. Use Hotkey Toggle Sidepanel to close the panel, then reopen it both
  with its toggle and an inventory/tab hotkey. Check minimap, compass and orbs.
- Repeat with Hide minimap enabled for each compact preset and with Free Position.
  Individually hidden or game-hidden elements must remain hidden after reopening.
- While the panel is closed, disable the setting, change profiles, change display
  modes, and disable/re-enable the plugin. Check restoration and saved positions.
- Check Fixed and Resizable Classic remain unaffected, including transitions back
  to Resizable Modern. Check a world hop, wiki banner initialization, minimap
  minimization and the core Minimap plugin's Hide minimap setting.
