## Purpose

Defines the 3D perspective toolpath preview: how the parsed toolpath is visualized against the machine bed, how it stays in sync with the editor, and what statistics and safety diagnostics it produces before code ever reaches the machine.

## ADDED Requirements

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
