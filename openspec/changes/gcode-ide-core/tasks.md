# Tasks: gcode-ide-core

## 1. Project skeleton & tooling

- [x] 1.1 Create `pyproject.toml` (setuptools, dynamic version from `app.__version__`, deps `PySide6`/`PyYAML`, dev extras `pytest`/`pytest-qt`/`ruff`, entry point `gcode-ide = app.main:main`) plus `app/__init__.py`, `app/main.py` stub, and package subdirs; verify `pip install -e ".[dev]"` succeeds and `python -c "import app; print(app.__version__)"` prints
- [x] 1.2 Configure pytest (`testpaths = ["tests"]`) and ruff in `pyproject.toml`; add a version smoke test under `tests/`; verify `pytest` and `ruff check .` both pass clean

## 2. Machine profiles (pure Python)

- [x] 2.1 Implement `MachineProfile` dataclass + validation in `app/profiles/profile.py` (positive bed extents/feed limits required, rejection reason names the offending field); unit tests cover a valid profile and one rejection per invalid field
- [x] 2.2 Implement YAML load/serialize and create `assets/profiles/prover_3018.yaml` (bed 300×180×45 mm, `feeds.max_cut: 800`, `feeds.max_rapid: 1000`, `safe_z: 5`, field names per design D8); test loads the preset and asserts every documented field
- [x] 2.3 Implement the loader (built-in assets + user directory under `QStandardPaths` AppConfigLocation `gcode-ide/profiles`, Qt call isolated in a thin wrapper); tests use tmp dirs to verify a dropped valid profile appears and a malformed one is skipped with a reason while others still load

## 3. G-code parsing (pure Python)

- [x] 3.1 Implement `Move`/`Diagnostic`/`ParseResult` dataclasses in `app/parsing/model.py` and the word tokenizer (case-insensitive `letter+number`, `;` and parenthesized comments, `N` words); tokenizer unit tests cover comment stripping and mixed-case input
- [x] 3.2 Implement the modal interpreter in `app/parsing/parser.py`: `G0`/`G1` with modal continuation, `G90`/`G91`, `G20`/`G21` canonicalization to mm, `F`/`S` and `M3`/`M4`/`M5` tracking, per-line attribution; tests assert the spec's incremental-inches scenario ends at (25.4, 25.4), feed modality, and line attribution
- [x] 3.3 Implement arc support (`G2`/`G3`, `I/J` start-relative and `R` forms, sagitta-based flattening with 0.01 mm default tolerance, `from_arc` tagging, malformed-arc skip diagnostic); tests assert the quarter-circle tolerance bound, `R`-form center solving, and the bad-arc diagnostic scenario
- [x] 3.4 Implement unsupported-word tolerance (e.g., `G43`, `M8` → INFO diagnostic, parsing continues); add a golden end-to-end parser fixture test combining linear moves, arcs, comments, units/mode switches, and one unsupported code

## 4. Statistics & warnings (pure Python)

- [x] 4.1 Implement statistics in `app/preview/diagnostics.py`: cut bounding box, cut/rapid distances, estimated time per design D7 (missing-feed moves warned and counted at `max_cut`); unit tests match hand-computed values on a synthetic toolpath
- [x] 4.2 Implement out-of-bed and excessive-feed warnings against a `MachineProfile` (offending line lists included); tests cover the X 350-on-300 mm bed scenario and the F 2000 vs 800 cap scenario from the specs

## 5. Editor widget

- [x] 5.1 Implement `app/editor/gcode_editor.py`: line-number gutter, current-line highlight, caret line/column reporting signal; pytest-qt smoke test verifies the gutter renders and the signal fires on cursor moves
- [x] 5.2 Implement the syntax highlighter with a pure word-classification helper (comments, G/M words, axis/parameter words, `N` numbers, fallback style); unit-test the helper on `N10 G1 X10 Y20 F600 ; first pass` and an unknown-word line, then wire it into the editor
- [x] 5.3 Implement `gotoLine(line)` (place caret, select, scroll to, and signal the change); test asserts line 42 is selected and visible after the call

## 6. Preview widget

- [x] 6.1 Implement `app/preview/preview_view.py`: `QGraphicsView`/`QGraphicsScene` in millimeter scene units with Y-up flip and a bed rectangle from the active profile; test asserts the bed item resizes when the profile changes
- [x] 6.2 Implement toolpath rendering: one line item per move, rapids dashed gray, cuts solid with Z-depth color ramp, `data(line)` attribution on every item; tests assert per-move item count, dash style on rapids, and line attribution
- [x] 6.3 Implement bidirectional sync: item click emits `lineSelected(line)`; a highlight item tracks `currentLineChanged`; pytest-qt tests verify both directions of the spec's sync scenarios
- [x] 6.4 Implement stale handling: on a `ParseResult` with no usable new state, keep the last good scene and expose a stale indicator; test verifies scene retention and the stale flag

## 7. Main window & integration

- [x] 7.1 Implement `app/document.py` and the main window's new/open/save/save-as actions with modified-state tracking and save/discard/cancel prompts on new, open, and close; pytest-qt tests cover save-clears-modified, save-as-switches-path, and cancel-keeps-content scenarios
- [x] 7.2 Wire debounced live re-parse (300 ms `QTimer`): `textChanged` → parse → preview rebuild + status refresh; pytest-qt test types an added `G1` move and asserts the preview item count grows after the debounce, and that a syntax error sets the stale indicator
- [x] 7.3 Implement the status bar: caret line/column, cut bbox, distances, estimated run time, and warning list (out-of-bed, feed-cap with source lines); profile selector writes through to preview and diagnostics; tests assert statistics rendering for a known program and recomputation on profile switch
- [x] 7.4 Implement app config (`config.yaml` under the same `QStandardPaths` config dir: active profile, window geometry); test verifies the selected profile is active again after a config reload

## 8. End-to-end verification & packaging

- [x] 8.1 Add `assets/examples/demo.nc` exercising `G0`/`G1`/`G2`/`G3`, units and mode switches, and one deliberate out-of-bed move; pytest-qt integration test opens it and asserts the spec's statistics and warning scenarios end to end
- [x] 8.2 Write README quickstart (venv, `pip install -e ".[dev]"`, `gcode-ide` run) and a PyInstaller spec mirroring `grblmc.spec`; verify a built launcher starts the window (or exits cleanly with `--version`) on the dev machine
