# Design: svg-import-wizard

## Context

Phase-2 feature on the completed phase-1 IDE. The original design already
pinned the key constraint: the parser becomes the round-trip test rig for the
generator, and any SVG Y-flip belongs to the producer, not the parser. The
preview is 3D-only with bed X→X, bed Y→−Z, depth down; generated code is
ordinary GRBL in millimeters.

## Decisions

### D1: Pure generator package, JDK XML only
`model/svg/` is pure Java (no JavaFX), keeping the model boundary and
headless testing: `SvgDocument.load(bytes)` (DOM via `javax.xml.parsers`),
`SvgPathData` parser for path `d` strings (M/L/H/V/C/S/Q/T/A/Z, absolute and
relative), a small transform stack (translate/scale/rotate/matrix — the
common subset), shape → path conversion (rect, circle, ellipse, line,
polyline, polygon), and `GCodeGenerator` producing the program text. No
Batik — the JDK parser plus hand-rolled path data keeps dependencies at zero;
the path-data grammar is small and heavily unit-testable. Unsupported
constructs (unknown elements, unsupported transforms, non-stroked paint) are
collected as warnings, never exceptions.

### D2: Geometry model and flattening
Generator works on `SvgRun` lists (ordered points in millimeters, open or
closed). Curves (C/S/Q/T/A) are flattened adaptively to the same 0.01 mm
tolerance the parser uses for arcs, so the round-trip tolerance is a single
constant. Output polyline G1 (no G2/G3 emission — the tolerance-flattened
form is simpler and robust; true-arc output can be added later behind the
same generator API).

### D3: Program shape
Header `G21 G90 G17`; `M3 S<speed>`; per run: rapid to start at safe Z,
plunge to current pass depth (at cut feed), cut the run, retract to safe Z;
passes loop depth-per-pass down to the final depth; `M5` footer. Y-up: SVG Y
is flipped at generation time (SVG Y-down → bed Y-up), per the original
design's producer-side flip. Placement: the wizard computes a translate so
the scaled bounding box lands at the chosen spot (default: centered with a
5 mm margin), clamped to the bed — the free placement doubles as the
round-trip's inside-the-bed guarantee.

### D4: Wizard UI — one dialog, three steps in a pane
A JavaFX `Dialog` with a parameter grid (size width in mm with locked aspect,
placement X/Y or center, depth per pass, final depth, cut feed, safe Z,
spindle) plus a warnings list when the document has any. No in-wizard canvas
preview (the result opens in the main preview immediately; a mini-preview is
deferred). File > Import Image... wires through the same chooser pattern as
Open/Save (`PathChooser` callback, dialog starts in `last_open_dir`);
completion calls the controller's `loadText` after the existing
`DocumentService` unsaved-changes protection.

### D5: Round-trip tests as the acceptance spine
Pure tests: path-data parser goldens (each command, relative forms, arc
flags), flatten tolerance bound, generator parameter honoring (size, depth
passes, feeds), and the round-trip — generate from a test SVG, parse with
`GCodeParser`, assert zero ERROR diagnostics, containment within the
generated geometry's own bounds, and endpoint match within tolerance. One TestFX smoke test drives the menu entry to the
wizard with a stubbed chooser.

## Risks / Trade-offs

- [SVG surface area is huge (filters, text, gradients, use/defs…)] → Explicit
  supported subset with warnings; unknown elements are skipped and listed —
  the spec's warning scenario makes this behavior, not a bug.
- [Arc flag math (A command) is error-prone] → Port the standard endpoint-
  to-center conversion with goldens; flattening hides residual error.
- [Y-flip mistakes would mirror drawings] → Round-trip test asserts a known
  asymmetric drawing's orientation (a marker in the top-right quadrant stays
  top-right in bed coordinates).

## Migration Plan

Additive; rollback removes the menu entry and package. No data migrations.

## Open Questions

- Whether the wizard should later offer laser mode (M4, no depth passes) —
  deferred; parameters are a grid, easy to extend.
