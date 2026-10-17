## Purpose

Defines the application's visual design system: the token roles that give every color and surface a single reserved meaning, the editor's typography, the shared popover language, the status-bar presentation, and theme-family-adaptive coloration for the editor's syntax palette and the 3D preview canvas.

## Requirements

### Requirement: Design tokens as the single source of visual semantics
The application SHALL define its visual vocabulary as named design tokens (surface, border, text, muted text, and reserved semantic colors) in one application stylesheet, and all styled surfaces SHALL reference these tokens rather than ad-hoc inline styles or one-off color literals. Semantic colors SHALL be reserved: the highlight color SHALL mark the current editor line and the matching toolpath segments only; the error color SHALL mark parse-error indication only; warning/stale indications SHALL use the warning (amber) token, not the error token.

#### Scenario: Tokens replace inline styles
- **WHEN** the codebase is inspected for UI color and style definitions
- **THEN** styled surfaces reference the shared token definitions (or the documented 3D bridge constants carrying the same values) rather than scattered inline style strings

### Requirement: Editor typography is monospace
The code editor SHALL render G-code (text, line-number gutter) in a monospace font, so columns align and digits hold their width.

#### Scenario: Code aligns
- **WHEN** the editor displays several lines of G-code with coordinates of varying digit counts
- **THEN** corresponding characters align vertically across lines

### Requirement: Shared popover language
The completion popup, the documentation popup, and the stale indication SHALL share one visual language: the same surface color, border, padding, and corner treatment derived from the design tokens. Completion suggestions SHALL render as a structured row - the code emphasized, its description in muted text - rather than a raw display string in a default list.

#### Scenario: Popups look like one system
- **WHEN** the completion popup, the documentation popup, and the stale badge are shown
- **THEN** all three share the same surface, border, and padding treatment

#### Scenario: Completion rows are structured
- **WHEN** the completion popup lists suggestions
- **THEN** each row shows the code emphasized and its description in muted text

### Requirement: Status bar presentation
The status bar SHALL present a quiet, hierarchical strip: a distinct surface with generous padding, caret position and statistics in muted secondary text, and warnings shown as a compact amber chip (visible only when warnings exist) rather than bold red text.

#### Scenario: Quiet status bar
- **WHEN** a program with statistics and one or more warnings is loaded
- **THEN** the status bar shows the statistics in muted text and the warnings as a compact amber chip, with no bold red text

#### Scenario: Chip only when needed
- **WHEN** the current toolpath has no warnings
- **THEN** no warning chip is shown in the status bar

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
