# Design: 3d-view-navigation

## Context

Builds on `Preview3DView` (orbit camera: nested yaw/pitch groups, camera at
`translateZ = -distance`). Wheel zoom and left-drag orbit are implemented and
tested; the missing control is right-drag pan.

## Decisions

### D1: Pan = translating a shared ancestor of content and camera rig
Screen-parallel translation means content and camera must move together. Add
a `panGroup` that wraps BOTH the world content group and the camera rig
(yaw/pitch groups) in the SubScene root, carrying a single `Translate`. On
right-drag, update the translate along the camera's current right/up axes —
computed from the existing yaw/pitch/distance state (pure math, testable) —
scaled by `distance * dragFactor` so speed couples to zoom. Panning this way
keeps orbit, zoom, and picking math untouched (the bed stays at world origin;
the rig moves with the content). Alternative considered: moving the orbit
pivot — same effect, more state; rejected.

### D2: Right-button handling and context-menu suppression
Drag dispatch keys on the button that started the gesture, remembered from
`MOUSE_PRESSED` (secondary → pan, primary → orbit). (Amended during apply:
checking `isSecondaryButtonDown()`/`isPrimaryButtonDown()` inside
`MOUSE_DRAGGED` proved unreliable for real platform events on Windows —
they can arrive with no button flags set — so the remembered press button is
authoritative.) A consuming filter on `ContextMenuEvent.CONTEXT_MENU_REQUESTED`
over the SubScene suppresses the default menu; both drag modes consume their
events, and release-time picking fires only for the primary button.

### D3: Interaction state
Track `panX/panY/panZ` (or the Translate object) with getters for tests; a
pure static `panDelta(yawDeg, pitchDeg, distance, dxPixels, dyPixels)` returns
the world-space delta, unit-tested for axis correctness (screen-right stays
camera-right after any yaw/pitch).

## Risks / Trade-offs

- [Pan drifts content far from origin → picking/frustum tests unaffected
  (they use world coords), but the user can get lost] → accepted; a reset-view
  action is an open question for a later change.
- [Pixel-to-world scale is nominal, not DPI-exact] → acceptable for a preview
  control; factor chosen empirically.

## Migration Plan

Single-class change inside `Preview3DView`; rollback is removing the pan
filter and group. No data or spec migrations.
