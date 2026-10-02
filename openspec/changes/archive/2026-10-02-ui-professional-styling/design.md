# Design: ui-professional-styling

## Context

The UI is default Modena plus scattered one-off styles (inventory in the
proposal). The exploration decided the target: **refined light** now, tokens
first so a dark "workshop" palette is a later swap. JavaFX has no
`prefers-color-scheme`, and the 3D colors are Java constants — both recorded
limitations.

## Decisions

### D1: Tokens as looked-up colors on `.root` and a `.tokens` class
One `app.css` at the scene level defines the vocabulary as looked-up colors:

```
.root, .tokens {
  -gd-surface: #ffffff;   -gd-surface-alt: #f5f6f8;
  -gd-border: #d0d4da;    -gd-text: #1e2329;   -gd-text-muted: #6b7280;
  -gd-accent: #2563eb;    -gd-highlight: #ff8c00;
  -gd-error: #d62728;     -gd-warn: #92400e;   -gd-warn-bg: #fef3c7;
  -gd-caret-line: #eef2ff;
}
```

The `gd-` prefix namespaces them. `.tokens` exists because Popup windows are
separate scenes: looked-up colors do not cross windows, so popup wrapper
panes carry the `tokens` class and the stylesheet, giving them their own
lookup root. `accent` is available for focus/selection defaults (Modena
mostly supplies these; the token documents the choice).

### D2: Stylesheet attachment — every view self-sufficient
`app.css` is attached at three points so tokens exist wherever a view lands
(app scene, standalone test scenes, popups): the editor `CodeArea`'s
stylesheet list (alongside `editor.css`), the preview wrapper `StackPane`
(plus `tokens` class for the badge), and the main scene in `App`.
`editor.css` keeps the syntax palette (code-specific, literal) but its
current-line rule switches to the `caret-line` token; syntax colors
themselves stay literals by design (a code palette, not UI chrome).

### D3: Editor typography in code, not CSS
`GCodeEditorView` picks the first installed family among
Consolas / Menlo / DejaVu Sans Mono / Monaco / Courier New (falling back to
Java's logical `Monospaced`) at 13 px and sets it on the `CodeArea`; the
RichTextFX gutter inherits the area font. Doing it in code avoids JavaFX's
limited CSS font-family fallback lists and is unit-testable.

### D4: Popovers — one geometry, structured cells
The completion popup's cell factory renders an HBox: code in bold `text`,
description in `text-muted`, with token padding; the list loses its default
outline (`-fx-background-insets: 0` on cells). Doc popup label and stale
badge become style classes (`doc-popup`, `stale-badge`) defined once in
`app.css`; the stale badge shifts from red block to the amber warn pair on
the amber background, matching the warnings chip.

### D5: Status bar restructure in FXML + classes
The HBox becomes `.status-bar` (surface-alt, top border, 6/12 padding,
12 px spacing); `posLabel`/`statsLabel` get `.status-text` (muted, smaller);
`warningsLabel` gets `.warning-chip` (warn text on warn-bg, padding, corner
radius) and is visible only when its text is non-empty (bound in the
controller). Inline styles leave the FXML. The Machine label/combo keep
default styling (their removal is `remove-machine-profiles`' business).

### D6: 3D bridge — constants carry token values
`Preview3DView` keeps its public constants (tests reference them) but their
values adopt the tokens: bed = surface-alt, rapid gray = text-muted (with
alpha), out-of-bed = error, highlight = highlight. Depth-ramp hues are
unchanged (they are data encoding, not chrome).

## Risks / Trade-offs

- [Looked-up colors don't cross popup windows] → the `.tokens` class +
  per-popup stylesheet attachment (D1/D2); documented so future popups copy
  the pattern.
- [Font fallback differs per OS] → ordered candidate list with a logical
  `Monospaced` fallback; behavior test asserts the family is in the
  candidate set or the fallback.
- [Modena-based restyle may still look "Java"] → accepted for this round;
  the token layer is the groundwork for a fuller custom look later.

## Migration Plan

Presentation-only; no behavior or data. Rollback = revert the commit.

## Open Questions

- none blocking
