# Proposal: 3d-fixes-docs-debrand

## Why

Real-use feedback on the 3D preview and editor: depth appears inverted and
arcs seem to bend the wrong way (suspected axis/chirality bugs — to be
verified analytically); segments render as fat rounded tubes instead of lines
through the tool center; right-drag pan still does not work with real input
despite passing tests; the toolpath is hard to read without command docs at
the caret; and the app is branded around one machine it was never actually
specific to.

## What Changes

- **3D orientation (bug)**: G-code Z negative renders toward the bottom
  (lower = deeper) with the camera above the bed — today the initial camera
  sits below the bed plane and the bed-Y→world-Z mapping mirrors the drawing,
  which together make depth look inverted and arcs look wrong.
- **G2/G3 verification (bug, verify-then-fix)**: add hand-computed arc
  chirality tests in bed coordinates (parser) and an orientation-preserving
  3D mapping test; fix wherever the direction is actually wrong.
- **Line-style toolpath (presentation)**: segments render as thin lines
  centered on the toolpath (no rounded-tube look), keeping depth colors,
  distinct rapids, out-of-bed marking, and picking.
- **Right-drag pan (bug, still broken with real input)**: instrument event
  delivery in the running app, then fix with a platform-robust approach;
  acceptance is manual verification on the dev machine.
- **Hover/shortcut documentation**: the command description from the
  completion table shows when hovering a word (~2 s delay) or on Ctrl+Q for
  the word at or next to the caret.
- **Debrand**: remove all PROVER 3018 / Sainsmart mentions from the app,
  built-in data, README, specs, and project context; ship a generic example
  profile in its place (assumption recorded: keep one example profile so the
  zero-profile edge case stays out of scope).

## Capabilities

### New Capabilities

(ADDED requirements — capabilities exist as change deltas; the machine
profiles one supersedes the PROVER-specific wording from earlier deltas.)

- `gcode-editor`: hover/Ctrl+Q documentation requirement.
- `machine-profiles`: generic example profile requirement (replaces the
  built-in PROVER 3018 requirement).
- `toolpath-preview`: line-style segment rendering requirement (the
  orientation, chirality, and pan fixes change no specified behavior).

## Impact

- **Code**: `Preview3DView` (camera start, world mapping, line rendering,
  pan diagnostics + fix), parser tests (chirality goldens),
  `GCodeEditorView` (hover timer + Ctrl+Q doc popup), profile asset +
  `ProfileService` builtin list, README, `openspec/config.yaml` context,
  spec deltas, test updates.
- **Dependencies**: none new.
- **Ecosystem**: the built-in profile filename and name change
  (`example_mill.yaml`, "Example Mill") — user config `active_profile`
  values referencing "PROVER 3018" fall back to the first profile (existing
  behavior for unknown names).
