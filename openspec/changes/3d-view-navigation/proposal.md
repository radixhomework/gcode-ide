# Proposal: 3d-view-navigation

## Why

Standard 3D-CAD navigation muscle memory: wheel zoom and left-drag orbit exist
already, but there is no way to pan — sliding the whole scene parallel to the
screen to center an area of interest. Users inspecting where a toolpath sits
on the bed need all three motions without reaching for reset controls.

## What Changes

Formalizes the 3D preview's camera controls as one scheme and adds the
missing piece:

- **Wheel**: zoom in/out (existing).
- **Left-drag**: orbit around the bed center (existing).
- **Right-drag** (new): pan — translate the view straight, parallel to the
  screen, without rotating; speed coupled to the current zoom so fine control
  works zoomed in. The right button must not open a context menu.
- Panning is transient view state: it does not affect the toolpath, parsing,
  or saved data; a later "reset view" can restore the default framing (out of
  scope here, recorded as an open question).

## Capabilities

### New Capabilities

(ADDED requirement; the capability exists as deltas from prior changes —
this supersedes the drag-only wording of the earlier rotation requirement on
this branch by stating the full navigation scheme.)

- `toolpath-preview`: the unified 3D navigation requirement (wheel zoom,
  left-drag orbit, right-drag pan).

## Impact

- **Code**: `Preview3DView` only — a pan offset applied to a shared ancestor
  of the content and camera rig, right-button drag handling with context-menu
  suppression, zoom-coupled pan speed; tests for the new interactions.
- **Dependencies**: none. **Ecosystem**: none.
