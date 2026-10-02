## Purpose

Defines the application's visual design system: token roles, editor typography, popover language, and status-bar presentation — extended here with theme-family-adaptive coloration for the editor's syntax palette and the 3D preview canvas.

## ADDED Requirements

### Requirement: Theme-adaptive syntax coloration
The editor's syntax colors SHALL follow the selected theme's family: light themes use the light syntax palette and dark themes use the dark syntax palette (curated for readability on dark backgrounds). Switching themes SHALL recolor the editor immediately, without restart.

#### Scenario: Dark theme gets the dark syntax palette
- **WHEN** the user selects a dark theme (e.g., Primer Dark)
- **THEN** the editor's comments, G/M words, axis words, and fallback text render in the dark palette colors

#### Scenario: Switching back is immediate
- **WHEN** the user switches from a dark theme to a light theme
- **THEN** the editor immediately renders the light syntax palette

### Requirement: Theme-adaptive 3D preview colors
The 3D preview's viewport background, bed surface, and rapid-move color SHALL follow the selected theme's family (distinct dark variants under dark themes). The depth ramp SHALL remain a data encoding in both families, and out-of-bed segments SHALL stay visually marked. Switching themes SHALL restyle the preview immediately.

#### Scenario: Dark preview under a dark theme
- **WHEN** a dark theme is active
- **THEN** the preview viewport and bed render in dark variants, distinct from each other, with rapids still distinguishable from cuts

#### Scenario: Live restyle on theme switch
- **WHEN** the user applies a theme from the settings window while a toolpath is shown
- **THEN** the preview recolors immediately without reopening or reloading the document
