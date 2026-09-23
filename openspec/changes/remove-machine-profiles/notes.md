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
