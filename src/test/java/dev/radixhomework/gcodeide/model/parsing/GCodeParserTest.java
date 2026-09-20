package dev.radixhomework.gcodeide.model.parsing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Ports of the reference suite's modal-interpreter and arc scenarios. */
class GCodeParserTest {

    private static List<Move> cuts(ParseResult result) {
        return result.moves().stream().filter(m -> m.kind() == MoveKind.CUT).toList();
    }

    private static List<Move> arcs(ParseResult result) {
        return result.moves().stream().filter(Move::fromArc).toList();
    }

    private static List<Diagnostic> ofSeverity(ParseResult result, Severity severity) {
        return result.diagnostics().stream().filter(d -> d.severity() == severity).toList();
    }

    @Test
    void incrementalInchesEndAt25_4() {
        ParseResult result = GCodeParser.parse("G20 G91 G1 X1 Y0\nG1 Y1\n");
        List<Move> cuts = cuts(result);
        assertEquals(2, cuts.size());
        assertEquals(25.4, cuts.get(0).end().x(), 1e-9);
        assertEquals(0.0, cuts.get(0).end().y(), 1e-9);
        assertEquals(25.4, cuts.get(1).end().x(), 1e-9);
        assertEquals(25.4, cuts.get(1).end().y(), 1e-9);
    }

    @Test
    void feedIsModal() {
        ParseResult result = GCodeParser.parse("F600\nG1 X10\nG1 X20\nF900\nG1 X30\n");
        assertEquals(List.of(600.0, 600.0, 900.0),
                result.moves().stream().map(Move::feed).toList());
    }

    @Test
    void feedConvertedFromInches() {
        ParseResult result = GCodeParser.parse("G20\nG1 X1 F10\n");
        assertEquals(254.0, result.moves().get(0).feed(), 1e-9);
    }

    @Test
    void rapidMovesCarryNoFeed() {
        ParseResult result = GCodeParser.parse("F600\nG0 X10\nG1 X20\n");
        Move rapid = result.moves().get(0);
        Move cut = result.moves().get(1);
        assertEquals(MoveKind.RAPID, rapid.kind());
        assertTrue(rapid.feed() == null);
        assertEquals(MoveKind.CUT, cut.kind());
        assertEquals(600.0, cut.feed());
    }

    @Test
    void modalMotionContinuation() {
        ParseResult result = GCodeParser.parse("G1 X10 Y0 F500\nX20\nX30\n");
        assertEquals(3, result.moves().size());
        assertTrue(result.moves().stream().allMatch(m -> m.kind() == MoveKind.CUT));
        assertEquals(30.0, result.moves().get(2).end().x(), 1e-9);
    }

    @Test
    void absoluteAndIncrementalModes() {
        ParseResult result = GCodeParser.parse("G90 G1 X10 Y10\nG91 G1 X5 Y-5\n");
        assertEquals(10.0, result.moves().get(0).end().x(), 1e-9);
        assertEquals(15.0, result.moves().get(1).end().x(), 1e-9);
        assertEquals(5.0, result.moves().get(1).end().y(), 1e-9);
    }

    @Test
    void spindleStateTrackedAndAssociated() {
        ParseResult result = GCodeParser.parse("M3 S10000\nG1 X10 F600\nM5\nG1 X20\n");
        assertEquals(List.of(Spindle.CW, Spindle.OFF),
                result.moves().stream().map(Move::spindle).toList());
    }

    @Test
    void lineAttribution() {
        ParseResult result = GCodeParser.parse("\n\nG0 X10\n\nG1 X20 F600\n");
        assertEquals(List.of(3, 5), result.moves().stream().map(Move::line).toList());
    }

    @Test
    void lineNumberWordsIgnored() {
        ParseResult result = GCodeParser.parse("N10 G1 X10 F600\nN20 G1 X20\n");
        assertEquals(2, result.moves().size());
        assertTrue(result.diagnostics().isEmpty());
    }

    // --- arcs ---------------------------------------------------------------

    @Test
    void quarterCircleIjFormFlatteningTolerance() {
        ParseResult result = GCodeParser.parse("G21 G90\nG0 X0 Y0\nG2 X10 Y10 I10 F600\n", 0.01);
        List<Move> arcMoves = arcs(result);
        assertTrue(!arcMoves.isEmpty());
        assertTrue(arcMoves.stream().allMatch(m -> m.kind() == MoveKind.CUT));
        assertEquals(0.0, arcMoves.get(0).start().x(), 1e-9);
        assertEquals(0.0, arcMoves.get(0).start().y(), 1e-9);
        Position last = arcMoves.get(arcMoves.size() - 1).end();
        assertEquals(10.0, last.x(), 1e-9);
        assertEquals(10.0, last.y(), 1e-9);
        double cx = 10.0;
        double cy = 0.0;
        for (Move m : arcMoves) {
            double midX = (m.start().x() + m.end().x()) / 2;
            double midY = (m.start().y() + m.end().y()) / 2;
            double dist = Math.hypot(midX - cx, midY - cy);
            assertTrue(dist - 10.0 <= 0.01 + 1e-9, "chord midpoint deviation within tolerance");
        }
        assertEquals(3, arcMoves.get(0).line());
    }

    @Test
    void rFormMinorArcQuarter() {
        ParseResult result = GCodeParser.parse("G3 X10 Y10 R10 F600\n");
        List<Move> arcMoves = arcs(result);
        assertTrue(!arcMoves.isEmpty());
        double total = sweptAngle(arcMoves);
        assertEquals(Math.PI / 2, total, 1e-6);
    }

    @Test
    void rFormNegativeRSelectsMajorArc() {
        ParseResult result = GCodeParser.parse("G3 X10 Y10 R-10 F600\n");
        double total = sweptAngle(arcs(result));
        assertEquals(3 * Math.PI / 2, total, 1e-6);
    }

    private static double sweptAngle(List<Move> arcMoves) {
        double cx = circumX(arcMoves);
        double cy = circumY(arcMoves);
        double total = 0.0;
        for (Move m : arcMoves) {
            double a0 = Math.atan2(m.start().y() - cy, m.start().x() - cx);
            double a1 = Math.atan2(m.end().y() - cy, m.end().x() - cx);
            total += (a1 - a0) % (2 * Math.PI);
        }
        return total;
    }

    private static double circumX(List<Move> arcMoves) {
        return circum(arcMoves)[0];
    }

    private static double circumY(List<Move> arcMoves) {
        return circum(arcMoves)[1];
    }

    private static double[] circum(List<Move> arcMoves) {
        Position a = arcMoves.get(0).start();
        Position b = arcMoves.get(arcMoves.size() / 2).end();
        Position c = arcMoves.get(arcMoves.size() - 1).end();
        double d = 2 * (a.x() * (b.y() - c.y()) + b.x() * (c.y() - a.y()) + c.x() * (a.y() - b.y()));
        double ux = ((a.x() * a.x() + a.y() * a.y()) * (b.y() - c.y())
                + (b.x() * b.x() + b.y() * b.y()) * (c.y() - a.y())
                + (c.x() * c.x() + c.y() * c.y()) * (a.y() - b.y())) / d;
        double uy = ((a.x() * a.x() + a.y() * a.y()) * (c.x() - b.x())
                + (b.x() * b.x() + b.y() * b.y()) * (a.x() - c.x())
                + (c.x() * c.x() + c.y() * c.y()) * (b.x() - a.x())) / d;
        return new double[] {ux, uy};
    }

    @Test
    void fullCircleIjForm() {
        ParseResult result = GCodeParser.parse("G2 I-5 F600\n");
        List<Move> arcMoves = arcs(result);
        assertTrue(arcMoves.size() >= 8);
        Position last = arcMoves.get(arcMoves.size() - 1).end();
        assertEquals(0.0, last.x(), 1e-9);
        assertEquals(0.0, last.y(), 1e-9);
        double total = arcMoves.stream()
                .mapToDouble(m -> Math.hypot(m.end().x() - m.start().x(), m.end().y() - m.start().y()))
                .sum();
        assertEquals(2 * Math.PI * 5, total, 0.5);
    }

    @Test
    void arcMissingCenterAndRadiusDiagnosed() {
        ParseResult result = GCodeParser.parse("G0 X10 Y10\nG2 X20 Y20\nG1 X30 Y30 F600\n");
        List<Diagnostic> warnings = ofSeverity(result, Severity.WARNING);
        assertEquals(1, warnings.size());
        assertEquals(2, warnings.get(0).line());
        assertEquals(2, result.moves().size());
        assertEquals(30.0, result.moves().get(1).end().x(), 1e-9);
    }

    @Test
    void arcRadiusTooSmallDiagnosed() {
        ParseResult result = GCodeParser.parse("G2 X20 Y0 R5\n");
        assertTrue(result.moves().isEmpty());
        List<Diagnostic> warnings = ofSeverity(result, Severity.WARNING);
        assertEquals(1, warnings.size());
        assertEquals(1, warnings.get(0).line());
    }

    // --- tolerance & golden fixture -------------------------------------------

    @Test
    void unsupportedWordsAreInfoAndParsingContinues() {
        ParseResult result = GCodeParser.parse("G43 H1\nM8\nT1\nG1 X10 F600\n");
        assertEquals(1, result.moves().size());
        assertEquals(10.0, result.moves().get(0).end().x(), 1e-9);
        List<Diagnostic> infos = ofSeverity(result, Severity.INFO);
        assertEquals(4, infos.size()); // G43, H1, M8, T1
        assertEquals(List.of(1, 2, 3), infos.stream().map(Diagnostic::line).distinct().toList());
        assertTrue(infos.get(0).message().contains("G43"));
    }

    @Test
    void malformedNumberIsErrorAndLineSkipped() {
        ParseResult result = GCodeParser.parse("G1 X10 F600\nG1 X\nG1 Y10\n");
        List<Diagnostic> errors = ofSeverity(result, Severity.ERROR);
        assertEquals(1, errors.size());
        assertEquals(2, errors.get(0).line());
        assertEquals(2, result.moves().size());
        assertEquals(10.0, result.moves().get(1).end().y(), 1e-9);
    }

    @Test
    void goldenProgramEndToEnd() {
        String text = """
                ; Golden fixture: linear, arcs, comments, units/mode switches, unsupported code
                G21 G90 G17
                G0 X10 Y10 Z5        (rapid approach)
                G1 Z-1 F600          ; plunge
                G1 X20 Y10
                G3 X30 Y20 R10       ; quarter circle, R form
                G2 X40 Y10 I-10 J0   ; quarter circle, I/J form (cw)
                G91                  ; incremental
                G1 X5 Y5
                G20                  ; inches from here
                G1 X1
                G21 G90
                M8                   ; unsupported coolant code, ignored
                G1 X50 Y10
                """;
        ParseResult result = GCodeParser.parse(text);
        List<Move> cuts = cuts(result);
        List<Move> rapids = result.moves().stream().filter(m -> m.kind() == MoveKind.RAPID).toList();
        assertEquals(1, rapids.size());
        Position rapidEnd = rapids.get(0).end();
        assertEquals(10.0, rapidEnd.x(), 1e-9);
        assertEquals(10.0, rapidEnd.y(), 1e-9);
        assertEquals(5.0, rapidEnd.z(), 1e-9);
        assertTrue(arcs(result).size() > 0);
        List<double[]> nonArcEndpoints = cuts.stream()
                .filter(m -> !m.fromArc())
                .map(m -> new double[] {round6(m.end().x()), round6(m.end().y())})
                .toList();
        assertTrue(containsPoint(nonArcEndpoints, 20.0, 10.0));
        assertTrue(containsPoint(nonArcEndpoints, 45.0, 15.0));
        assertTrue(containsPoint(nonArcEndpoints, 70.4, 15.0));
        assertTrue(containsPoint(nonArcEndpoints, 50.0, 10.0));
        assertEquals(List.of(Severity.INFO), result.diagnostics().stream().map(Diagnostic::severity).toList());
        assertEquals(13, result.diagnostics().get(0).line()); // M8 (text block starts at line 1)
        assertTrue(result.moves().stream().allMatch(m -> m.line() >= 3));
    }

    private static double round6(double v) {
        return Math.round(v * 1e6) / 1e6;
    }

    private static boolean containsPoint(List<double[]> points, double x, double y) {
        return points.stream().anyMatch(p -> p[0] == x && p[1] == y);
    }
}
