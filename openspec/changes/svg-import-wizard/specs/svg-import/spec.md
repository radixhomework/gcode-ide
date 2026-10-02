## Purpose

Defines the drawing import flow: the Import Image entry, the wizard that collects import parameters, the SVG-to-G-code generation (supported geometry and warnings), and the round-trip validity of the generated program inside the IDE.

## ADDED Requirements

### Requirement: Import Image entry
The application SHALL provide a File > Import Image... action that opens an image file dialog (accepting SVG files) and, for a chosen file, launches the import wizard. The regular Open action SHALL remain plain-G-code-only.

#### Scenario: Importing a drawing
- **WHEN** the user activates Import Image... and chooses an SVG file
- **THEN** the import wizard opens showing the drawing's importable content

#### Scenario: Open stays G-code-only
- **WHEN** the user compares the Open and Import Image file dialogs
- **THEN** Open filters G-code files and Import Image filters image files (SVG for now)

### Requirement: Wizard collects import parameters
The wizard SHALL collect, with sensible defaults: the drawing's target size (scale), its placement, the final cutting depth expressed as a depth per pass, the cutting feed, the rapid/safe height, and the spindle speed. It SHALL show the computed size in millimeters before generation, and SHALL list any unsupported or ignored SVG constructs as warnings. Completing the wizard generates the program.

#### Scenario: Defaults are ready to use
- **WHEN** the wizard opens on an importable drawing
- **THEN** the parameters are pre-filled with defaults and the computed millimeter size is visible

#### Scenario: Unsupported constructs are reported
- **WHEN** the drawing contains constructs outside the supported subset
- **THEN** the wizard lists them as warnings naming the elements, and the importable geometry is still offered

#### Scenario: Cancelling imports nothing
- **WHEN** the user cancels the wizard
- **THEN** no document is created and the current document is untouched

### Requirement: G-code generation from SVG geometry
The generator SHALL convert the drawing's stroked geometry into a GRBL program in millimeters: open paths become open cut runs, closed shapes become closed loops; runs are separated by rapid moves at the safe height and each depth pass plunges from the previous pass. Curved segments SHALL be emitted within a stated tolerance (flattened polylines). The generated program SHALL respect the wizard's size, placement, depth, feed, and spindle parameters.

#### Scenario: Drawing becomes cut runs
- **WHEN** importing a drawing with an open polyline and a circle
- **THEN** the generated program contains two cut runs — one open, one closed — separated by rapid moves, with plunges per depth pass

#### Scenario: Parameters are honored
- **WHEN** the wizard requested a 100 mm width and 2 passes of 0.5 mm
- **THEN** the generated geometry is 100 mm wide at the chosen placement, cutting at Z −0.5 and Z −1.0, at the chosen feed and spindle speed

#### Scenario: Curvature within tolerance
- **WHEN** the drawing contains Bézier or arc segments
- **THEN** the generated polyline stays within the stated tolerance of the original curve

### Requirement: Generated program opens as a new document
Completing the wizard SHALL load the generated program into the editor as a new, unsaved document (default name derived from the SVG file), flowing through the existing live preview, statistics, and warnings. Any modified current document SHALL be protected by the existing unsaved-changes prompt.

#### Scenario: Import replaces the editor content safely
- **WHEN** completing the wizard with a modified current document and choosing save in the prompt
- **THEN** the current document is saved and the generated program opens as a new untitled-imported document with preview and statistics live

### Requirement: Round-trip validity
Generated programs SHALL parse with the application's GRBL parser without error diagnostics and SHALL reproduce the imported geometry's endpoints within the stated tolerance.

#### Scenario: Round trip through the parser
- **WHEN** a generated program is parsed
- **THEN** it yields no error diagnostics, and each run's endpoints match the imported geometry within tolerance
