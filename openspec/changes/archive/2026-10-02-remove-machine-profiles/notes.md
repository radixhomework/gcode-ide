# Notes

- Archive prerequisite: this change's toolpath-preview delta uses MODIFIED
  and REMOVED operations, which require the target main spec to exist.
  Main specs are currently empty (no change has been archived yet), so the
  completed changes (java-javafx-stack, preview3d-recent-dir,
  autocomplete-export-3dfix, 3d-view-navigation, 3d-fixes-docs-debrand — and
  gcode-ide-core as superseded/dropped) must be archived BEFORE this change.
  After that consolidation the deltas apply cleanly.
- svg-import-wizard (pending, unimplemented) is amended by this change's
  task 4.1: placement becomes a free offset (no bed clamp), the round-trip
  bed-containment assertion becomes a bounds check.
- Stale `active_profile` keys in existing user config.yaml files are ignored,
  not deleted.

- Post-apply fix (review): the 3D view rendered Z-inverted. Root cause
  confirmed by rendered probes: the yaw/pitch group rig sent the camera
  below the content plane with an inverted up vector (the analytic sign
  conventions for JavaFX Rotate did not match its effective behavior).
  Fix: negative pitch places the camera above; an added 180-degree roll
  un-flips the camera's up; the visibility regression check now tests
  world-space corners through the actual toWorld mapping.
- Pan persistence: applyCamera() was resetting panTranslate from the stale
  `pan` field, wiping the pan on the next dolly/orbit; the field is now
  synced on every pan drag.
