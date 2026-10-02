# Design: preview3d-recent-dir

## Context

Builds on the completed `java-javafx-stack` implementation. **Revised: the 3D
view replaces the 2D Canvas preview** (maintainer decision — depth is the
priority, one preview).

## Decisions

### D1: Last directory as a config key, applied in the dialog factory
`App` builds the open/save choosers; they read `last_open_dir` from
`ProfileService`'s config before showing and write it after a successful pick
(only if the directory still exists). Persisted in the existing `config.yaml`
via `updateConfig`.

### D2: 3D preview = SubScene + PerspectiveCamera + cylinder segments
`Preview3DView` wraps a `SubScene` (own camera) in a `StackPane`. World
mapping: bed X → X centered, bed Y → Z centered, G-code Z → Y (deeper is
lower). Moves are `Cylinder` segments (radius 0.4 mm) colored by a depth ramp
(blue at Z0 → red at deepest); rapids translucent gray, out-of-bed red,
current-line highlight bright orange diffuse. Bed = flat `Box`. Drag rotates
(yaw/pitch around bed center), wheel dollies. Shared color decisions live as
pure statics on `Preview3DView` (`depthColor`, `segmentColor`,
`deepestCutZ`) — `PreviewView` (2D) is deleted.

### D3: Picking replaces the 2D hit-test
SubScene mouse events carry a `PickResult`; on a click (press and release
within a small movement), the intersected node's `userData` (source line)
drives a `lineSelected` callback wired to `editor.gotoLine`. Synthetic-event
tests construct a `PickResult` targeting a known cylinder. Editor→preview
highlight via material restyle as before.

## Risks / Trade-offs

- [Cylinder count on huge programs] → fine at phase-1 scale; swap in one
  TriangleMesh behind the same API if needed (no spec change).
- [Precision work without 2D] → accepted by the maintainer; a 2D view can be
  reintroduced later as a toggle without spec changes beyond restoring the
  old requirement.

## Migration Plan

Rollback = restore `PreviewView` and the toggle from git history. No data
migrations.
