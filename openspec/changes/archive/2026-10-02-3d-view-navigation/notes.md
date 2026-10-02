# Notes

- Implemented and verified (2026-09-20): pan group wrapping content + camera
  rig, pure `panDelta` helper, right-drag pan, left-drag orbit, wheel zoom,
  context menu suppressed over the preview. `mvn test` 99/99 green.
- Post-apply fix: real Windows `MOUSE_DRAGGED` events can omit the
  button-down flags, so dispatch now uses the button recorded at
  `MOUSE_PRESSED` (the original `isSecondaryButtonDown()` check made
  right-drag pan a no-op with real input while synthetic-flag tests passed).
  Right-click release no longer triggers segment picking.
- Test note: a TestFX robot-gesture test reproduced the flag bug but proved
  flaky; replaced by a deterministic regression test that fires a
  secondary-button press followed by flag-less drag events (the real-world
  shape). 3D suite verified stable across repeated runs.
- Open question (deferred): a "reset view" action to restore default framing
  after panning.
