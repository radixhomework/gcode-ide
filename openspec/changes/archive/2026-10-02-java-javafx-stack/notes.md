# Notes

- `gcode-ide-core` is superseded on this branch: `java-javafx-stack` delivers
  the same phase-1 scope on the Java stack. Archive candidate once this
  change is archived (its Python realization is preserved on the
  `feat/python-design` branch).
- Verified on the dev machine (Windows, Zulu JDK 25.0.4.1, Maven 3.9.16):
  `mvn test` 80/80 green; jpackage app-image launcher
  (`dist-java/gcode-ide/gcode-ide.exe --version`) prints `gcode-ide 0.1.0`
  and exits 0.

- Supersession note (2026-09-20): the 2D Canvas preview requirement wording
  (bed rectangle, 2D click hit-testing) is superseded on this branch by the
  `preview3d-recent-dir` change — the preview is 3D-only, with picking done
  via SubScene picking. The behavioral requirements (distinct move styles,
  depth shading, bed context, bidirectional sync, stale handling) carry over
  unchanged in 3D form.
