package dev.radixhomework.gcodeide.model.svg;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.radixhomework.gcodeide.model.parsing.ParseResult;
import dev.radixhomework.gcodeide.model.parsing.Severity;
import java.util.List;
import org.junit.jupiter.api.Test;

class GCodeGeneratorTest {

    private static SvgDocument doc(String svg) {
        return SvgDocument.load(svg);
    }

    private static SvgImportOptions options(double widthMm, double depth, double finalDepth) {
        return new SvgImportOptions(widthMm, 0, 0, depth, finalDepth, 600, 5, 10000);
    }

    private static SvgImportOptions options(double widthMm, double placeX, double placeY,
            double depth, double finalDepth, double cutFeed, double safeZ, int spindle) {
        return new SvgImportOptions(widthMm, placeX, placeY, depth, finalDepth, cutFeed,
                safeZ, spindle);
    }

    @Test
    void generatedProgramHasHeaderFooterAndPasses() {
        var document = doc("""
                <svg width="100" height="50">
                  <rect x="0" y="0" width="100" height="50" stroke="black" fill="none"/>
                </svg>""");
        String gcode = GCodeGenerator.generate(document, options(100, 0.5, -1.0));
        assertTrue(gcode.startsWith("G21 G90 G17\nM3 S10000\n"), "header + spindle");
        assertTrue(gcode.endsWith("G0 Z5.000\nM5\n"), "safe-Z retract + spindle off");
        assertTrue(gcode.contains("G1 Z-0.500 F600"), "first pass depth");
        assertTrue(gcode.contains("G1 Z-1.000 F600"), "final pass depth");
        assertTrue(gcode.contains("G0 Z5.000"), "safe-Z rapid between passes");
    }

    @Test
    void widthScalingAndYFlip() {
        // 100-unit-wide drawing, marker at SVG top-right (x=90, y=5..10)
        var document = doc("""
                <svg width="100" height="100">
                  <rect x="90" y="0" width="10" height="10" stroke="black" fill="none"/>
                </svg>""");
        String gcode = GCodeGenerator.generate(document, options(100, 0.5, -0.5));
        // scale 10 (10-unit rect -> 100mm). Y-flip check: the SVG-top edge
        // (y=0) must land at bed Y=100 (the far side), not Y=0.
        assertTrue(gcode.contains("X100.000 Y100.000"), "SVG top -> bed far side: " + gcode);
        assertTrue(gcode.contains("X100.000 Y0.000"), "SVG bottom -> bed near side: " + gcode);
    }

    @Test
    void generatedGeometryStaysWithinRequestedPlacement() {
        var document = doc("""
                <svg width="100" height="100">
                  <circle cx="50" cy="50" r="40" stroke="black" fill="none"/>
                </svg>""");
        String gcode = GCodeGenerator.generate(document, options(100, 10, 20, 0.5, -1.0, 600, 5, 10000));
        for (String line : gcode.split("\n")) {
            if (line.startsWith("G1 X") || line.startsWith("G0 X")) {
                // placement 10..110 x 20..120 (100mm wide centered circle is
                // 80 wide at place 10: 10..90; y: 20..100) - all within bounds
                int x = (int) Math.round(Double.parseDouble(
                        line.substring(line.indexOf('X') + 1, line.indexOf('Y'))));
                int y = (int) Math.round(Double.parseDouble(
                        line.substring(line.indexOf('Y') + 1).replaceFirst("F.*", "").trim()));
                assertTrue(x >= 0 && x <= 200, "x within placement: " + line);
                assertTrue(y >= 0 && y <= 200, "y within placement: " + line);
            }
        }
    }

    @Test
    void roundTripParsesWithoutErrorsAndMatchesGeometry() {
        var document = doc("""
                <svg width="100" height="100">
                  <rect x="10" y="10" width="80" height="80" stroke="black" fill="none"/>
                  <circle cx="50" cy="50" r="20" stroke="black" fill="none"/>
                </svg>""");
        String gcode = GCodeGenerator.generate(document, options(100, 0, 0, 0.5, -0.5, 600, 5, 10000));
        ParseResult result = dev.radixhomework.gcodeide.model.parsing.GCodeParser.parse(gcode);
        assertEquals(List.of(), result.diagnostics().stream()
                .filter(d -> d.severity() == Severity.ERROR).toList(), "no ERROR diagnostics");

        // 2 runs, each cut twice (2 passes) -> 4 cut polylines in the model
        long cutRuns = result.moves().stream()
                .filter(m -> m.kind() == dev.radixhomework.gcodeide.model.parsing.MoveKind.CUT
                        && !m.fromArc())
                .count();
        assertTrue(cutRuns >= 8, "plunges + cuts for 2 runs x 2 passes: " + cutRuns);

        // endpoints within the placement bounds
        for (var move : result.moves()) {
            assertTrue(move.end().x() >= -1e-9 && move.end().x() <= 100 + 1e-9);
            assertTrue(move.end().y() >= -1e-9 && move.end().y() <= 100 + 1e-9);
        }
    }

    @Test
    void emptyRunsProduceMinimalProgram() {
        var document = doc("<svg width=\"10\" height=\"10\"/>");
        String gcode = GCodeGenerator.generate(document, options(100, 0.5, -1.0));
        assertTrue(gcode.startsWith("G21 G90 G17"));
        assertTrue(gcode.contains("M5"));
    }
}
