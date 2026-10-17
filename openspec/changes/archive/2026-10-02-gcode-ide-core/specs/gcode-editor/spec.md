## Purpose

Defines the G-code document lifecycle (create, open, save) and the editing experience — dialect-aware syntax coloring, line numbering, and unsaved-change tracking — that makes the text file a first-class, safely editable artifact.

## ADDED Requirements

### Requirement: Create a new G-code document
The application SHALL provide a "new document" action that replaces the current editor content with an empty, untitled G-code document.

#### Scenario: New document from menu
- **WHEN** the user activates the new-document action with an unmodified document open
- **THEN** the editor is cleared, the document is untitled, and no save prompt appears

### Requirement: Open an existing G-code file
The application SHALL open plain-text G-code files (commonly `.nc`, `.gcode`, `.ngc`, `.tap`) and display their contents in the editor.

#### Scenario: Opening a file
- **WHEN** the user opens an existing G-code file
- **THEN** its contents are displayed in the editor and the window title identifies the file by name

### Requirement: Save and save as
The application SHALL save the current document to its file, and SHALL provide save-as to choose a new path or name.

#### Scenario: Save clears the modified state
- **WHEN** the user saves a modified document
- **THEN** the file on disk contains the editor contents and the document is no longer marked modified

#### Scenario: Save as switches the document
- **WHEN** the user saves a document under a new path
- **THEN** subsequent saves write to the new path and the window title reflects the new name

### Requirement: Unsaved-change tracking
The application SHALL track whether the current document has unsaved modifications, expose that state visually, and prompt (save / discard / cancel) before that content would be lost through new-document, open, or application close.

#### Scenario: Prompt on close with unsaved changes
- **WHEN** the user closes the application while the document is modified
- **THEN** a prompt offers to save, discard, or cancel, and cancelling leaves the application open with content intact

### Requirement: GRBL G-code syntax coloring
The editor SHALL colorize G-code text by token category: comments (`;` and parenthesized), G and M words with their numeric values, axis and parameter words (`X Y Z I J K R F S T`), and `N` line numbers. Recognized-but-unused and unknown words SHALL be rendered with a distinct fallback style rather than breaking highlighting.

#### Scenario: Coloring a representative line
- **WHEN** the editor displays a line such as `N10 G1 X10 Y20 F600 ; first pass`
- **THEN** the `N`, `G`, axis, and `F` words and the trailing comment each receive their category's color

### Requirement: Line numbers and cursor position
The editor SHALL display a line-number gutter and SHALL report the caret position (line and column) in the status area.

#### Scenario: Cursor position tracking
- **WHEN** the caret moves within the document
- **THEN** the status area shows the caret's current line and column
