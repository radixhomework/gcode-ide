## Purpose

Defines the 3D perspective toolpath preview: how the parsed toolpath is visualized against the machine bed, how it stays in sync with the editor, and what statistics and safety diagnostics it produces before code ever reaches the machine.

## ADDED Requirements

### Requirement: 3D depth view is the preview
The preview SHALL be a 3D perspective view of the toolpath against the machine bed in which pass depth (Z) is directly visible: bed X/Y as the ground plane, deeper passes lower than shallow ones. Cut segments SHALL be shaded by a depth ramp so deeper passes are distinguishable from shallow ones, rapid moves SHALL be rendered with a distinct translucent style, and out-of-bed segments SHALL be visually marked. The user SHALL be able to rotate the view by dragging and zoom with the wheel.

#### Scenario: Depths are visible
- **WHEN** previewing a program that cuts at two different depths
- **THEN** the deeper pass renders lower than the shallow pass and the two are distinguishable by the depth color ramp

#### Scenario: Rotating the view
- **WHEN** the user drags within the preview
- **THEN** the toolpath and bed rotate around the bed center

### Requirement: Bidirectional editor–preview sync in 3D
The preview SHALL highlight the move (if any) produced by the editor's current line, and clicking a segment in the 3D view SHALL move the editor caret to (and scroll to) that move's source line.

#### Scenario: Editor line to preview segment
- **WHEN** the caret sits on a line that produced a move
- **THEN** that move is highlighted in the 3D preview

#### Scenario: Preview segment to editor line
- **WHEN** the user clicks a cut segment in the 3D preview
- **THEN** the editor selects and scrolls to the line that produced it

### Requirement: Machine bed context in 3D
The preview SHALL render the active machine profile's bed as the ground reference the toolpath is drawn against, and SHALL resize it when the active profile changes.

#### Scenario: Bed follows the profile
- **WHEN** the active machine profile changes from one bed size to another
- **THEN** the rendered bed reflects the new envelope
