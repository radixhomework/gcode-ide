## Purpose

Defines the 2D top-down toolpath preview: how the parsed toolpath is visualized against the machine bed, how it stays in sync with the editor, and what statistics and safety diagnostics it produces before code ever reaches the machine.

## ADDED Requirements

### Requirement: Distinct rendering of move types
The preview SHALL render rapid moves and cut moves with distinct, visually separable styles (e.g., dashed vs. solid), and SHALL shade cut segments by depth (Z) so deeper passes are distinguishable from shallow ones.

#### Scenario: Mixed program rendering
- **WHEN** previewing a program that positions with `G0` and cuts with `G1` at two different depths
- **THEN** rapids, shallow cuts, and deep cuts are each visually distinguishable at a glance

### Requirement: Machine bed context
The preview SHALL draw the active machine profile's bed as a reference rectangle and render the toolpath within bed coordinates (Y-up), so the drawing's placement on the bed is visible.

#### Scenario: Bed follows the profile
- **WHEN** the active machine profile changes from one bed size to another
- **THEN** the preview's bed rectangle resizes accordingly and the toolpath is re-placed in the new bed's coordinates

### Requirement: Live update while editing
The preview SHALL refresh automatically (deferred briefly while typing) whenever the document text changes, without requiring an explicit refresh action; if the new text cannot be parsed, the last good toolpath SHALL remain visible with a visible indication that it is stale.

#### Scenario: Added move appears
- **WHEN** the user adds a `G1` move to the document and pauses typing
- **THEN** the new segment appears in the preview without any explicit refresh action

#### Scenario: Stale preview indication
- **WHEN** an edit leaves the document unparseable
- **THEN** the preview keeps showing the last good toolpath and indicates it is stale

### Requirement: Bidirectional editor–preview sync
The preview SHALL highlight the move (if any) produced by the editor's current line, and selecting a move in the preview SHALL move the editor caret to (and scroll to) that move's source line.

#### Scenario: Editor line to preview segment
- **WHEN** the caret sits on a line that produced a move
- **THEN** that move is highlighted in the preview

#### Scenario: Preview segment to editor line
- **WHEN** the user clicks a cut segment in the preview
- **THEN** the editor selects and scrolls to the line that produced it

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
