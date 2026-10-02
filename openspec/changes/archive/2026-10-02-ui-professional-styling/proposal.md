# Proposal: ui-professional-styling

## Why

The interface reads as "rough" — the maintainer's word for a non-professional
feel. The audit found the cause is not any single ugly component but an
*unauthored* UI: the editor renders code in the platform's proportional font
(no font is set — columns don't align), the completion popup is a bare
default `ListView`, the documentation popup and stale badge are hand-styled
one-offs (sticky-note yellow, red block), the status bar is default labels at
4 px padding, and color is semantically anarchic (red means M-codes, parse
errors, the stale badge, and out-of-bed segments; blue means G-codes and the
shallow end of the depth ramp). Nothing ties the surfaces together; the app
looks like defaults plus fixes.

## What Changes

- **Design tokens as the single source of visual semantics**: one `app.css`
  defining looked-up colors at `.root` — `surface`, `surface-alt`, `border`,
  `text`, `text-muted`, plus reserved semantic colors: `accent` (selection,
  focus, links), `highlight` (current line / current toolpath segments ONLY),
  `error` (parse errors ONLY), `warn` (warnings and stale state — amber, not
  red), and the depth-ramp endpoints. All existing inline styles and one-off
  colors migrate to tokens.
- **Editor typography**: the code area renders in a tuned monospace font
  (with matching line-number gutter); the current-line highlight softens to a
  token-driven tone.
- **Consistent popover language**: the completion popup and documentation
  popup (and stale badge) share one geometry — same surface, border, padding,
  corner treatment — with completion cells restructured as bold code +
  muted description instead of raw strings in a default list.
- **Status bar presentation**: `surface-alt` background, generous padding,
  muted secondary text, and warnings presented as a quiet amber chip rather
  than bold red text; caret/stats hierarchy via a small type scale.
- **3D color bridge**: `Preview3DView`'s Java color constants adopt the same
  token values (bed, rapid, highlight, out-of-bed/error) so the 3D view and
  the 2D chrome speak one palette.
- **Theme target: refined light** (per exploration decision): Modena stays as
  the component base; the work is the token layer and component restyles.
  Dark "workshop" theme is explicitly deferred — with tokens in place it
  becomes a later palette swap plus a small Java bridge (JavaFX has no
  `prefers-color-squence` support and 3D colors are code constants).
- Non-goals: dark theme, an icon set (text-only status bar is acceptable),
  main-window layout restructuring, any behavior change — this is
  presentation only; no parser, preview logic, or interaction changes.

## Capabilities

### New Capabilities

- `ui-styling`: the visual design system — token roles and their reservation
  rules, editor typography, shared popover language, status-bar presentation,
  and the 3D palette bridge. (Styling requirements are observable behavior:
  what font renders code, what colors may mean, what the popups look like.)

### Modified Capabilities

(none — existing capability requirements concern behavior, not appearance;
their scenarios remain valid unchanged)

## Impact

- **Code**: new `app.css` (scene-level stylesheet); `editor.css` absorbed
  into the token system; `GCodeEditorView` (font, popup cell factory, doc
  popup restyle), `Preview3DView` (color constants → token values),
  `MainWindow.fxml`/controller (status bar structure, inline styles removed).
- **Dependencies**: none new — JavaFX CSS only.
- **Ecosystem**: none.
