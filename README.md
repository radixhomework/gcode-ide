# G-Code IDE

A small desktop IDE for GRBL G-code: hand-edit programs with syntax coloring,
preview the toolpath in 3D, and — in later phases — generate code from SVG
drawings, text, and images. Works with any GRBL-driven desktop CNC mill via
machine-agnostic settings; machine control (jogging, streaming, probing)
lives in the sibling project
[grbl-machine-controller](https://github.com/radixhomework/grbl-machine-controller).

This branch (`feat/java-design`) is the Java 25 / JavaFX implementation
(MVC: pure-Java model & services, JavaFX views/controllers). The
Python/PySide6 reference implementation lives on `feat/python-design`.

## Quickstart

Requires JDK 25 (any LTS distribution) and Maven 3.9+.

```
mvn javafx:run        # start the IDE
```

Open `src/main/resources/assets/examples/demo.nc` from the app to see
editing, live preview, statistics, and warnings working together.

## The editor

The editor renders G-code in a monospace font with dialect-aware syntax
coloring (theme-adaptive palettes), a line-number gutter, and vertical +
horizontal scroll bars for long documents.

- **Autocompletion**: while typing a word, a popup lists the supported
  G/M codes and parameter words with one-line documentation — accept with
  **Enter or Tab**; dismiss by continuing to type.
- **Command documentation**: rest the mouse on a keyword for ~2 seconds, or
  press **Ctrl+Q** with the caret on/next to a word. The popup stays open
  while the mouse moves within the keyword.
- **Go to line**: **Edit > Go to Line...** (Ctrl+G).
- File dialogs remember the last used directory.

## The 3D preview

The preview is a 3D perspective view where pass depth is directly visible:
bed X/Y as the ground plane, deeper passes lower, cuts colored by a depth
ramp, rapids translucent. **Drag** rotates, **right-drag** (or middle-drag)
pans straight, the **wheel** zooms, and **clicking a segment** jumps the
editor to its source line. The canvas colors follow the selected theme's
family (light/dark).

**File > Save Preview Image...** exports the current preview as PNG or JPG.

## Settings

**File > Settings...** opens a settings window with **Apply / OK / Cancel**:
edits stage until Apply or OK commits them (Apply keeps the window open),
Cancel discards. The Appearance section selects the UI theme across the
installed AtlantaFX themes (Primer, Nord, Cupertino — light and dark — and
Dracula); the choice applies immediately on commit and persists. The editor
syntax palette and the 3D canvas adapt to the theme family automatically.

## Statistics & warnings

The status bar shows the caret position, the cutting bounding box, cut and
rapid distances, and an **estimated cutting time** (commanded cut feeds only;
rapids and feedless cuts are not timed — a feedless cut is flagged as a
dialect warning). The toolpath is machine-profile-free by design: no bed
envelope or feed-cap checks, so placement awareness stays with the user.

## Development

```
mvn test              # model/service (plain JUnit) + view (TestFX) suites
mvn javafx:run        # run the app
```

Architecture: MVC with a strict boundary — all domain logic (parsing,
statistics/warnings) is pure Java with no JavaFX imports, so it is testable
headlessly and reusable by later phases (the SVG-import generator's
round-trip tests). Lombok trims service/controller boilerplate; SLF4J +
Logback provide logging; AtlantaFX (Primer and friends) provides the flat
component theme. See `openspec/` for specifications.

## Packaging (jpackage)

`mvn package` builds a shaded uber-jar (JavaFX excluded — the runtime image
provides those modules). Fetch the JavaFX jmods once, then produce a
self-contained app image:

```
mvn package
mkdir -p target/package && cp target/gcode-ide-*.jar target/package/
curl -sL -o target/openjfx-jmods.zip \
  https://download2.gluonhq.com/openjfx/25/openjfx-25_windows-x64_bin-jmods.zip
unzip -o target/openjfx-jmods.zip -d target/
jpackage --type app-image --name gcode-ide \
  --input target/package --main-jar gcode-ide-<version>.jar \
  --module-path target/javafx-jmods-25 \
  --add-modules javafx.controls,javafx.fxml,java.logging \
  --java-options "--enable-native-access=javafx.graphics" --dest dist-java
dist-java/gcode-ide/gcode-ide.exe            # or --version to verify
```
