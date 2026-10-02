# Tasks: 3d-fixes-docs-debrand

## 1. 3D orientation and arc chirality

- [x] 1.1 Add hand-computed parser chirality goldens (G2/G3 midpoints, I/J and R forms, bed coordinates) and confirm or fix the interpreter; document the verdict in the change notes
- [x] 1.2 Fix the 3D view: positive initial pitch (camera above the bed), bed Y → world −Z mapping, drag directions adjusted; regression tests assert camera-above and that a known G2 arc's midpoint stays on the 2D-consistent side in a top view
- [x] 1.3 Thin segments to line-style rendering (radius ≈ 0.12 mm); update style/visibility tests and verify picking still passes

## 2. Right-drag pan (still broken)

- [x] 2.1 Add a temporary event-delivery log (press/drag events with button flags) and capture what the real app receives on the dev machine
- [x] 2.2 Apply the platform-robust pan fix indicated by the log (candidates in design D4), keeping deterministic tests green; confirm by hand on the dev machine

## 3. Hover and Ctrl+Q documentation

- [x] 3.1 Implement pure word-under-point / word-at-or-near-caret resolution helpers; unit tests cover hover positions, caret positions, and unknown words
- [x] 3.2 Wire the documentation popup: 2 s hover delay, Ctrl+Q shortcut, hide-on-move/press/focus; TestFX tests cover all three spec scenarios

## 4. Debrand

- [x] 4.1 Replace the built-in profile with `example_mill.yaml` ("Example Mill", same values); update `ProfileService` builtin list, tests, README, and the `openspec/config.yaml` context (generic GRBL desktop mill, no vendor/model)
- [x] 4.2 Grep the repo for PROVER/Sainsmark/Sainsmart remnants and clean; verify a stale `active_profile` config falls back to the example profile

## 5. Wrap-up

- [x] 5.1 Full `mvn test` green; rebuild app image; verify on the dev machine: depth direction, arc directions against the goldens, line-style toolpath, pan by hand, hover/Ctrl+Q docs, no branding
