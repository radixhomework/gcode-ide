## Purpose

Defines the 3D perspective toolpath preview: how the parsed toolpath is visualized, how it stays in sync with the editor, and what statistics and diagnostics it produces before code ever reaches the machine.

## MODIFIED Requirements

### Requirement: Toolpath statistics
The application SHALL display, for the current toolpath: the cutting bounding box in mm, total cut distance, total rapid distance, and an estimated cutting time computed from commanded feed rates over cut moves only. The figure SHALL be labeled as an estimate. Cut moves without a commanded feed SHALL be flagged as a dialect warning and SHALL NOT contribute to the estimated time.

#### Scenario: Known program statistics
- **WHEN** previewing a program whose moves and feeds are known
- **THEN** the displayed bounding box, distances, and estimated cutting time match values computed from those cut moves and feeds

#### Scenario: Rapid moves are not timed
- **WHEN** a program contains rapid moves
- **THEN** the rapid distance is displayed but rapids add nothing to the estimated time

#### Scenario: Feedless cuts are warned and untimed
- **WHEN** a cut move has no commanded feed
- **THEN** a warning cites its source line and the move adds nothing to the estimated time

## REMOVED Requirements

### Requirement: Machine bed context
- **Reason**: the bed rectangle/box was rendered from the active machine profile; profiles are removed entirely (see the machine-profiles capability removal).
- **Migration**: the preview renders the toolpath in its own coordinate system with the existing fit-to-content framing; no bed is drawn.

### Requirement: Out-of-bed warning
- **Reason**: out-of-bed checks compare against a profile's bed envelope; with profiles removed there is no envelope to check against.
- **Migration**: the warning disappears from the status bar and preview; geometry may extend anywhere — placement awareness returns to the user.

### Requirement: Excessive feed warning
- **Reason**: the feed cap came from the profile's maximum cutting feed; without profiles there is no cap to exceed.
- **Migration**: the warning disappears; commanded feeds are shown as-is in statistics. (A cut without any commanded feed still warns — pure dialect check, no cap involved.)
