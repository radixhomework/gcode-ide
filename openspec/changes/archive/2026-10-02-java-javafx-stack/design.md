# Design: java-javafx-stack

## Context

Greenfield on `feat/java-design`: this branch has no code, only the OpenSpec setup. The Python/PySide6 realization of the same phase-1 scope lives on `feat/python-design` (see proposal.md — Why for the stack switch). The parser semantics, severity policy, statistics formula, and profile YAML schema are proven by that reference implementation and its 83-test suite; this design ports the contracts, not the code. The machine-profile YAML format is a cross-repo contract with `grbl-machine-controller` (Python) and is frozen.

## Goals / Non-Goals

**Goals:**

- MVC with a strict boundary: **model + backend services are pure Java** (no JavaFX imports) — plain-JUnit testable, reusable headlessly by later phases (phase-2 generator round-trip tests).
- Views are thin JavaFX; controllers wire views to services; services own state and expose observable properties the views bind to.
- Behavioral parity with the `feat/python-design` reference, verified by porting its test scenarios (including the exact demo.nc statistics).

**Non-Goals** (design-level): no FXML-per-view ceremony beyond the main window; no theming beyond JavaFX default Modena + CSS accent; no OSGi/modules beyond what jpackage needs; English-only strings; no code sharing with the Python branch (assets and YAML data only).

## Decisions

### D1: Java 25 LTS + JavaFX 25, Maven build
LTS JDK; JavaFX kept version-matched. Maven over Gradle: the maintainer's decade of Java is most likely Maven-shaped, and the build needs are simple (compile, test, run, jpackage via plugin). Alternatives: Gradle (fine, but no feature here needs it); Swing (rejected — see proposal; JavaFX also owns the phase-5 3D story via its scene graph).

### D2: Package layout — MVC with a service layer

```
pom.xml
src/main/java/dev/radixhomework/gcodeide/
  App.java                     Application entry (JavaFX Application)
  model/                       PURE JAVA — no JavaFX imports
    parsing/Move.java, ParseResult.java, Diagnostic.java, Severity.java, Spindle.java
    parsing/GCodeParser.java   tokenizer + modal interpreter + arc flattening
    profiles/MachineProfile.java
    preview/ToolpathStats.java, ToolpathWarning.java, Diagnostics.java
  service/                     backend services (own state, observable state via listeners)
    DocumentService.java       path/modified state, file IO, save prompts driven by controller
    ParseService.java          debounced re-parse: submit text -> ParseResult (last-good policy)
    ProfileService.java        built-in + user dir loading, active profile, config persistence
    StatsService.java          statistics/warnings for (ParseResult, MachineProfile)
  view/
    MainWindow.fxml (+ MainWindowController.java)
    GCodeEditorView.java       RichTextFX CodeArea wrapper: gutter, classification coloring
    PreviewView.java           Canvas rendering + interaction
    syntax/WordClassifier.java PURE JAVA classification helper (unit-tested)
  util/
    ConfigPaths.java           per-user config dir per OS
src/main/resources/assets/
  profiles/example_mill.yaml   generic example (was a vendor preset)
  examples/demo.nc             unchanged
src/test/java/...              JUnit 5 (model/service) + TestFX (view smoke)
```

Dependency direction: `view → service → model`; `model` imports nothing upward. `util` is importable by services only (ConfigPaths touches no JavaFX).

MVC mapping: **Model** = `model` + service state; **View** = FXML/`view` classes (no business logic, bind only); **Controller** = FXML controller + view classes' event handlers calling services. Services expose a tiny listener API (`addXxxListener`) instead of JavaFX `Property` types so `service/` stays JavaFX-free; views adapt them to JavaFX bindings in one place.

### D3: Parser is a straight port of the reference semantics
Two-stage pure function `parse(text, arcToleranceMm=0.01)`: regex word tokenizer (case-insensitive, `;`/paren comments, N words) then a modal interpreter (G0–G3, G90/G91, G20/G21 → mm canonicalization including feed conversion, F/S/M3/M4/M5, per-line attribution). Arcs: I/J start-relative and signed-R forms (R sign selects minor/major), sagitta-based flattening `n = ceil(θ / (2·acos(1 − tol/r)))`, `fromArc` tagging, exact commanded endpoint landing. Severity policy ported verbatim because the preview stale logic depends on it: ERROR = tokenizer-level failure (line skipped), WARNING = well-formed but uninterpretable (bad arc data), INFO = unsupported words/codes (parsing continues). All reference test scenarios port 1:1 — the incremental-inches (25.4, 25.4) case, quarter-circle tolerance bound, R-form center solving, golden program.

### D4: Editor on RichTextFX `CodeArea`
RichTextFX is the established JavaFX code-editor component (used by many IDE-like apps): built-in line-number gutter factory, per-segment styled text, current-paragraph tracking, mutable styling for live recoloring. `WordClassifier` (pure Java, mirrors the reference's `classify_line`: comment / G / M / axis-param / N / fallback with the same supported-code sets) is unit-tested standalone; a view helper applies CSS style classes per classification. `gotoLine(line)` selects the paragraph, moves caret, scrolls (`showParagraphInViewport`), and fires the current-line callback. Alternative considered: `TextArea` + manual `StyleRange`-style rewriting — verbose and slow on large docs; rejected.

### D5: Preview is an immediate-mode `Canvas`, not a scene graph
One `Canvas` in a `Pane`; `GraphicsContext` draws bed + moves + highlight each repaint. Coordinates are millimeters; the view installs an `Affine` with Y-negated scale (Y-up) plus zoom/pan; wheel zoom anchored at cursor, drag pan. Styling: rapids dashed gray (`setLineDashes`), cuts solid with hue ramp blue→red by Z between Z0 and deepest, out-of-bed segments red. Hit-testing: on click, inverse-transform to scene mm, then nearest segment within a 2 mm band (distance-to-segment; segments grouped by line number so any sub-segment of an arc selects its source line) → emit line-selected callback. Highlight = thicker translucent orange re-stroke of the current line's segments on the next repaint (no per-segment nodes to restyle). Stale = keep the last good `ParseResult` and repaint it with a "STALE" overlay label. Alternative: a `Group` of `Line` nodes — same scaling wall as QGraphicsItem-per-move; rejected.

### D6: Debounce via `PauseTransition` (300 ms), synchronous parse
Restarted on every text mutation; on fire, run the parser synchronously on the FX thread and repaint. Same rationale as the reference: pure-Java parsing of tens of thousands of lines fits easily inside the debounce interval; if >50k-line files become real, swap in a `Task`+executor behind the same service API (no spec change).

### D7: Estimated run time — identical formula
`Σ(cut_length / active_feed) + Σ(rapid_length / profile.maxRapid)`; cut moves with no active feed get a missing-feed warning and are counted at `max_cut`; labeled "estimated". The reference's demo.nc hand-verified figures (cut 118.0 mm, rapid 664.4 mm, est. 0.9 min) become the ported integration test's assertions — a strong cross-implementation parity check.

### D8: Profiles, config, and user directories
SnakeYAML for both machine profiles and app config — the YAML field names stay exactly the reference's (`name`, `bed.x/y/z`, `feeds.max_cut/max_rapid`, `safe_z`, millimeters). Built-in presets load from packaged resources (`assets/profiles/`); user profiles and `config.yaml` live in a per-user config dir resolved by `ConfigPaths` (`%APPDATA%\gcode-ide` on Windows, `~/Library/Application Support/gcode-ide` on macOS, `~/.config/gcode-ide` on Linux — the OS-specific equivalent of the reference's QStandardPaths AppConfigLocation). Config schema: `active_profile` name + `window_geometry` (x, y, w, h, maximized) as plain YAML — JavaFX stage geometry is serializable without byte blobs, unlike the Qt blob the reference base64-encoded. `ProfileService` persists the active profile on selection and geometry on close.

### D9: Testing strategy
Model/service: plain JUnit 5, no FX toolkit — the parser, profiles, statistics, and warnings suites are direct ports of the reference's pure-Python tests (same inputs and expected values). View layer: TestFX with the Monocle headless platform for CI (`-Dtestfx.headless=true` / `prism.order=sw`), covering the editor gutter/signal, preview sync both directions, debounce-driven item growth, stale flag, save/discard/cancel prompts (dialogs stubbed at the service boundary so no real modal can block a run — a lesson from the reference implementation's pytest-qt hang), and the demo.nc end-to-end statistics. `WordClassifier` and all parser helpers tested headlessly without TestFX.

### D10: Lombok for boilerplate, SLF4J + Logback for logging
**Lombok** generates the mechanical code in `service/`, `view`, and controllers: `@Slf4j` loggers, `@RequiredArgsConstructor` for constructor injection, `@Getter` where needed. The `model` package stays plain records — records already cover data carriers, so no processor magic (and no annotation-processing dependency) leaks into the pure-Java model. JDK 23+ requires annotation processing to be declared explicitly, so the Maven compiler plugin configures Lombok via `annotationProcessorPaths` rather than relying on the classpath. Alternative considered: hand-written accessors/loggers — pure noise for a solo maintainer; rejected.

**Logging**: SLF4J API with Logback as the single implementation, configured by `logback.xml` in resources (console appender at INFO, app logger `dev.radixhomework.gcodeide`). Services log operational events the user shouldn't miss but that don't warrant UI surfacing: profile-load skip reasons, parse diagnostics summary on stale transitions, config persistence failures, unexpected IO errors. Diagnostics that the specs require the *user* to see (warnings list, stale indicator) remain UI concerns driven by the model — logging is observability, not a reporting channel.

## Risks / Trade-offs

- [RichTextFX is a third-party component with moderate maintenance activity] → It is the de-facto standard for JavaFX code editors and its API surface used here (CodeArea, paragraph ops, style classes) is stable; worst case, the `GCodeEditorView` wrapper isolates it behind one class that could be reimplemented.
- [jpackage module path setup for JavaFX + RichTextFX is fiddly] → Use the `javafx-maven-plugin` for dev runs first; add jpackage only in the packaging task, with a non-modular (classpath) fallback via `jlink`-less `--module-path` layout if plugin automation fights back.
- [TestFX/Monocle headless quirks on Windows CI] → Tests run headed on the dev machine (the reference's approach); Monocle is the CI fallback, and view tests are kept to smoke level — the behavioral meat lives in headless model/service tests.
- [Canvas redraw cost on every caret move for the highlight] → Repaint is O(segments) vector drawing — trivial at preview scale; if it ever matters, cache the static pass into a snapshot image and only recomposite the highlight layer.
- [Two implementations of the same product on two branches] → Deliberate exploration; the YAML/`.nc` contracts are shared, specs are shared, and the Python branch is preserved as the executable reference for parity testing.
- [Solo-maintainer bandwidth across two stacks] → Accepted by decision (proposal — Why); `grbl-machine-controller` remains Python with no shared code, so there is no cross-stack coupling to maintain.
- [Lombok historically lags new JDK releases] → Pin the latest Lombok and declare it via `annotationProcessorPaths` (JDK 23+ requires explicit processing anyway); if a future JDK bump ever outruns Lombok, only services/controllers use it — the pure-Java `model` records compile without it, so the blast radius of removing it is small.

## Migration Plan

Greenfield branch — nothing to migrate. Rollback is deleting the new `src/`, `pom.xml`, and assets; no existing behavior depends on this change. Landing it implies archiving/dropping the superseded `gcode-ide-core` change on this branch and updating `openspec/config.yaml` context to the Java stack (a task in tasks.md).

## Open Questions

- Exact RichTextFX version pin (latest stable at implementation time) — deferrable to the skeleton task.
- Whether to later publish installers (`.msi`/`.dmg`) or just the raw jpackage app-image — packaging task verifies an app-image launcher; installer formats can be added without spec changes.
