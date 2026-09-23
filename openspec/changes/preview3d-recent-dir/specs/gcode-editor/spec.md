## Purpose

Defines the G-code document lifecycle (create, open, save) and the editing experience — dialect-aware syntax coloring, line numbering, and unsaved-change tracking — that makes the text file a first-class, safely editable artifact.

## ADDED Requirements

### Requirement: File dialogs remember the last used directory
The open and save-as file dialogs SHALL start in the directory of the last successfully used file (opened or saved), and this directory SHALL persist across application restarts.

#### Scenario: Second open starts in the last directory
- **WHEN** the user opens a file from directory D, closes the application, and later activates the open action
- **THEN** the file dialog starts in directory D

#### Scenario: Save-as also updates the remembered directory
- **WHEN** the user saves a document into directory E via save-as
- **THEN** the next open or save-as dialog starts in directory E
