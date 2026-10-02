## Purpose

Defines the 3D perspective toolpath preview: how the parsed toolpath is visualized against the machine bed, how it stays in sync with the editor, and what statistics and safety diagnostics it produces before code ever reaches the machine.

## Requirements

### Requirement: 3D depth view is the preview
The preview SHALL be a 3D perspective view of the toolpath against the machine bed in which pass depth (Z) is directly visible: bed X/Y as the ground plane, deeper passes lower than shallow ones. Cut segments SHALL be shaded by a depth ramp so deeper passes are distinguishable from shallow ones, rapid moves SHALL be rendered with a distinct translucent style, and out-of-bed segments SHALL be visually marked. The user SHALL be able to rotate the view by dragging and zoom with the wheel.

#### Scenario: Depths are visible
- **WHEN** previewing a program that cuts at two different depths
- **THEN** the deeper pass renders lower than the shallow pass and the two are distinguishable by the depth color ramp

#### Scenario: Rotating the view
- **WHEN** the user drags within the preview
- **THEN** the toolpath and bed rotate around the bed center

### Requirement: Line-style segment rendering
Toolpath segments SHALL render as thin lines centered on the toolpath (the line runs through the geometric path of the tool), not as thick rounded tubes. Move kinds SHALL remain visually distinct (depth-ramped cuts, translucent rapids, out-of-bed marking) and segment picking SHALL keep working.

#### Scenario: Segments read as lines
- **WHEN** previewing a program with cuts at two depths
- **THEN** the toolpath appears as thin lines along the tool path, with the depth ramp still distinguishing passes

### Requirement: Machine bed context in 3D
The preview SHALL render the active machine profile's bed as the ground reference the toolpath is drawn against, and SHALL resize it when the active profile changes.

#### Scenario: Bed follows the profile
- **WHEN** the active machine profile changes from one bed size to another
- **THEN** the rendered bed reflects the new envelope

### Requirement: Live update while editing
The preview SHALL refresh automatically (deferred briefly while typing) whenever the document text changes, without requiring an explicit refresh action; if the new text cannot be parsed, the last good toolpath SHALL remain visible with a visible indication that it is stale.

#### Scenario: Added move appears
- **WHEN** the user adds a `G1` move to the document and pauses typing
- **THEN** the new segment appears in the preview without any explicit refresh action

#### Scenario: Stale preview indication
- **WHEN** an edit leaves the document unparseable
- **THEN** the preview keeps showing the last good toolpath and indicates it is stale

### Requirement: Bidirectional editor-preview sync in 3D
The preview SHALL highlight the move (if any) produced by the editor's current line, and clicking a segment in the 3D view SHALL move the editor caret to (and scroll to) that move's source line.

#### Scenario: Editor line to preview segment
- **WHEN** the caret sits on a line that produced a move
- **THEN** that move is highlighted in the 3D preview

#### Scenario: Preview segment to editor line
- **WHEN** the user clicks a cut segment in the 3D preview
- **THEN** the editor selects and scrolls to the line that produced it

### Requirement: 3D view navigation
The 3D preview SHALL support the following camera controls: the mouse wheel zooms in and out around the current view; dragging with the left button orbits the view around the bed center; dragging with the right button pans the view straight, parallel to the screen, without changing the viewing angle. Pan speed SHALL scale with the current zoom so the motion stays controllable when zoomed in. The right button SHALL NOT open a context menu in the preview.

#### Scenario: Wheel zooms
- **WHEN** the user scrolls the wheel over the preview
- **THEN** the camera moves closer or farther without changing the viewing angle

#### Scenario: Left-drag orbits
- **WHEN** the user drags with the left button
- **THEN** the toolpath and bed rotate around the bed center

#### Scenario: Right-drag pans straight
- **WHEN** the user drags with the right button
- **THEN** the scene translates parallel to the screen with no rotation, and no context menu appears

#### Scenario: Controls combine
- **WHEN** the user pans, then orbits, then zooms
- **THEN** each motion applies on top of the previous view state (pan persists across orbit and zoom)

### Requirement: Toolpath statistics
The application SHALL display, for the current toolpath: the cutting bounding box in mm, total cut distance, total rapid distance, and an estimated run time computed from commanded feeds with rapid moves at the profile's rapid rate.

#### Scenario: Known program statistics
- **WHEN** previewing a program whose moves and feeds are known
- **THEN** the displayed bounding box and estimated time match values computed from those moves and feeds

### Requirement: Out-of-bed warning
Moves that fall outside the active machine profile's bed envelope SHALL be flagged as warnings listing the offending source lines, and SHALL be visually marked in the preview.

#### Scenario: Move beyond the bed
- **WHEN** a document commands a move to X 350 on a 300 mm bed
- **THEN** a warning identifies the offending line(s), and the out-of-bed portion is visually marked in the preview

### Requirement: Excessive feed warning
Cut moves commanded at a feed rate exceeding the active profile's maximum cutting feed SHALL be flagged as warnings citing the source lines and the applicable limit.

#### Scenario: Feed above the profile cap
- **WHEN** a cut move is commanded at F 2000 while the profile caps cutting feed at 800 mm/min
- **THEN** a warning cites the move's line and the 800 mm/min limit

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
