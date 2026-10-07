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
