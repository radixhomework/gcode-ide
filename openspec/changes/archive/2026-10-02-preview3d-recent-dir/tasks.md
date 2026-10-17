# Tasks: preview3d-recent-dir

## 1. Last directory for file dialogs

- [x] 1.1 Add last-directory handling to `App`'s open/save choosers (read `last_open_dir`, apply as initial directory when it exists, persist after a successful pick); test verifies the key round-trips through config and that a missing directory is ignored
- [x] 1.2 Verify manually/dialog-level behavior via a wiring smoke test (chooser callback updates the config after a simulated pick)

## 2. 3D preview as the only preview

- [x] 2.1 Implement `view/Preview3DView` (SubScene + PerspectiveCamera, bed box from profile, world mapping bed X→X, bed Y→Z, Z→−Y); headless tests assert the mapping helper and that a known toolpath produces one segment node per move
- [x] 2.2 Implement segment rendering (cylinders: depth-ramp cuts, translucent gray rapids, red out-of-bed, orange highlight for the current line); tests assert node counts per kind and depth-color divergence, and that a -4 mm pass renders lower than a -1 mm pass (Y mapping)
- [x] 2.3 Implement interaction (drag rotates yaw/pitch around bed center, wheel dollies; events consumed) and the stale overlay label; TestFX smoke test asserts the rotation transform changes on drag and the stale flag shows
- [x] 2.4 Make the 3D view the only preview: delete `PreviewView` (2D) and its test, move the shared color decisions (`depthColor`, `deepestCutZ`, out-of-bed/rapid colors) into `Preview3DView` pure statics; port the style-decision tests
- [x] 2.5 Implement 3D picking: click on a segment emits `lineSelected(line)` from the picked cylinder's user data (press/release within a small movement); test fires a synthetic click with a PickResult targeting a known cylinder and asserts the emitted line

## 3. Integration

- [x] 3.1 Remove the 3D toggle: SplitPane holds editor + 3D preview only; wire profile/toolpath/current-line/lineSelected through the controller; update integration tests (segment counts, stale, highlight, profile switch via bed box)
- [x] 3.2 Update README (3D preview is the preview); run the full `mvn test` suite green
