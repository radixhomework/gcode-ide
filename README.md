# G-Code IDE

A small desktop IDE for GRBL G-code: hand-edit programs with syntax coloring,
preview the toolpath against the machine bed, and — in later phases — generate
code from SVG drawings, text, and images. 
machine control (jogging, streaming, probing) lives in the sibling project
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
editing, live preview, statistics, and out-of-bed warnings working together.
The preview is a **3D perspective view** where pass depth is directly visible
(drag to rotate, wheel to zoom, click a segment to jump to its source line).
While typing, the editor offers **autocompletion with one-line command
documentation** for the supported G/M codes and parameter words.
**File > Save Preview Image...** exports the current preview as PNG or JPG.
File dialogs remember the last used directory.

## Machine profiles

Profiles are plain YAML (millimeters, mm/min) — a generic Example Mill preset ships
built-in under `src/main/resources/assets/profiles/` — plus any files you
drop into your user config directory (`%APPDATA%\gcode-ide\profiles` on
Windows, `~/Library/Application Support/gcode-ide/profiles` on macOS,
`~/.config/gcode-ide/profiles` on Linux). The same format is designed to be
read by `grbl-machine-controller` without shared code.

```yaml
name: Example Mill
bed: { x: 300.0, y: 180.0, z: 45.0 }
feeds:
  max_cut: 800.0
  max_rapid: 1000.0
safe_z: 5.0
```

## Development

```
mvn test              # model/service (plain JUnit) + view (TestFX) suites
mvn javafx:run        # run the app
```

Architecture: MVC with a strict boundary — all domain logic (parsing,
profiles, statistics/warnings) is pure Java with no JavaFX imports, so it is
testable headlessly and reusable by later phases (SVG generator round-trip
tests). Lombok trims service/controller boilerplate; SLF4J + Logback provide
logging. See `openspec/` for specifications.

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
