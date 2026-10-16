package dev.radixhomework.gcodeide.model.svg;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.radixhomework.gcodeide.model.parsing.GCodeParser;
import org.junit.jupiter.api.Test;

class CutSideTest {

    private static final String SQUARE = """
            <svg width="40" height="40">
              <rect x="0" y="0" width="40" height="40" stroke="black" fill="none"/>
            </svg>""";

    private static double[] extents(String gcode) {
        double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY;
        var result = GCodeParser.parse(gcode);
        for (var move : result.moves()) {
            if (move.kind() != dev.radixhomework.gcodeide.model.parsing.MoveKind.CUT) {
                continue; // measure machined geometry, not travel rapids
            }
            minX = Math.min(minX, Math.min(move.start().x(), move.end().x()));
            minY = Math.min(minY, Math.min(move.start().y(), move.end().y()));
            maxX = Math.max(maxX, Math.max(move.start().x(), move.end().x()));
            maxY = Math.max(maxY, Math.max(move.start().y(), move.end().y()));
        }
        return new double[] {minX, minY, maxX, maxY};
    }

    @Test
    void insideCutShrinksByCutterRadius() {
        var document = SvgDocument.load(SQUARE);
        String gcode = GCodeGenerator.generate(document, new SvgImportOptions(
                40, 0, 0, 0.5, -1.0, 600, 5, 10000, CutSide.INSIDE, 3.175));
        var e = extents(gcode);
        // radius 1.5875: outline 0..40 -> inside cut 1.5875..38.4125
        assertEquals(1.5875, e[0], 0.01);
        assertEquals(1.5875, e[1], 0.01);
        assertEquals(38.4125, e[2], 0.01);
        assertEquals(38.4125, e[3], 0.01);
    }

    @Test
    void outsideCutGrowsByCutterRadius() {
        var document = SvgDocument.load(SQUARE);
        String gcode = GCodeGenerator.generate(document, new SvgImportOptions(
                40, 0, 0, 0.5, -1.0, 600, 5, 10000, CutSide.OUTSIDE, 3.175));
        var e = extents(gcode);
        assertEquals(-1.5875, e[0], 0.01);
        assertEquals(-1.5875, e[1], 0.01);
        assertEquals(41.5875, e[2], 0.01);
        assertEquals(41.5875, e[3], 0.01);
    }

    @Test
    void onLineCutKeepsTheOutline() {
        var document = SvgDocument.load(SQUARE);
        String gcode = GCodeGenerator.generate(document, new SvgImportOptions(
                40, 0, 0, 0.5, -1.0, 600, 5, 10000, CutSide.ON_LINE, 3.175));
        var e = extents(gcode);
        assertEquals(0.0, e[0], 0.01);
        assertEquals(40.0, e[2], 0.01);
    }

    @Test
    void offsetProgramParsesCleanly() {
        var document = SvgDocument.load(SQUARE);
        String gcode = GCodeGenerator.generate(document, new SvgImportOptions(
                40, 0, 0, 0.5, -1.0, 600, 5, 10000, CutSide.INSIDE, 3.175));
        var result = GCodeParser.parse(gcode);
        assertTrue(result.diagnostics().stream()
                .noneMatch(d -> d.severity() == dev.radixhomework.gcodeide.model.parsing.Severity.ERROR),
                "no ERROR diagnostics");
    }

    @Test
    void tightGeometryFallsBackWithWarningComment() {
        // 0.5mm wide slot: a 3.175mm cutter cannot fit inside; inward offset
        // self-intersects -> fallback to on-line with a warning comment
        var document = SvgDocument.load("""
                <svg width="10" height="10">
                  <rect x="0" y="0" width="0.5" height="10" stroke="black" fill="none"/>
                </svg>""");
        String gcode = GCodeGenerator.generate(document, new SvgImportOptions(
                0.5, 0, 0, 0.5, -1.0, 600, 5, 10000, CutSide.INSIDE, 3.175));
        assertTrue(gcode.contains("(warning:"), "fallback warning comment present");
        assertTrue(gcode.contains("G1 X0.500"), "on-line fallback geometry");
    }

    @Test
    void openRunsStayOnLineWithWarningWhenSideSelected() {
        // open line along the top edge + closed square: the open run stays
        // on-line with a warning, the closed run is still offset inward
        var document = SvgDocument.load("""
                <svg width="40" height="40">
                  <path d="M 0 0 L 40 0" stroke="black" fill="none"/>
                  <rect x="0" y="0" width="40" height="40" stroke="black" fill="none"/>
                </svg>""");
        String gcode = GCodeGenerator.generate(document, new SvgImportOptions(
                40, 0, 0, 0.5, -1.0, 600, 5, 10000, CutSide.INSIDE, 3.175));
        assertTrue(gcode.contains("(warning:"), "open-run warning present");
        assertTrue(gcode.contains("X40.000 Y40.000"), "open run cut on-line");
        assertTrue(gcode.contains("X38.413 Y38.413"),
                "closed run still offset inward");
    }
}
