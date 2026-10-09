## Purpose

Defines the G-code document lifecycle (create, open, save) and the editing experience — dialect-aware syntax coloring, line numbering, and unsaved-change tracking — that makes the text file a first-class, safely editable artifact.

## ADDED Requirements

### Requirement: Hover and shortcut documentation
The editor SHALL show the documented description of a supported G/M code or parameter word when the mouse hovers over that word for about two seconds, and when Ctrl+Q is pressed while the caret is on or next to a word. The documentation popup SHALL be non-focus-stealing and SHALL hide on mouse movement away, key press, or focus loss.

#### Scenario: Hovering a word shows its description
- **WHEN** the mouse rests over `G2` for roughly two seconds
- **THEN** a popup shows the documented description of `G2`

#### Scenario: Ctrl+Q documents the word at the caret
- **WHEN** the caret is on or immediately next to `G3` and the user presses Ctrl+Q
- **THEN** the popup shows the documented description of `G3`

#### Scenario: Unknown words show nothing
- **WHEN** hovering or pressing Ctrl+Q on a word that is not in the documented table
- **THEN** no popup appears
