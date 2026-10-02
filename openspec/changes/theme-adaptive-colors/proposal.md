# Proposal: theme-adaptive-colors

## Why

The chrome now follows the selected theme (AtlantaFX palette), but two surfaces still speak the old light-only language: the editor's syntax colors use a fixed mapping that looks muted/wrong on dark themes, and the 3D preview's viewport, bed, and rapid colors are Java constants tuned for light mode — a glaring light canvas inside a dark UI. Professional tools give each theme family its own curated syntax palette and canvas colors; the user has asked for exactly that.

## What Changes

- **Theme-family detection**: when a theme is applied, the app records whether it is dark (`Theme.isDarkMode()`) and marks the scene roots, editor, and preview wrappers with a `dark` style class (removed for light themes). This flag is the single switch every adaptive surface reads.
- **Curated syntax palettes**: the editor's syntax colors become two curated palettes — the existing light palette for light themes and a new dark palette (soft, desaturated tones typical of dark syntax themes, readable on dark backgrounds) — exposed as looked-up variables in `app.css` with `.dark` overrides, consumed by `editor.css`. Switching themes recolors the editor immediately.
- **Theme-adaptive 3D preview**: the preview's viewport, bed, rapid, and out-of-bed colors switch between light and dark palettes when the theme family changes (the depth-ramp hues stay a data encoding in both). The restyle is immediate — no reload needed.
- Non-goals: user-customizable palettes; new themes; per-theme settings beyond the family switch; changing the settings window or status-bar tokens (already adaptive).

## Capabilities

### New Capabilities

- `ui-styling` delta additions: theme-adaptive syntax coloration and theme-adaptive 3D preview colors (ADDED requirements; the capability exists as deltas from `ui-professional-styling`, which is implemented but not archived).

## Impact

- **Code**: `app.css` (syntax variables + `.dark` overrides), `editor.css` (consume the variables), `GCodeEditorView`/`MainWindowController`/`App` (dark-flag application on theme apply), `Preview3DView` (palette fields replacing light-only constants, restyle on family switch), tests (palette selection, preview recolor), README.
- **Dependencies**: none new.
- **Ecosystem**: none.
