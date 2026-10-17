# Proposal: svg-import-wizard

## Why

The Open menu only reads plain G-code, so the drawing-to-toolpath half of the
roadmap (phase 2: SVG import) is still missing: today a user with a drawing
has no path into the IDE except hand-writing G-code. An Import Image entry
that accepts SVG and walks the user through a short wizard turns the IDE into
a drawing-to-G-code tool while keeping generation out of the parser's way —
the parser becomes the round-trip test rig for the generated code, exactly as
planned in the original design.

## What Changes

- New **File > Import Image...** menu entry: opens an image file dialog
  (SVG filter for now — the entry name anticipates raster formats later) and
  launches an import wizard.
- **Import wizard**: collects the import parameters over the loaded drawing —
  target size (scale), placement (free X/Y - the app is
  machine-profile-free), cutting depth as passes of a chosen depth-per-pass,
  cutting feed, rapid (safe) height, spindle speed — and reports
  unsupported/ignored SVG constructs as warnings.
- **SVG-to-G-code generation** (pure model): interprets the SVG's stroked
  geometry (paths, basic shapes) into GRBL G-code in millimeters — open
  paths become open cut runs, closed shapes become closed loops, with rapid
  moves between runs and plunges between depth passes.
- The generated program opens in the editor as a **new, unsaved document**
  (default name from the SVG file), flowing through the existing live
  preview, statistics, and warnings.
- **Round-trip validity**: generated code parses with the existing GRBL
  parser and reproduces the imported geometry within a stated tolerance —
  verified by tests, per the roadmap.

**Explicit non-goals:** raster image import (phase 4); fill/hatch of filled
areas (phase 5 fill-out); multi-tool or tool-diameter compensation; emitting
G2/G3 arcs (output is tolerance-flattened polylines); editing geometry in the
wizard; SVG transforms beyond the supported subset (unsupported ones warn and
skip).

## Capabilities

### New Capabilities

- `svg-import`: The Import Image entry, the wizard, SVG-to-G-code generation
  (supported geometry, warnings), and round-trip validity of the generated
  program.

### Modified Capabilities

(none — Open stays plain-G-code-only; the new entry is a separate flow)

## Impact

- **Code**: new pure `model/svg/` package (path-data parser, geometry,
  generator, no JavaFX), a JavaFX wizard dialog, `MainWindowController`/FXML
  menu wiring, `App` chooser wiring, tests (pure generator + round-trip + a
  wizard smoke test), README. Note (remove-machine-profiles): placement is
  free X/Y without bed clamping; round-trip asserts bounds containment.
- **Dependencies**: none new — SVG XML via the JDK's built-in parser;
  geometry math hand-rolled.
- **Ecosystem**: none — output is ordinary `.nc` G-code in the existing
  dialect; profiles and machine handoff unchanged.
