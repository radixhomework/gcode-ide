# Proposal: settings-dialog

## Why

The maintainer clarified the settings intent: not a menu-bar submenu, but a
**File > Settings... entry that opens a dedicated settings window** — a proper
home that can absorb all future settings (editor, preview, import defaults…)
instead of growing an ever-longer menu. The window follows conventional
**staged semantics** (Apply / OK / Cancel) so the user decides when changes
take effect, and settings that cannot take effect live must say so.

## What Changes

- **File > Settings...** menu entry (accelerator `Ctrl+,`) opening a dedicated
  settings window built as a tabbed dialog — one tab per settings category,
  starting with **Appearance** (theme selection across the installed
  AtlantaFX themes).
- **Three buttons**: **Apply** (commit + persist, window stays open), **OK**
  (commit + persist, window closes), **Cancel** (close, discard uncommitted
  changes). Changes are staged while editing — nothing takes effect until
  Apply or OK.
- **Restart-required marking**: a setting that cannot be applied to the
  running application SHALL carry a visible mention in the window stating it
  takes effect after the application is closed and reopened. (No current
  setting needs it; the mechanism is built for future settings.)
- Theme behavior (choose among the installed AtlantaFX themes, persist in
  `config.yaml` `theme` key, restore on startup, default Primer Light) is
  preserved from `theme-settings` but relocated into the dialog under the
  staged semantics.
- The top-level **Settings menu and its inline Theme submenu are removed**
  from the menu bar — the dialog is the single settings surface.
- Non-goals: new settings beyond theme; validation/error states; a restart of
  the application being automated (the mention only informs).

## Capabilities

### New Capabilities

- `settings-dialog`: the File > Settings entry, the dedicated settings window
  (Apply/OK/Cancel semantics, restart-required mentions), and theme selection
  living inside it under staged application.

### Modified Capabilities

(none — this supersedes the menu-submenu wording of `theme-settings`'s
`ui-theme-settings` delta on this branch; that change is implemented but not
archived, and its requirements are restated here in dialog form with staged
semantics.)

## Impact

- **Code**: new `view/SettingsDialog` (tabbed staged dialog with the button
  bar), FXML/controller (File > Settings entry, Settings menu removal),
  tests (Apply/OK/Cancel semantics, staged theme commit, restart-note
  rendering via a fixture setting), README.
- **Dependencies**: none new.
- **Ecosystem**: none.
