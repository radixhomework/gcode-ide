# Proposal: preview3d-recent-dir

## Why

Two everyday-use gaps in the phase-1 IDE: (1) file dialogs always open in the
default location, forcing users to re-navigate to their G-code folder on every
open/save; (2) depth is the single most important sanity check before cutting,
and the 2D top-down projection cannot show it. **Revised after review: the 3D
view replaces the 2D view entirely — the maintainer wants one preview, and the
one that shows depth.**

## What Changes

- File dialogs (open and save-as) start in the last directory used by a
  successful file operation, remembered across application restarts via the
  existing `config.yaml`.
- **BREAKING (within the branch):** the 2D Canvas preview is removed. The
  preview is a 3D perspective view: toolpath against the bed with Z depth
  visible (bed X/Y ground plane, deeper lower), cuts colored by the depth
  ramp, rapids translucent gray, out-of-bed red; drag rotates, wheel zooms.
- Clicking a segment in the 3D view selects (and scrolls to) its source line
  in the editor — picking moves to 3D since the 2D view is gone. The
  editor→preview line highlight, live updates, and stale behavior carry over
  unchanged.
- Non-goals: material removal simulation, stock/workpiece rendering, 3D
  editing.

## Capabilities

### New Capabilities

(ADDED requirements — the underlying capabilities already exist as change
deltas; these add/revise requirements to them.)

- `gcode-editor`: adds the file-dialog last-directory requirement.
- `toolpath-preview`: adds the 3D-only depth-view requirement (supersedes the
  2D-specific wording of the bed-rectangle and click-sync requirements from
  `java-javafx-stack` on this branch; see its notes.md).

## Impact

- **Code**: `App` (dialog wiring), new `view/Preview3DView` becomes the only
  preview, `view/PreviewView` (2D) **deleted**, `MainWindowController` +
  FXML (no toggle), tests updated, README.
- **Dependencies**: none new — JavaFX's built-in 3D scene graph.
- **Ecosystem**: config gains a `last_open_dir` key (additive, YAML).
