## Purpose

Defines the G-code document lifecycle (create, open, save) and the editing experience — dialect-aware syntax coloring, line numbering, unsaved-change tracking, autocompletion with command documentation, and theme-adaptive presentation — that makes the text file a first-class, safely editable artifact.

## ADDED Requirements

### Requirement: Editor scroll bars
The editor SHALL provide a vertical scroll bar on the right and a horizontal scroll bar on the bottom, shown whenever the document height or the widest line exceeds the visible pane, and hidden when the content fits. Scrolling SHALL keep the syntax coloring, current-line highlight, caret reporting, and goto-line behavior working unchanged.

#### Scenario: Long document shows the vertical bar
- **WHEN** the editor displays a document taller than the visible pane
- **THEN** a vertical scroll bar is available on the right and scrolling reaches the end of the document

#### Scenario: Long line shows the horizontal bar
- **WHEN** the editor displays a line wider than the visible pane
- **THEN** a horizontal scroll bar is available on the bottom and scrolling reaches the end of the line

#### Scenario: Goto-line works through scrolling
- **WHEN** the user invokes goto-line for a line outside the visible area
- **THEN** the editor scrolls to it and the line is selected and visible

### Requirement: Go to line
The editor SHALL provide a go-to-line action, available as Edit > "Go to Line..." with the Ctrl+G shortcut, that asks for a line number and navigates the editor to that line: the caret is placed there and the line is selected and scrolled into view. Input that is empty, non-numeric, or beyond the document SHALL be ignored without moving the caret.

#### Scenario: Navigating to a valid line
- **WHEN** the user activates go-to-line and enters 42 in a document with 100 lines
- **THEN** the caret moves to line 42 and the line is selected and scrolled into view

#### Scenario: Invalid input is ignored
- **WHEN** the user activates go-to-line and enters an empty or non-numeric value, or a line beyond the document
- **THEN** the caret does not move

### Requirement: Tab-validated completion
When the completion popup is open, pressing Tab SHALL accept the selected suggestion and insert it, exactly like Enter, and SHALL NOT insert a tab character into the document. When the popup is not open, Tab behavior is unchanged.

#### Scenario: Tab accepts the suggestion
- **WHEN** the completion popup is open with a suggestion selected and the user presses Tab
- **THEN** the selected code is inserted at the caret and no tab character is added

#### Scenario: Tab still indents when no popup
- **WHEN** the completion popup is not open and the user presses Tab
- **THEN** the editor behaves as before (no completion is triggered)

## MODIFIED Requirements

### Requirement: Hover and shortcut documentation
The editor SHALL show the documented description of a supported G/M code or parameter word when the mouse hovers over that word for about two seconds, and when Ctrl+Q is pressed while the caret is on or next to a word. While the documentation popup is open, moving the mouse SHALL NOT dismiss it: the popup SHALL remain visible while the cursor stays on the triggering keyword, and SHALL hide when the cursor leaves that keyword, on any key press, on a mouse click, or when the document text changes. The popup SHALL be non-focus-stealing.

#### Scenario: Hovering a word shows its description
- **WHEN** the mouse rests over `G2` for roughly two seconds
- **THEN** a popup shows the documented description of `G2`

#### Scenario: Mouse movement keeps the popup open
- **WHEN** the documentation popup is open and the mouse moves while staying on the triggering keyword
- **THEN** the popup remains visible

#### Scenario: Leaving the keyword hides the popup
- **WHEN** the documentation popup is open and the cursor moves onto a different word or onto empty space
- **THEN** the popup hides

#### Scenario: Key press hides the popup
- **WHEN** the documentation popup is open and the user presses any key
- **THEN** the popup hides

#### Scenario: Ctrl+Q documents the word at the caret
- **WHEN** the caret is on or immediately next to `G3` and the user presses Ctrl+Q
- **THEN** the popup shows the documented description of `G3`

#### Scenario: Unknown words show nothing
- **WHEN** hovering or pressing Ctrl+Q on a word that is not in the documented table
- **THEN** no popup appears
