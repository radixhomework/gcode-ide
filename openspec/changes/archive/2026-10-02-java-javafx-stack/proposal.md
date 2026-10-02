# Proposal: java-javafx-stack

## Why

The chosen stack (Python/PySide6, mirroring `grbl-machine-controller`) optimizes for cross-project idiom, but the maintainer has written Java for over a decade — fluency that this project currently leaves on the table. Rebuilding the IDE core on Java/JavaFX with a clean MVC separation between the UI and backend services puts phase 1 and every later phase (SVG import, tool library, 3D preview) on the stack the maintainer owns best. The Python realization stays parked on the `feat/python-design` branch as a reference implementation.

## What Changes

This branch (`feat/java-design`) is a greenfield build; the change supersedes `gcode-ide-core` on this branch by delivering the same phase-1 product scope on the Java stack.

- Java 25 LTS + JavaFX application: Maven project (`pom.xml`, `src/main/java`, `src/test/java`) with a `gcode-ide` launcher, mirroring the phase-1 scope (document lifecycle, GRBL editor, parser, 2D preview, machine profiles, statistics/warnings).
- **MVC architecture with backend services**: a pure-Java model and service layer (parsing, profiles, statistics/warnings, document, config — no JavaFX imports, plain JUnit) behind JavaFX views (code editor, toolpath canvas, status bar) and controllers that wire them.
- Editor built on RichTextFX (`CodeArea`): line-number gutter, syntax coloring via a pure word-classification helper, current-line tracking, goto-line.
- Preview as an immediate-mode JavaFX `Canvas`: millimeter coordinates, Y-up transform, depth-shaded cuts, dashed rapids, out-of-bed marking, click↔line sync, stale retention of the last good toolpath.
- Machine-profile YAML format is **unchanged** (same fields, millimeters, mm/min) — the cross-repo interchange contract with `grbl-machine-controller` survives the stack switch. The example preset (renamed generic in 3d-fixes-docs-debrand) and `demo.nc` carry over.
- Testing: JUnit 5 for the model layer (same scenarios as the Python suite, including the incremental-inches, arc-tolerance, and warning cases); TestFX for GUI smoke tests; jpackage for distribution.
- Updates `openspec/config.yaml` project context to describe the Java stack (it still describes Python).

**Explicit non-goals:** machine control (owned by `grbl-machine-controller`); SVG import and generation (phase 2); tool library (phase 3); 3D preview (phase 5); migrating or deleting the `feat/python-design` branch.

## Capabilities

### New Capabilities

The four capabilities restate `gcode-ide-core`'s behavioral contracts — the product behavior is identical; the realization is Java/JavaFX. Paths reuse the established organization; they are `ADDED` deltas because no main specs exist yet.

- `gcode-editor`: Document lifecycle (new/open/save) and the editing experience — syntax coloring for GRBL G-code, line numbers, unsaved-change tracking.
- `gcode-parsing`: Interpreting GRBL-dialect G-code text into a toolpath model with line attribution, tolerant of comments and unsupported-but-harmless words, with explicit diagnostics for what cannot be interpreted.
- `toolpath-preview`: 2D top-down visualization of the parsed toolpath against the machine bed, with bidirectional editor↔preview selection sync and live updates, plus toolpath statistics and safety warnings.
- `machine-profiles`: Machine definitions (bed size, feed limits, safe-Z) as YAML data files with a built-in example preset and user-selectable active profile.

### Modified Capabilities

(none — `openspec/specs/` is empty)

## Impact

- **Code**: entire Java application is new on this branch (`pom.xml`, `src/`, `assets/`); no existing code on this branch is touched. The Python implementation remains untouched on `feat/python-design`.
- **Dependencies**: Java 25 LTS; JavaFX 25 (controls/graphics); RichTextFX (code editor component); SnakeYAML (profiles/config); Lombok (compile-time boilerplate in services/controllers); SLF4J + Logback (logging); JUnit 5, TestFX, Monocle headless platform (tests); javafx-maven-plugin (dev runs), jpackage (distribution).
- **Ecosystem**: handoff to `grbl-machine-controller` (Python) stays a `.nc` file plus the shared plain-YAML machine-profile format — no shared code in either direction.
- **OpenSpec**: `gcode-ide-core` is superseded on this branch (archive or drop it when this change lands); `openspec/config.yaml` context is updated to the Java stack.
- **Later phases build on this**: the pure-Java parser becomes the round-trip test rig for the phase-2 SVG generator; JavaFX's 3D scene graph is the natural home for the phase-5 3D preview.
