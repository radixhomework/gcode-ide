# Design: theme-adaptive-colors

## Context

The chrome is theme-adaptive (AtlantaFX palette + `-gd-*` tokens resolving to
Primer variables). Remaining light-only surfaces: the editor syntax palette
(currently mapped to generic Primer fg colors — readable but not curated) and
the 3D preview (Java color constants for light mode). The maintainer wants
both to follow the theme with curated palettes.

## Decisions

### D1: The `dark` style class is the single switch
`Theme.isDarkMode()` decides the family. Applying a theme (restore and
settings commit both) adds/removes a `dark` style class on every lookup root:
the app scene root, the editor `CodeArea`, the preview wrapper, and the
settings-window pane. Central helper `UiTheme.applyDark(Node, boolean)` keeps
it consistent. CSS then branches: syntax variables default to the light
palette at `.root/.tokens/.context-menu` and are overridden under `.dark`.

### D2: Two curated syntax palettes as variables
`app.css` defines `-gd-syn-comment/-g-word/-m-word/-axis/-line-number/-fallback`
(light: the current green/blue/red/brown scheme) with `.dark` overrides
(GitHub-dark-style soft tones: muted gray comments, light blue G-words, salmon
M-words, pale gold axis). `editor.css` consumes the variables; WordClassifier
categories are unchanged. Curated literals beat derived fg-colors — syntax
palettes are designed, not computed.

### D3: Preview palette object, applied live
`Preview3DView` gains a small `PreviewPalette` (viewport, bed, rapid,
out-of-bed, highlight — record) with LIGHT and DARK constants, an instance
field, and `setDark(boolean)` that swaps the palette and calls
`restyle()`/`rebuild()` (bed box color needs rebuild). The static style
helpers (`segmentColor`, `depthColor`) take the palette as a parameter;
the Java constants become the LIGHT palette fields (tests updated). The
controller calls `preview.setDark(isDark)` next to `setCurrentLine` wiring on
every theme apply path (restore + settings commit).

### D4: Dark palette values (curated, Primer-dark adjacent)
viewport `#1c2128`, bed `#2d333b`, rapid `#9ea7b3` @55%, out-of-bed `#f85149`,
highlight `#ffa657`; syntax dark palette: comments `#8b949e`, G-word `#79c0ff`,
M-word `#ff7b72`, axis `#d2a8ff`, line-number `#6e7681`, fallback `#8b949e`
italic. Light palette unchanged.

## Risks / Trade-offs

- [A missed lookup root keeps a light surface in dark mode] → the dark-flag
  helper is applied at every root the tokens use (.root/.tokens/.context-menu
  scenes); tests assert the class lands on editor and preview wrappers.
- [Static color helpers gaining a parameter churns tests] → mechanical update;
  keeps the helpers pure and headless-testable.

## Migration Plan

Presentation-only; rollback = revert commit.

## Open Questions

- none blocking
