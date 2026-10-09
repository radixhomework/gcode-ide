# Proposal: theme-settings

## Why

The maintainer wants control over the app's look (dark mode now, other themes
later) and, separately, does not need the Machine profile selector — its
dropdown should go (full capability removal remains the separate
`remove-machine-profiles` change; this change only removes the UI element).

## What Changes

- A **Settings menu** with a Theme submenu offering the installed AtlantaFX
  themes (Primer Light/Dark, Nord Light/Dark, Cupertino Light/Dark,
  Dracula) as exclusive choices; the selection applies immediately and
  persists in `config.yaml` (`theme` key), restored on startup.
- **Machine dropdown removed** from the status bar (label + combo). The
  internal default profile keeps feeding diagnostics and the 3D bed
  (unchanged behavior; UI element only).

## Capabilities

### New Capabilities

- `ui-theme-settings`: the Settings/Theme menu, immediate application, and
  persistence of the chosen theme.

### Modified Capabilities

(none — the dropdown's removal does not alter any specified requirement;
profiles were never a UI-selector requirement of their own beyond the
machine-profiles capability, which the pending removal change handles)

## Impact

- **Code**: FXML (settings menu; dropdown removal), controller (theme menu
  population, selection handling, profile-combo wiring removal), `App`
  (theme restore + persistence wiring), integration test updates, README.
- **Dependencies**: none beyond the adopted AtlantaFX.
