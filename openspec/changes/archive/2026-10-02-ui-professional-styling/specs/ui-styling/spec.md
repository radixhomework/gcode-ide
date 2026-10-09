## Purpose

Defines the application's visual design system: the token roles that give every color and surface a single reserved meaning, the editor's typography, the shared popover language, and the status-bar presentation that together give the interface a coherent, professional appearance.

## ADDED Requirements

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
The completion popup, the documentation popup, and the stale indication SHALL share one visual language: the same surface color, border, padding, and corner treatment derived from the design tokens. Completion suggestions SHALL render as a structured row — the code emphasized, its description in muted text — rather than a raw display string in a default list.

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
