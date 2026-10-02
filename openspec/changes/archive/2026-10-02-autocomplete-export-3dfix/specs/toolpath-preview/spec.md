## Purpose

Defines the 3D perspective toolpath preview: how the parsed toolpath is visualized against the machine bed, how it stays in sync with the editor, and what statistics and safety diagnostics it produces before code ever reaches the machine.

## ADDED Requirements

### Requirement: Save preview as image
The application SHALL provide an action that saves the currently rendered preview as an image file in PNG or JPG format, chosen through a file dialog that starts in the last used directory. The saved image SHALL contain the preview as currently visible (rotation, toolpath, bed).

#### Scenario: Saving a PNG
- **WHEN** the user activates save-preview-image and confirms a `.png` path
- **THEN** a valid PNG file is written containing the current preview rendering

#### Scenario: JPG is supported
- **WHEN** the user confirms a `.jpg` path
- **THEN** a valid JPG file is written containing the current preview rendering

#### Scenario: Dialog starts in the last directory
- **WHEN** the save-image dialog opens after a previous file operation
- **THEN** it starts in the last used directory, like the open/save dialogs
