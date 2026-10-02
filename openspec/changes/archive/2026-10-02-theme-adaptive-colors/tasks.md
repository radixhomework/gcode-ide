# Tasks: theme-adaptive-colors

- [x] 1.1 `app.css`: define the syntax palette variables (light defaults + `.dark` overrides per design D4) and apply the `dark` class plumbing via a small `UiTheme.applyDark(node, dark)` helper used on theme apply/restore for the scene root, editor, preview wrapper, and settings pane
- [x] 1.2 `editor.css`: consume the `-gd-syn-*` variables; unit tests already cover classification (unchanged); verify colors resolve by asserting the `dark` class lands on the editor and wrappers when a dark theme is applied
- [x] 1.3 `Preview3DView`: introduce `PreviewPalette` (LIGHT/DARK records), `setDark(boolean)` swapping and restyling live; static helpers take the palette; update the style/visibility tests to the parameterized helpers and add a test asserting the palette switches and materials recolor (via restyle counters/fields)
- [x] 1.4 Controller/App: call `UiTheme.applyDark` + `preview.setDark` on both theme paths (startup restore and settings commit), driven by `Theme.isDarkMode()`; integration test asserts the `dark` class toggles with Primer Dark/Primer Light and the preview palette flag follows
- [x] 1.5 README note (theme-adaptive syntax + preview colors); full `mvn test` green; rebuild the app image and verify by hand: Primer Dark gives dark editor palette and dark 3D canvas, Primer Light restores the light palettes, both live
