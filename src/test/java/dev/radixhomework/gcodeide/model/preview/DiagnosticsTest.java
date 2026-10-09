package dev.radixhomework.gcodeide.model.preview;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.radixhomework.gcodeide.model.parsing.GCodeParser;
import dev.radixhomework.gcodeide.model.parsing.Move;
import dev.radixhomework.gcodeide.model.parsing.MoveKind;
import dev.radixhomework.gcodeide.model.parsing.ParseResult;
import dev.radixhomework.gcodeide.model.parsing.Position;
import dev.radixhomework.gcodeide.model.parsing.Spindle;
import java.util.List;
import org.junit.jupiter.api.Test;

class DiagnosticsTest {

    private static Move move(MoveKind kind, double[] start, double[] end, int line, Double feed) {
        return new Move(kind, new Position(start[0], start[1], start[2]),
                new Position(end[0], end[1], end[2]), line, feed, false, Spindle.OFF);
    }

    private static final ParseResult SYNTHETIC = new ParseResult(List.of(
            move(MoveKind.RAPID, new double[] {0, 0, 0}, new double[] {10, 0, 0}, 1, null),
            move(MoveKind.CUT, new double[] {10, 0, -1}, new double[] {10, 20, -1}, 2, 100.0),
            move(MoveKind.CUT, new double[] {10, 20, -1}, new double[] {30, 20, -1}, 3, 200.0),
            move(MoveKind.CUT, new double[] {30, 20, -1}, new double[] {30, 25, -1}, 4, null),
            move(MoveKind.RAPID, new double[] {30, 25, 0}, new double[] {0, 0, 0}, 5, null)),
            List.of());

    @Test
    void statisticsMatchHandComputedValues() {
        ToolpathStats stats = Diagnostics.computeStatistics(SYNTHETIC);
        assertEquals(new BBox(10.0, 0.0, 30.0, 25.0), stats.cutBBox());
        assertEquals(45.0, stats.cutDistance(), 1e-9);
        assertEquals(10.0 + Math.sqrt(1525), stats.rapidDistance(), 1e-9);
        // cut-only time: rapids and the feedless cut add no time
        double expected = 20.0 / 100 + 20.0 / 200;
        assertEquals(expected, stats.estimatedTimeMin(), 1e-9);
    }

    @Test
    void missingFeedMovesWarnedAndUntimed() {
        List<ToolpathWarning> warnings = Diagnostics.computeWarnings(SYNTHETIC);
        List<ToolpathWarning> missing = warnings.stream()
                .filter(w -> w.kind().equals(ToolpathWarning.MISSING_FEED)).toList();
        assertEquals(1, missing.size());
        assertEquals(List.of(4), missing.get(0).lines());
    }

    @Test
    void emptyToolpathStatistics() {
        ToolpathStats stats = Diagnostics.computeStatistics(new ParseResult(List.of(), List.of()));
        assertNull(stats.cutBBox());
        assertEquals(0.0, stats.cutDistance());
        assertEquals(0.0, stats.rapidDistance());
        assertEquals(0.0, stats.estimatedTimeMin());
    }

    @Test
    void geometryBeyondAnyEnvelopeIsNotFlagged() {
        // machine-profile-free: there is no envelope, so far-away geometry
        // with a commanded feed produces no warnings at all
        List<ToolpathWarning> warnings =
                Diagnostics.computeWarnings(GCodeParser.parse("G1 X99999 F600\n"));
        assertEquals(List.of(), warnings);
    }
}
