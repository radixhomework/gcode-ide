# Design: svg-import-cut-side

## Context

Builds on the active `svg-import-wizard` implementation (shape outlines,
mm-space, free placement). The offset happens in the generator, after scale
and Y-flip, so the SVG model stays in user units.

## Decisions

### D1: Offset in mm space, per closed run
`GCodeGenerator` scales/flips each run to mm (existing `toMm`), then, when a
side is selected, offsets the closed polygon by radius = cutterDiameter/2
in the requested direction. Inward for a Y-up polygon = shrink; outward =
grow. Implementation in a new pure `model/svg/PathOffsetter`:
per-vertex round-join offset (each corner replaced by an arc approximation
through the join), then a simplicity check.

### D2: Round joins, no miter
Each convex corner emits an arc (sampled at the flattener tolerance)
centered on the original vertex; this is the natural CNC motion and avoids
miter spikes. Concave corners on the offset side collapse into the
neighboring segment intersection only when segments actually cross - v1
does not compute intersections: the simplicity check detects the
self-intersection instead.

### D3: Simplicity check and fallback
After offsetting a closed run, verify the ring is simple (no non-adjacent
segment intersections, O(n^2) - run sizes are small post-flattening). If
self-intersecting: fall back to the on-line run and add a warning naming
the run. This keeps every generated toolpath machinable.

### D4: Wizard surface
"Cut side" combo: On line (default) / Inside / Outside. "Cutter diameter
(mm)" field, default 3.175. Both feed `SvgImportOptions` (new fields
cutSide enum + cutterDiameterMm). Inside/Outside with open runs: open runs
stay on-line and a warning explains the v1 limitation (spec scenario).

## Risks / Trade-offs

- [Naive offset self-intersects on tight concave geometry] -> detected by
  the simplicity check; fallback keeps output machinable. Full
  Clipper-style cleanup is the documented upgrade path (would add a
  dependency).
- [Round joins at 0.01mm sampling inflate file size] -> arc sampling is
  bounded by the flattener tolerance; acceptable.

## Migration Plan

Additive; default On line preserves current output byte-for-byte when the
new fields are unused. Rollback = revert commit.

## Open Questions

- none blocking
