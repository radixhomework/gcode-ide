# Tasks: java-javafx-stack

## 1. Maven skeleton & tooling

- [ ] 1.1 Create `pom.xml` (Java 25, JavaFX 25 controls/graphics, RichTextFX, SnakeYAML, Lombok declared via `annotationProcessorPaths`, SLF4J + Logback with a `logback.xml` console appender; test deps JUnit 5, TestFX, Monocle; `javafx-maven-plugin` run goal) plus `src/main/java`/`src/test/java` trees, `App.java` launching a bare `Stage`, and `.gitignore` for `target/`; verify `mvn compile` (Lombok-processed) and `mvn javafx:run` open an empty window
- [ ] 1.2 Add an `App.version` constant + a `--version` handler that prints and exits cleanly without starting FX; version smoke test asserts it; verify `mvn test` and a `mvn check`-style verification (compiler warnings clean) both pass
- [ ] 1.3 Update `openspec/config.yaml` project context to describe the Java stack (Java 25, JavaFX, Maven, Lombok, SLF4J/Logback, MVC layout); verify the YAML parses and no Python references remain in the context block

## 2. Machine profiles (pure Java model)

- [ ] 2.1 Implement `MachineProfile` record + validation (positive bed extents/feed limits, safe-Z non-negative; rejection reason names the `bed.x`/`feeds.max_cut`-style dotted field); unit tests cover a valid profile and one rejection per invalid field, ported from the reference suite
- [ ] 2.2 Implement SnakeYAML load/serialize (`fromYaml`/`toYaml`) and create `src/main/resources/assets/profiles/prover_3018.yaml` (bed 300×180×45 mm, `feeds.max_cut: 800`, `feeds.max_rapid: 1000`, `safe_z: 5`, field names unchanged); test loads the packaged preset and asserts every documented field plus YAML round-trip
- [ ] 2.3 Implement `ProfileService` (built-in resources + user directory) and `util/ConfigPaths` (per-OS config dir: `%APPDATA%\gcode-ide`, `~/Library/Application Support/gcode-ide`, `~/.config/gcode-ide`), `@Slf4j`-logging each skip reason; `@TempDir` tests verify a dropped valid profile appears and a malformed one is skipped with a reason while others still load

## 3. G-code parser (pure Java model)

- [ ] 3.1 Implement `Move`/`Diagnostic`/`ParseResult` records and the word tokenizer (case-insensitive `letter+number`, `;` and parenthesized comments, `N` words); tokenizer unit tests cover comment stripping and mixed-case input
- [ ] 3.2 Implement the modal interpreter in `GCodeParser` (`G0`/`G1` with modal continuation, `G90`/`G91`, `G20`/`G21` canonicalization to mm including feed conversion, `F`/`S` and `M3`/`M4`/`M5` tracking, per-line attribution); tests assert the incremental-inches scenario ends at (25.4, 25.4), feed modality, spindle association, and line attribution
- [ ] 3.3 Implement arc support (`G2`/`G3`, `I/J` start-relative and signed-`R` forms, sagitta-based flattening with 0.01 mm default tolerance, `fromArc` tagging, malformed-arc skip diagnostics with the ERROR/WARNING/INFO severity policy); tests assert the quarter-circle tolerance bound, R-form center solving (minor and major arcs), and the bad-arc diagnostic scenario
- [ ] 3.4 Port the unsupported-word tolerance tests (`G43`, `M8`, `T1` → INFO, parsing continues) and the golden end-to-end fixture test combining linear moves, arcs, comments, units/mode switches, and one unsupported code — expected values copied from the reference suite

## 4. Statistics & warnings (pure Java model)

- [ ] 4.1 Implement `ToolpathStats`/`Diagnostics` (cut bounding box, cut/rapid distances, estimated time with missing-feed moves counted at `max_cut`); unit tests match the hand-computed values ported from the reference synthetic toolpath
- [ ] 4.2 Implement out-of-bed and excessive-feed warnings against a `MachineProfile` with offending line lists; tests cover the X 350-on-300 mm bed and F 2000-vs-800 cap scenarios from the specs

## 5. Editor view (MVC: view layer)

- [ ] 5.1 Implement `GCodeEditorView` wrapping a RichTextFX `CodeArea`: line-number gutter factory, current-line highlight, caret line/column callback; TestFX smoke test verifies the gutter renders and the caret callback fires on cursor moves
- [ ] 5.2 Implement `WordClassifier` (pure Java: comments, G/M words, axis/parameter words, `N` numbers, fallback style) with the same supported-code sets as the reference; unit-test on `N10 G1 X10 Y20 F600 ; first pass` and an unknown-word line, then wire it into the CodeArea with CSS style classes
- [ ] 5.3 Implement `gotoLine(line)` (select paragraph, move caret, scroll into view, fire the current-line callback); TestFX test asserts line 42 is selected and visible after the call

## 6. Preview view (MVC: view layer)

- [ ] 6.1 Implement `PreviewView` as a millimeter-coordinate `Canvas` with a Y-up `Affine`, wheel zoom and drag pan, and a bed rectangle from the active profile; test asserts the bed redraws when the profile changes (observable via a `repaintCounter`/bounds accessor)
- [ ] 6.2 Implement toolpath rendering (per-move strokes: rapids dashed gray, cuts solid with Z-depth hue ramp, out-of-bed segments red, current-line highlight overlay); headless render-logic tests (style/color decisions per move) assert dash style, ramp divergence at two depths, and red out-of-bed marking with recolor on profile switch
- [ ] 6.3 Implement bidirectional sync: click hit-testing (2 mm band, nearest segment → its source line) emits a line-selected callback; `setCurrentLine` triggers a highlight repaint covering all of that line's segments; TestFX tests verify both directions of the spec's sync scenarios
- [ ] 6.4 Implement stale handling (keep last good `ParseResult`, "STALE" overlay, stale callback); test verifies scene retention and the stale flag on ERROR vs WARNING-only parses

## 7. Services, main window & integration

- [ ] 7.1 Implement `DocumentService` (path/modified state, UTF-8 file IO, new/open/save/save-as with save/discard/cancel decision exposed as a callback the controller backs with an `Alert`); unit tests with a stubbed prompt cover save-clears-modified, save-as-switches-path, cancel-keeps-content, and open-sets-title
- [ ] 7.2 Implement `ParseService` debounced via `PauseTransition` (300 ms, restarted on text mutation, last-good policy on ERROR, stale transitions logged via SLF4J) and wire `MainWindowController`: text changes → parse → preview repaint + status refresh; TestFX test types an added `G1` move and asserts the preview segment count grows after the debounce, and that a syntax error sets the stale flag
- [ ] 7.3 Implement the status bar (caret line/column, cut bbox, distances, estimated time, warnings with tooltip) and the profile selector combo writing through to preview and diagnostics; tests assert the rendered statistics for a known program and recomputation on profile switch
- [ ] 7.4 Implement config persistence in `ProfileService`/`ConfigPaths` (`config.yaml`: `active_profile`, `window_geometry` as plain YAML fields, saved on selection/close, IO failures logged); test verifies the selected profile is active again after a config reload and geometry round-trips

## 8. End-to-end verification & packaging

- [ ] 8.1 Copy `assets/examples/demo.nc` from the reference (G0/G1/G2/G3, units and mode switches, one deliberate out-of-bed move); TestFX integration test opens it and asserts the reference's verified statistics end to end (bbox 10.0–75.0 × 10.0–30.0, cut 118.0 mm, rapid 664.4 mm, est. 0.9 min, out-of-bed warning citing lines 16–17)
- [ ] 8.2 Write the README quickstart (JDK 21, `mvn javafx:run`, `mvn test`, jpackage instructions) and a jpackage packaging config; verify a built app-image launcher starts the window (or exits cleanly with `--version`) on the dev machine
- [ ] 8.3 Run the full `mvn test` suite and confirm all model, service, and TestFX tests pass; update this change's tasks as completed and note in the change folder that `gcode-ide-core` is superseded on this branch (archive candidate)
