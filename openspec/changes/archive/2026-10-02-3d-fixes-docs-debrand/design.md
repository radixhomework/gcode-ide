# Design: 3d-fixes-docs-debrand

## Context

Builds on the 3D-only preview. Diagnosis (code analysis, pending runtime
confirmation): the perceived Z inversion and wrong-way arcs are two
compounding view bugs — the parser is ported with Python parity and passes
arc tests, but the 3D view shows it wrong.

## Decisions

### D1: Camera above the bed + orientation-preserving mapping (the real "Z inverted" bug)
The initial pitch is negative (`Rotate(-35, X_AXIS)`), which puts the camera
BELOW the bed plane (camera Y = distance·sin(pitch) < 0) — viewed from
underneath, down looks up. And `toWorld` maps bed Y → world +Z, which
mirrors the drawing in a top-down view (right-handed X-right/Y-up world
shows +Z toward the screen bottom). Fix: start with positive pitch (camera
above, looking down), drag directions adjusted so dragging up still tilts
naturally, and map bed Y → world **−Z** so a top view matches the canonical
2D orientation. Regression tests: camera position Y > 0 after reset;
chirality test asserting a known G2 arc's bed-space midpoint lands on the
same side as the 2D/top-view interpretation (e.g., `G2 X10 Y10 I10` from the
origin passes above-left of the center — hand-computable).

### D2: G2/G3 verification, not blind fixing
Add parser goldens with hand-computed arc midpoints in BED coordinates for
G2 and G3 in both I/J and R forms (the "to be checked" item made
executable). If the parser is confirmed correct (expected — Python parity),
the visual misinterpretation is fully explained by D1; if a golden fails,
fix the sweep sign in the interpreter and note it here.

### D3: Line rendering
Keep cylinders but thin them (radius ≈ 0.12 mm) so segments read as lines
through the toolpath center; the rounded-cap look disappears at that scale.
Alternative (single TriangleMesh of camera-facing quads) deferred unless
visual quality demands it — swapping is contained in `segmentBetween`.
Picking and hit tolerances unaffected.

### D4: Pan — diagnose with logging, then fix platform-robustly
The press-button dispatch passes deterministic tests but real input still
fails, and the one realistic robot-gesture test was flaky — strong hint that
secondary-button `MOUSE_DRAGGED` delivery is unreliable here. Plan: (1) add
a temporary event-log probe (press/drag/button flags) run on the dev machine
to capture what actually arrives; (2) apply the fix the log indicates —
candidates: deliver pan through `MOUSE_MOVED` tracking while a secondary
press is active, enable `startFullDrag()` and handle drag-over events, or
add middle-button pan as an additional path; (3) keep the deterministic
tests; acceptance is the user confirming pan by hand.

### D5: Hover / Ctrl+Q documentation
Reuse `GCodeWords`. Hover: track mouse position on the CodeArea; a 2 s
`PauseTransition` (restarted on movement) resolves the word under the cursor
via a caret-hit, shows a non-focus-stealing `Popup` near the mouse; hides on
movement/keys/focus loss. Ctrl+Q: key handler documents the word at (or
adjacent to) the caret — scan the paragraph left then right for the nearest
letter-run. Pure word-resolution helpers are unit-tested; popup behavior
TestFX-tested (show after simulated delay via a test hook, Ctrl+Q path,
unknown word shows nothing).

### D6: Debrand
Rename the built-in preset to `assets/profiles/example_mill.yaml`
("Example Mill", same dimensions — an example, not a target), strip
PROVER/Sainsmart from README, `openspec/config.yaml` context (target becomes
"a generic GRBL-driven desktop CNC mill"), and tests. Old `active_profile`
config values fall back to the first profile (existing unknown-name
behavior). Spec wording superseded via the machine-profiles delta.

## Risks / Trade-offs

- [D4 root cause may differ from all candidates] → the logging probe decides;
  no fix ships without a reproduced-then-fixed loop.
- [Thinner segments may reduce click target] → picking uses cylinder shape,
  0.12 mm radius still hit-testable via the existing 3D picking; verify in
  tests.
- [Hover popup timing feel] → 2 s is the requested default; constant is
  tweakable.

## Migration Plan

Additive plus two contained reworks (camera start/mapping, segment radius);
profile rename falls back gracefully. Rollback per item is independent.

## Open Questions

- Exact pan fix mechanism — resolved by the D4 probe during apply.
