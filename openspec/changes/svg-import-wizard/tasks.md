# Tasks: svg-import-wizard

## 1. Pure SVG model (model/svg)

- [ ] 1.1 Implement `SvgPathData` parser (M/L/H/V/C/S/Q/T/A/Z, absolute + relative) with an endpoint-to-center conversion for `A`; goldens cover every command, relative forms, implicit repeats, and the arc flags (large-arc/sweep) on a known ellipse
- [ ] 1.2 Implement `SvgDocument.load`: DOM parse, shape→path conversion (rect, circle, ellipse, line, polyline, polygon), the transform subset (translate/scale/rotate/matrix), stroke-only geometry extraction, and warnings for unsupported constructs; tests cover a fixture SVG with supported shapes, a transformed group, and two unsupported constructs
- [ ] 1.3 Implement curve flattening to the shared 0.01 mm tolerance; tests assert the tolerance bound on C and A segments and that closed shapes close

## 2. G-code generator (model/svg)

- [ ] 2.1 Implement `GCodeGenerator` (program shape per design D3: header, spindle, safe-Z rapids, per-pass plunges, cut runs, footer; SVG Y flip; scale/place/clamp to bed) with pure tests: parameter honoring (100 mm width, 2×0.5 mm passes, feed, spindle), Y-flip orientation marker, bed clamping
- [ ] 2.2 Implement the round-trip test: generate from the fixture SVG, parse with `GCodeParser`, assert zero ERROR diagnostics, bounds containment, run count, and endpoint match within tolerance

## 3. Wizard UI and wiring

- [ ] 3.1 Implement the import wizard dialog (parameter grid with defaults + computed mm size + warnings list; Cancel imports nothing) and the pure parameter→generator-options bridge; TestFX smoke test opens the wizard on a fixture SVG via a stubbed chooser, adjusts width, completes, and asserts the editor loaded generated text as a new unsaved document; a cancel test asserts the editor is untouched
- [ ] 3.2 Add File > Import Image... (image file filter) to the FXML/controller, reusing the chooser pattern and `last_open_dir`; wire completion through the existing unsaved-changes protection; integration test asserts Open still filters G-code and Import filters images

## 4. Wrap-up

- [ ] 4.1 Update README (Import Image, supported SVG subset); full `mvn test` green; rebuild the app image and verify by hand with a real drawing: wizard defaults, generated program in preview, warnings where expected
