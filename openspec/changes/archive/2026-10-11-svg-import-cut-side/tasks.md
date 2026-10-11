# Tasks: svg-import-cut-side

- [x] 1.1 Implement `model/svg/PathOffsetter`: closed-polygon offset by radius with round joins (per-vertex arc approximation at flattener tolerance) for Inside (shrink) and Outside (grow); pure functions, no JavaFX
- [x] 1.2 Simplicity check: detect self-intersecting offset rings (O(n^2) non-adjacent segment test); self-intersecting runs fall back to on-line with a warning (plus an area-direction check that rejects inward offsets collapsing through geometry tighter than the cutter)
- [x] 1.3 Unit tests: square inside/outside dimensions, round joins present, non-convex polygon offset, self-intersection fallback
- [x] 2.1 `SvgImportOptions` gains cutSide (ON_LINE/INSIDE/OUTSIDE) + cutterDiameterMm; `GCodeGenerator` applies `PathOffsetter` per closed run after scale/flip; generator tests for both directions and on-line default
- [x] 2.2 Round-trip test: offset output parses with zero ERROR diagnostics and the offset ring stays on the requested side of the original boundary
- [x] 3.1 Wizard gains "Cut side" combo and "Cutter diameter (mm)" field (default 3.175), feeding the options; smoke test drives Inside and asserts the generated path is the shrunk square
- [x] 3.2 Open runs: stay on-line with a warning when a side is selected (spec scenario); integration test covers it
- [x] 4.1 README note (cut side + cutter diameter)
- [x] 4.2 Full `mvn test` green; rebuild the app image; verify by hand: Inside vs Outside vs On line on the demo square
