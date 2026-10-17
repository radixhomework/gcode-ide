## Purpose

Defines the drawing import flow: the Import Image entry, the wizard that collects import parameters, the SVG-to-G-code generation, and the round-trip validity of the generated program inside the IDE.

## ADDED Requirements

### Requirement: Cut side selection with tool offset
The import wizard SHALL offer a cut-side choice - On line, Inside, Outside - and a cutter diameter. Inside and Outside SHALL offset closed runs by half the cutter diameter (round joins at corners) so the tool's edge follows the true boundary: Inside keeps the tool within the shape, Outside keeps it outside. On line SHALL keep the previous behavior (path itself). If an inward offset would self-intersect (geometry too tight for the cutter), the affected run SHALL fall back to its on-line path with a warning.

#### Scenario: Inside cut of a square
- **WHEN** the user imports a 40x40 square with cutter diameter 3.175 and cut side Inside
- **THEN** the generated path is a 36.825x36.825 square centered on the original

#### Scenario: Outside cut of a square
- **WHEN** the user imports the same square with cut side Outside
- **THEN** the generated path is a 43.175x43.175 square centered on the original

#### Scenario: Tight geometry falls back
- **WHEN** the inward offset of a run would self-intersect
- **THEN** that run falls back to its on-line path and a warning names it

#### Scenario: Open runs stay on line
- **WHEN** the drawing contains an open path and the cut side is Inside or Outside
- **THEN** the open run is generated on-line (with a warning that side offsets apply to closed shapes only in this version) and closed runs are offset as requested
