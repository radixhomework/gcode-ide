## Purpose

Defines how GRBL-dialect G-code text is interpreted into a canonical toolpath model (millimeter, absolute, line-attributed) that powers the preview, statistics, warnings, and - in later phases - round-trip tests of generated code.

## Requirements

### Requirement: Parse the supported GRBL motion subset
The parser SHALL interpret: `G0`/`G1` linear moves, `G2`/`G3` clockwise and counterclockwise arcs, modal motion continuation (a line containing only coordinates repeats the active motion mode), `G90`/`G91` absolute/incremental distance modes, `G20`/`G21` units, `F` feed rate, `S` spindle speed, `M3`/`M4`/`M5` spindle state, `N` line numbers, and both `;` and parenthesized comments. Whitespace, letter case, and blank lines SHALL be tolerated.

#### Scenario: Representative program
- **WHEN** parsing a program that uses linear moves, an arc, a feed change, and comments
- **THEN** the resulting toolpath model contains the corresponding moves with correct endpoints, ordered as written

### Requirement: Canonical model units and coordinates
The toolpath model SHALL be expressed in millimeters in bed coordinates (Y-up), regardless of `G20`/`G21` or `G90`/`G91` in the source; inch inputs SHALL be converted, and incremental inputs SHALL be accumulated.

#### Scenario: Incremental inches program
- **WHEN** parsing `G20 G91 G1 X1 Y0` followed by `G1 Y1`
- **THEN** the model records two cut moves ending at (25.4, 25.4) mm in bed coordinates

### Requirement: Arc representation within tolerance
Arcs (`G2`/`G3`, in both center-offset `I J` and radius `R` forms) SHALL be represented in the model as a sequence of linear sub-segments whose maximum deviation from the true arc stays within a configurable tolerance (default 0.01 mm).

#### Scenario: Quarter-circle arc flattening
- **WHEN** parsing a 90 degrees arc of radius 10 mm
- **THEN** the arc's sub-segments each deviate from the true arc by no more than the configured tolerance, and the chain's endpoints match the arc's endpoints

### Requirement: Per-line attribution
Every movement in the toolpath model SHALL record the 1-based line number of the source text that produced it.

#### Scenario: Attribution of a move
- **WHEN** the movement described on line 42 of the document is inspected
- **THEN** it reports source line 42

### Requirement: Modal state tracking
The model SHALL associate each cut move with the active feed rate and spindle state at that move, carrying modal values forward until changed.

#### Scenario: Feed applies until changed
- **WHEN** `F600` appears on one line and subsequent lines contain moves without an `F`
- **THEN** those moves are associated with feed rate 600, until a later `F` takes effect

### Requirement: Tolerance of unsupported words
Unrecognized or unsupported words and codes (e.g., `G43`, `T1`, `M8`) SHALL NOT abort parsing; the parser SHALL record a diagnostic citing the line number and continue with best-effort state.

#### Scenario: Unsupported code does not stop parsing
- **WHEN** parsing a program containing `G43 H1`
- **THEN** parsing completes, subsequent moves are present in the model, and a diagnostic notes the unsupported code and its line

### Requirement: Malformed lines are reported, not fatal
Lines that cannot be interpreted (e.g., a word without a number, or an arc missing its center/radius data) SHALL produce a diagnostic with the line number; the parser SHALL skip the offending line and continue.

#### Scenario: Bad arc data
- **WHEN** a `G2` line lacks both `I/J` and `R`
- **THEN** a diagnostic reports that line, and moves on surrounding lines still appear in the model
