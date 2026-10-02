## Purpose

Defines the G-code document lifecycle (create, open, save) and the editing experience — dialect-aware syntax coloring, line numbering, and unsaved-change tracking — that makes the text file a first-class, safely editable artifact.

## ADDED Requirements

### Requirement: Autocompletion with command documentation
While typing a word, the editor SHALL offer completion suggestions for the supported GRBL codes and axis/parameter words (`G0`–`G3`, `G17`, `G20`/`G21`, `G90`/`G91`, `M3`/`M4`/`M5`, `X Y Z I J R F S`), each shown with a short one-line description. Accepting a suggestion SHALL insert the word into the document. Suggestions SHALL appear without user configuration and SHALL not interfere with normal typing (dismissable by continuing to type or moving the caret).

#### Scenario: Completing a G word
- **WHEN** the user types `G` at a word start
- **THEN** a popup lists the supported G codes with their descriptions, and accepting one inserts it at the caret

#### Scenario: Documentation is shown per suggestion
- **WHEN** the completion popup is visible
- **THEN** each suggestion displays a short description of what the code does (e.g., `G1` — linear feed move)

#### Scenario: Dismissal does not disturb typing
- **WHEN** the user ignores the popup and keeps typing a non-matching sequence
- **THEN** the popup hides and the typed text is unaffected
