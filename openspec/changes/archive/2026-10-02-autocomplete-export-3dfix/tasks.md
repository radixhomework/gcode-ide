# Tasks: autocomplete-export-3dfix

## 1. Fix the 3D preview visibility

- [x] 1.1 Rework `Preview3DView` to the orbit-camera pattern (camera in a rotation group at `translateZ = -distance`, yaw/pitch on the group, world unrotated); verify by a regression test that projects the bed center and corners into screen space and asserts they fall inside the viewport, and by launching the app with a real G-code file
- [x] 1.2 Re-check drag-rotate and wheel-zoom behaviors against the new camera (update the existing interaction tests) and confirm picking still works after the rework

## 2. Autocompletion + command documentation

- [x] 2.1 Implement pure `view/syntax/GCodeWords` (code → one-line description for every supported G/M code and axis/parameter word, `suggestionsFor(prefix)`); unit tests cover prefix matching, ordering, and that every documented code is in the parser's supported set
- [x] 2.2 Wire RichTextFX `AutoCompletePopup` into `GCodeEditorView` (popup on word-start typing, cells show `code — description`, acceptance inserts, typing through dismisses); TestFX test types `G`, asserts the popup appears with documented suggestions, accepts one, and asserts insertion + dismissal behavior

## 3. Save preview as image

- [x] 3.1 Implement the snapshot-to-file exporter (node snapshot → `SwingFXUtils` → `ImageIO`, PNG/JPG by extension) with an injected path-chooser callback; tests write both formats to temp files and assert valid non-empty images
- [x] 3.2 Add the File > "Save Preview Image..." action in the controller/FXML, reusing `last_open_dir` for the dialog and persisting it after a successful save; integration test drives the action with a stubbed chooser and asserts the file lands on disk

## 4. Wrap-up

- [x] 4.1 Update README (autocompletion, image export); run the full `mvn test` suite green; rebuild the app image and verify on the dev machine that the 3D preview shows the toolpath for a real file
