## Purpose

Defines the 3D perspective toolpath preview: how the parsed toolpath is visualized against the machine bed, how it stays in sync with the editor, and what statistics and safety diagnostics it produces before code ever reaches the machine.

## ADDED Requirements

### Requirement: Line-style segment rendering
Toolpath segments SHALL render as thin lines centered on the toolpath (the line runs through the geometric path of the tool), not as thick rounded tubes. Move kinds SHALL remain visually distinct (depth-ramped cuts, translucent rapids, red out-of-bed) and segment picking SHALL keep working.

#### Scenario: Segments read as lines
- **WHEN** previewing a program with cuts at two depths
- **THEN** the toolpath appears as thin lines along the tool path, with the depth ramp still distinguishing passes
