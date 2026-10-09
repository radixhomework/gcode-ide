# Tasks: ui-professional-styling

## 1. Token layer

- [x] 1.1 Create `src/main/resources/app.css` with the token definitions (`.root, .tokens` block per design D1) and the shared component classes (`status-bar`, `status-text`, `warning-chip`, `doc-popup`, `stale-badge`, completion list treatment); attach it from `App`'s scene, the editor `CodeArea`, and the preview wrapper (with the `tokens` class); verify `mvn compile` and that all existing tests stay green
- [x] 1.2 Move the inline styles (FXML status bar, doc popup, stale badge) to the token-driven classes; verify the grep for inline `setStyle`/`style=` in view code comes back clean

## 2. Editor

- [x] 2.1 Set the monospace font on the `CodeArea` (candidate list per design D3, 13 px); behavior test asserts the chosen family is monospace (candidate set or `Monospaced` fallback) and that the gutter area inherits the area font size
- [x] 2.2 Restyle the completion popup with a structured cell factory (bold code, muted description, token padding, no default cell insets) and the doc popup with the shared `doc-popup` class; TestFX asserts the popup still shows/inserts (existing tests) and a cell-rendering check asserts the two-part row structure

## 3. Status bar & 3D bridge

- [x] 3.1 Restructure the FXML status bar (`.status-bar` surface, `.status-text` muted labels, `.warning-chip` amber chip bound to non-empty text); integration tests keep passing (warning text semantics unchanged, visibility-only change asserted)
- [x] 3.2 Update `Preview3DView` color constants to the token values (bed/rapid/out-of-bed/highlight per D6) and the stale badge to the amber class; existing preview tests stay green

## 4. Wrap-up

- [x] 4.1 Full `mvn test` green; rebuild the app image and do a visual pass on the dev machine: aligned monospace editor, structured completion rows, amber chip status bar, consistent popups
