# Design: autocomplete-export-3dfix

## Context

Builds on the 3D-only preview (`preview3d-recent-dir`). Two additive features
plus one regression fix. Diagnosis for the fix is grounded in the code: the
user sees an empty preview with real files loaded while all tests pass,
because tests assert scene-graph content but never on-screen visibility.

## Decisions

### D1: Autocompletion = RichTextFX-style popup + a pure word table
(Amended during apply: richtextfx 0.11.7 does not bundle its
`org.fxmisc.richtext.autocompletion` package, so the popup is a small
built-in `javafx.stage.Popup` + `ListView` inside `GCodeEditorView` —
Enter accepts, Escape dismisses, arrows navigate, typing through hides.) A
pure class `view/syntax/GCodeWords` holds the documentation table: code →
one-line description (the supported set the parser/classifier already
define), plus `suggestionsFor(prefix)` returning matches. Accepting inserts
the code part only. Pure table logic is unit-tested headlessly; TestFX
tests cover popup-shows-with-docs, insert-on-accept, and
typing-through-dismisses. Alternative considered: adding the
autocompletion artifact — rejected, no such artifact ships for 0.11.x.

### D2: Image export = node snapshot + ImageIO
`preview.node().snapshot(null, new WritableImage(w, h))` renders the preview
pane (SubScene included) offscreen; `SwingFXUtils.fromFXImage` +
`javax.imageio.ImageIO.write` produce PNG or JPG by extension. A new
File > "Save Preview Image..." action wires a save dialog with
PNG/JPG extension filters, starting in `last_open_dir` and persisting the
choice on success (reusing `App`'s helpers). Tests: headless render into a
temp file for both extensions, assert the file exists and `ImageIO.read`
returns non-zero dimensions; dialog wiring follows the existing chooser
pattern (injected callback, no real dialog in tests).

### D3: 3D visibility fix = orbit camera that looks at the bed
Root cause: the camera is a `PerspectiveCamera(true)` positioned with
translates only and never oriented — it looks straight along −Z, while the
bed sits ~29° off-axis against a ~30° vertical FOV (and the "view rotation"
is applied to the content group, moving content further off-axis). Fix with
the canonical JavaFX orbit pattern: the camera lives inside a `Group`;
yaw/pitch rotations go on that group and the camera sits at
`translateZ = -distance`, so it always faces the bed center; drag updates
the group rotations (the world group stays unrotated), the wheel changes
distance. Regression test: project the bed center and corners into screen
coordinates via the subscene/camera transforms and assert they land inside
the viewport bounds — the missing visibility assertion that let this ship.

## Risks / Trade-offs

- [AutoCompletePopup styling/focus quirks] → library-standard component;
  wrapped in `GCodeEditorView` so a worst-case swap is contained.
- [JPG loses transparency] → preview background is opaque (bed slab), no
  alpha concern.
- [Snapshot of an idle window may miss latest repaint] → snapshot happens
  synchronously on the FX thread after the last `setToolpath`; acceptable.

## Migration Plan

All additive; the camera rework is internal to `Preview3DView`. Rollback is
reverting the three touched areas independently.
