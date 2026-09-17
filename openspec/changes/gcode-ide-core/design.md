# Design: gcode-ide-core

## Context

Greenfield application; the repo contains only a README and this OpenSpec setup. The companion project `radixhomework/grbl-machine-controller` (Python 3.9+, PySide6, pytest/ruff, PyYAML, PyInstaller) establishes the conventions and skill set this project should mirror. The machine is a GRBL-driven Sainsmart PROVER 3018; machine control is explicitly out of scope (see proposal.md — Why).

## Goals / Non-Goals

**Goals:**

- A Qt-thin architecture: all domain logic (parsing, profiles, statistics/warnings) is pure Python with no Qt imports, so it is testable without a GUI and reusable by later phases (SVG generator round-trip tests).
- Live preview trust loop: edit → re-parse (debounced) → re-render, with line↔segment sync.
- Machine profiles as plain YAML interchange readable by grbl-machine-controller.

**Non-Goals** (design-level, beyond proposal scope):

- No multi-document tabs, no theming beyond the system palette, English-only UI strings.
- No threading for parsing in this phase (see Risks).
- No incremental reparsing — full reparse per debounce tick.
- No persistence beyond app config YAML (no SQLite in this change).

## Decisions

### D1: Python ≥ 3.9 + PySide6, mirroring the controller's conventions
Same stack, layout, and tooling as `grbl-machine-controller`: `app/` package, entry point `app.main:main`, `pyproject.toml` (setuptools, dynamic version from `app.__version__`), pytest + ruff, PyYAML. Alternatives considered: Tauri/Rust (best-in-class SVG libs but new language for the maintainer), Electron/TS (heavy for a "small IDE"). Fluency and one idiom across both CNC tools win.

### D2: Module layout with pure-Python core

```
app/
  main.py               entry point, QApplication bootstrap
  main_window.py        QMainWindow: actions, splitter layout, wiring
  document.py           document state (path, modified flag) + file IO
  editor/gcode_editor.py   QPlainTextEdit subclass: gutter, highlighter, goto-line API
  parsing/parser.py     tokenizer + modal interpreter (pure Python)
  parsing/model.py      Move, Diagnostic, ParseResult dataclasses
  preview/preview_view.py QGraphicsView/QGraphicsScene rendering + selection sync
  preview/diagnostics.py  statistics + out-of-bed/feed warnings (pure Python)
  profiles/profile.py   MachineProfile dataclass + validation (pure Python)
  profiles/loader.py    built-in asset + user-directory loading
assets/profiles/prover_3018.yaml
tests/
```

Dependency direction: `main_window` → (editor, preview, parsing, profiles); `preview` → (parsing, profiles); pure modules import nothing from Qt.

### D3: Parser as a two-stage pure function
Stage 1 tokenizes a line into words (`letter + number` regex, case-insensitive), stripping `;` and parenthesized comments. Stage 2 runs a modal state machine (position, motion mode G0–G3, G90/G91, G20/G21, F, spindle M3/M4/M5). Signature: `parse(text, arc_tolerance_mm) -> ParseResult(moves, diagnostics)`.

- **Canonicalization:** model is mm / absolute / Y-up. `G20` multiplies by 25.4; `G91` accumulates. No axis flip — bed coordinates are G-code coordinates (any SVG Y-flip belongs to the Phase 2 producer, not the parser).
- **Arcs:** `G2`/`G3` in `I J` form (offsets relative to arc start, per GRBL) and `R` form (solve center from radius; R sign selects minor/major arc). Flattened to linear sub-segments with count derived from the sagitta: `n = ceil(θ / (2·acos(1 − tol/r)))`, default tolerance 0.01 mm as spec'd.
- **Tolerance:** any unrecognized word or known-but-unsupported code (e.g., `G43`, `M8`) records an INFO diagnostic and the line is otherwise consumed; malformed numbers or an arc with neither `I/J` nor `R` record a WARNING and the line is skipped — matching the parsing spec's error scenarios.

### D4: Toolpath model
`Move(kind: RAPID|CUT, start: xyz, end: xyz, line: int, feed: float|None, from_arc: bool)` — flat list, arcs pre-flattened but tagged (Phase 2's generator tests will want to know provenance). `Diagnostic(line, severity: INFO|WARNING|ERROR, message)`. Statistics and warnings are computed from `(ParseResult, MachineProfile)` by `preview/diagnostics.py`, never stored in the model.

### D5: Preview via QGraphicsView, scene units = millimeters
Bed drawn from the active profile; the view applies a Y-flip transform so scene +Y is machine +Y. One `QGraphicsLineItem` per move (arc sub-segments included): rapids dashed gray, cuts solid with color lerped by Z depth between deepest and Z0. Per-item `data(line)` carries source-line attribution for sync. Pan = rubber-band drag, zoom = wheel (QGraphicsView built-ins). Clicking an item emits `lineSelected(int)`; the editor exposes `gotoLine(int)` and `currentLineChanged`. Editor→preview highlight via a movable highlight item rather than re-styling segments (cheap on every cursor move).

### D6: Live re-parse, debounced, synchronous
`QTimer` (300 ms) restarted on `textChanged`; on tick, parse + recompute diagnostics + rebuild scene items. Synchronous is acceptable at this scale: pure-Python parsing handles tens of thousands of lines well within the debounce interval. On parse input that yields ERROR diagnostics (nothing parseable new), keep last good scene + "stale" badge, per the preview spec.

### D7: Estimated run time formula (approximation, labeled as such)
`Σ(cut_length / active_feed) + Σ(rapid_length / profile.max_rapid)`. Cut moves with no active feed (violating GRBL's requirement) get a WARNING and are counted at `max_cut`. Acceleration is ignored — the status bar labels the figure "estimated".

### D8: Machine profile YAML schema (interchange contract)

```yaml
name: PROVER 3018
bed: { x: 300.0, y: 180.0, z: 45.0 }   # mm, travel envelope
feeds:
  max_cut: 800.0    # mm/min
  max_rapid: 1000.0
safe_z: 5.0
```

Always millimeters, flat field names, additive-only evolution (a `format:` version field can be added later if it ever breaks). Built-in presets live in `assets/profiles/` shipped with the app; user profiles are discovered in `QStandardPaths(AppConfigLocation)/gcode-ide/profiles` (Qt-native, avoids a new dependency). App config (active profile, window geometry) is a sibling `config.yaml`, mirroring the controller's config style.

### D9: Test strategy
Pure-core pytest suites: parser fixtures (linear, arcs both forms, units/modes, unsupported words, malformed lines), profile validation cases, statistics/warnings given synthetic models + profiles. GUI-level: `pytest-qt` (dev dependency) for editor modified-state, save prompts, and preview sync smoke tests. Highlighter logic tested by unit-testing its word-classification helper, not rendered colors.

## Risks / Trade-offs

- [Huge files stall the UI during synchronous parse/rebuild] → Debounce caps reparse rate; if files >50k lines become real, move parsing to a worker `QThread` behind the same debounce (no spec change).
- [Estimated time diverges from GRBL's planner on short segmented paths] → Figure is labeled an estimate; acceleration modeling explicitly deferred.
- [Per-segment QGraphicsItems get heavy at very high sub-segment counts] → Arc tolerance bounds sub-segment counts in practice; if needed, batch segments into few items and keep a separate line→segment index for hit-testing.
- [Profile schema is a cross-repo contract from day one] → Field names chosen conservative and additive-only; grbl-machine-controller adoption is optional, not required, for this app to function.
- [PySide6 wheel size / packaging heft] → Accepted; consistent with the controller's stack and the maintainer's familiarity.

## Migration Plan

Greenfield — nothing to migrate. Rollback is trivial (delete the new `app/`, `tests/`, `assets/` trees); no existing behavior depends on this change.

## Open Questions

- Exact stock feed caps for the PROVER 3018 preset (800/1000 mm/min are safe starting values) — the profile is user-editable data, so this can be tuned any time without code changes.
- Whether later phases want a `format:` version field in profiles — decide when a second consumer (the controller) actually adopts the format.
- Dark theme / custom color ramp preferences — system palette suffices for now.
