# Proposal: autocomplete-export-3dfix

## Why

Three gaps surfaced in real use: (1) writing GRBL G-code demands remembering
codes and parameters — inline autocompletion with short documentation removes
that friction and teaches the dialect; (2) the preview is worth sharing in
notes/issues but currently lives only on screen — an image export (PNG/JPG)
makes it a deliverable; (3) **the 3D preview renders nothing** for the user
with real files loaded — the camera placement puts the bed at the edge of (or
outside) the view frustum, so the window appears empty.

## What Changes

- **Autocompletion + command documentation** (editor): while typing, the
  editor offers completions for supported G/M codes and axis/parameter words,
  each with a one-line description; accepting a completion inserts it.
- **Save preview image** (preview): a menu action saves the current preview
  as PNG or JPG through a file dialog that starts in the last used directory
  (reusing the existing `last_open_dir` behavior).
- **Fix the 3D preview visibility** (bug, no spec change — the behavior is
  already specified): rework the camera to an orbit pattern that always looks
  at the bed center, and add a regression test that projects the bed into
  screen space and asserts it is actually inside the viewport.
- Non-goals: full G-code language server, hover documentation beyond the
  completion popup, animated GIF/video export, changing the 2D-removed
  decision.

## Capabilities

### New Capabilities

(ADDED requirements; the capabilities exist as deltas from prior changes.)

- `gcode-editor`: adds the autocompletion-with-documentation requirement.
- `toolpath-preview`: adds the save-preview-image requirement. (The 3D
  visibility fix changes no specified behavior.)

## Impact

- **Code**: `GCodeEditorView` (RichTextFX `AutoCompletePopup` wiring), a new
  pure `GCodeWords` documentation table, `Preview3DView` (orbit camera),
  `MainWindowController` + FXML (File > Save Preview Image...), tests, README.
- **Dependencies**: none new — autocompletion ships inside RichTextFX; image
  export uses JavaFX snapshot + `javax.imageio` (JDK).
- **Ecosystem**: no config or profile changes (`last_open_dir` reused as-is).
