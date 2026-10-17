# Notes

- G2/G3 verdict (task 1.1): the parser is CORRECT. Hand-computed midpoint
  goldens for G2 (I/J) and G3 (R) pass exactly — clockwise/counterclockwise
  chirality matches the GRBL interpretation in bed coordinates. The visual
  "wrong-way arcs" were the two view bugs below.
- View bugs fixed (task 1.2): the initial camera sat BELOW the bed plane
  (negative pitch), and bed Y mapped to +Z (mirrored). Now: positive pitch
  (camera above, Y = distance·sin(pitch) > 0, regression-tested), bed Y →
  −Z (orientation-preserving, top view matches the canonical 2D orientation,
  arc-side regression test), drag directions adjusted for the new mapping.
- Line rendering (task 1.3): segment radius 0.4 → 0.12 mm; segments read as
  thin lines through the tool path.
- Pan (task 2.x): a native-Robot probe with the event log
  (-Dgcodeide.eventlog=true) captured real secondary-button input —
  press/drag/release delivered with correct flags and pan applied. Dispatch
  hardened anyway: the initiating button decides, with the secondary flag and
  the middle button as additional pan paths. If pan still fails for you,
  run with -Dgcodeide.eventlog=true and share the EVT lines.
- Docs (task 3.x): hover (2 s) and Ctrl+Q popups reuse GCodeWords; words are
  letter+digits runs (G2, X10), unknown words show nothing, digit-stripping
  documents axis words (X10 → X). Coordinate-space lesson: CodeArea
  getCaretBounds() is SCREEN space; Popup.show takes screen coords; hit()
  takes node-local.
- Debrand (task 4.x): example_mill.yaml ("Example Mill", same dimensions)
  replaces the vendor preset; PROVER/Sainsmart scrubbed from code, README,
  config context, and the live spec deltas (gcode-ide-core kept untouched as
  the superseded historical record). Stale active_profile values fall back
  to the first profile.
- mvn test: 108/108 green. App image rebuilt and verified.
