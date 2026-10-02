## Purpose

Defines the G-code document lifecycle (create, open, save) and the editing experience — dialect-aware syntax coloring, line numbering, unsaved-change tracking, autocompletion with command documentation, and theme-adaptive presentation — that makes the text file a first-class, safely editable artifact.

## Requirements

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

### Requirement: File dialogs remember the last used directory
The open and save-as file dialogs SHALL start in the directory of the last successfully used file (opened or saved), and this directory SHALL persist across application restarts.

#### Scenario: Second open starts in the last directory
- **WHEN** the user opens a file from directory D, closes the application, and later activates the open action
- **THEN** the file dialog starts in directory D

#### Scenario: Save-as also updates the remembered directory
- **WHEN** the user saves a document into directory E via save-as
- **THEN** the next open or save-as dialog starts in directory E

### Requirement: Autocompletion with command documentation
While typing a word, the editor SHALL offer completion suggestions for the supported GRBL codes and axis/parameter words (`G0`-`G3`, `G17`, `G20`/`G21`, `G90`/`G91`, `M3`/`M4`/`M5`, `X Y Z I J R F S`), each shown with a short one-line description. Accepting a suggestion SHALL insert the word into the document. Suggestions SHALL appear without user configuration and SHALL not interfere with normal typing (dismissable by continuing to type or moving the caret).

#### Scenario: Completing a G word
- **WHEN** the user types `G` at a word start
- **THEN** a popup lists the supported G codes with their descriptions, and accepting one inserts it at the caret

#### Scenario: Documentation is shown per suggestion
- **WHEN** the completion popup is visible
- **THEN** each suggestion displays a short description of what the code does (e.g., `G1` - linear feed move)

#### Scenario: Dismissal does not disturb typing
- **WHEN** the user ignores the popup and keeps typing a non-matching sequence
- **THEN** the popup hides and the typed text is unaffected

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
