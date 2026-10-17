# Proposal: svg-import-cut-side

## Why

The SVG import currently generates toolpaths **exactly on the shape outline**
("on line"). For CNC that is only correct when the line itself is the target
geometry: a real cutter has width, so cutting on the line of a shape removes
half the line width from each side. The standard workflow needs the path
offset by the tool radius so the tool's *edge* follows the true boundary:

- **Inside (inshape)** — for pockets and interior cuts: the tool stays
  within the shape, offset inward by its radius.
- **Outside (outshape)** — for profiling around a part: the tool stays
  outside, offset outward by its radius.
- **On line** — engraving along the path itself (current behavior).

The user explicitly requested this choice when importing drawings; it was
listed as a non-goal of `svg-import-wizard` and is now promoted to a real
feature.

## What Changes

- The **Import wizard gains a "Cut side" selection** — On line (default),
  Inside, Outside — plus a **"Cutter diameter (mm)" field** (default
  3.175 mm = 1/8"). The offset distance is the cutter radius so the tool's
  edge follows the shape boundary (grab semantics: inside keeps the tool
  within the shape, outside keeps it outside).
- The generator offsets **closed runs** inward/outward by the radius;
  corners are handled with round joins. Open runs remain on-line in this
  round (documented limitation).
- If an inward offset would self-intersect (geometry too tight for the
  cutter), the run **falls back to on-line with a warning** rather than
  producing a broken toolpath.
- Non-goals: self-intersection cleanup (keeping offset loops that cross
  themselves), fill/hatching, multiple offset rings, open-path side
  offsets.

## Capabilities

### New Capabilities

(none — extends the `svg-import` capability defined by the active
`svg-import-wizard` change)

### Modified Capabilities

- `svg-import`: ADDED "Cut side selection with tool offset" (wizard
  parameter + offset geometry + tight-geometry fallback).

## Impact

- **Code**: new pure `model/svg/PathOffsetter` (closed-polyline offset with
  round joins, simplicity check), `GCodeGenerator` applies the offset after
  scale/flip, `ImportWizard` gains cut-side combo + cutter diameter field,
  tests, README.
- **Dependencies**: none new — the offset is implemented directly (naive
  per-vertex offset + simplicity check; a full Clipper-style library is the
  documented escape hatch if quality demands it later).
- **Ecosystem**: none.
