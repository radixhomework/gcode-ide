# Proposal: remove-machine-profiles

## Why

The machine-profiles capability — machine definitions as YAML (bed envelope,
feed caps, safe Z) driving the preview bed, safety warnings, and the profile
selector — is being removed from the product in every phase. The tool is a
general GRBL G-code workbench, not a machine-bound one; profiles coupled the
app to per-machine data the maintainer does not want to curate, and the
roadmap no longer includes them (the earlier "interchange format with
grbl-machine-controller" idea is dropped with it).

## What Changes

- **Capability removed**: the `machine-profiles` capability goes away —
  no built-in example profile, no user profile directory, no profile
  validation, no profile selector in the status bar, no active-profile
  persistence.
- **Dependent features change with it** (they were profile-powered):
  - the 3D preview no longer draws a machine bed; the toolpath renders in
    its own coordinates;
  - out-of-bed and excessive-feed warnings are removed (no envelope, no cap);
  - the estimated run time becomes a cut-only estimate (rapid moves are not
    timed — there is no rapid rate anymore);
  - a cut move without a commanded feed still warns (pure dialect check), and
    is no longer estimated in the time figure.
- **Downstream planning artifacts**: the pending (unimplemented)
  `svg-import-wizard` change loses its bed references — placement becomes
  free (no bed clamp); its artifacts are updated as part of this change.
- **Config**: `active_profile` disappears from `config.yaml`;
  window-geometry persistence is unaffected.
- Non-goals: introducing any replacement configuration format; changes to
  parsing, editing, or the preview's 3D navigation; roadmap phases 3–5 scope
  changes beyond dropping profile touchpoints.

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

(REMOVED and MODIFIED requirement deltas against the capabilities as specified
by the change deltas on this branch; main specs are still empty.)

- `machine-profiles`: the entire capability is removed (all five requirements).
- `toolpath-preview`: REMOVED — machine bed context, out-of-bed warning,
  excessive-feed warning; MODIFIED — toolpath statistics (cut-only estimated
  time).

## Impact

- **Code**: delete `model/profiles/`, `service/ProfileService` (profile
  halves), profile selector + machine label from the status bar/FXML/controller;
  strip profile parameters from `Diagnostics` (statistics signature, warning
  computation, out-of-bed checks); remove the bed box from `Preview3DView`
  (fit/orientation regressions re-targeted to the toolpath bounds);
  `ProfileService`'s config persistence moves to a small `ConfigService`
  (window geometry, `last_open_dir`); tests updated throughout; README and
  `openspec/config.yaml` context de-profiled; the pending `svg-import-wizard`
  artifacts updated (free placement).
- **Dependencies**: SnakeYAML stays (app config remains YAML).
- **Ecosystem**: the YAML profile interchange idea with
  `grbl-machine-controller` is abandoned — handoff remains a `.nc` file only.
