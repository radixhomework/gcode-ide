package dev.radixhomework.gcodeide.model.preview;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.radixhomework.gcodeide.model.parsing.GCodeParser;
import dev.radixhomework.gcodeide.model.parsing.Move;
import dev.radixhomework.gcodeide.model.parsing.MoveKind;
import dev.radixhomework.gcodeide.model.parsing.ParseResult;
import dev.radixhomework.gcodeide.model.parsing.Position;
import dev.radixhomework.gcodeide.model.profiles.MachineProfile;
import java.util.List;
import org.junit.jupiter.api.Test;

class DiagnosticsTest {

    private static final MachineProfile PROFILE = new MachineProfile(
            "Test Mill", 300.0, 180.0, 45.0, 800.0, 1000.0, 5.0);

    private static Move move(MoveKind kind, double[] start, double[] end, int line, Double feed) {
        return new Move(kind, new Position(start[0], start[1], start[2]),
                new Position(end[0], end[1], end[2]), line, feed, false,
                dev.radixhomework.gcodeide.model.parsing.Spindle.OFF);
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
        ToolpathStats stats = Diagnostics.computeStatistics(SYNTHETIC, PROFILE);
        assertEquals(new BBox(10.0, 0.0, 30.0, 25.0), stats.cutBBox());
        assertEquals(45.0, stats.cutDistance(), 1e-9);
        assertEquals(10.0 + Math.sqrt(1525), stats.rapidDistance(), 1e-9);
        double expected = 20.0 / 100 + 20.0 / 200 + 5.0 / 800 + (10.0 + Math.sqrt(1525)) / 1000;
        assertEquals(expected, stats.estimatedTimeMin(), 1e-9);
    }

    @Test
    void missingFeedMovesWarnedAndCountedAtMaxCut() {
        List<ToolpathWarning> warnings = Diagnostics.computeWarnings(SYNTHETIC, PROFILE);
        List<ToolpathWarning> missing = warnings.stream()
                .filter(w -> w.kind().equals(ToolpathWarning.MISSING_FEED)).toList();
        assertEquals(1, missing.size());
        assertEquals(List.of(4), missing.get(0).lines());
    }

    @Test
    void emptyToolpathStatistics() {
        ToolpathStats stats = Diagnostics.computeStatistics(new ParseResult(List.of(), List.of()), PROFILE);
        assertNull(stats.cutBBox());
        assertEquals(0.0, stats.cutDistance());
        assertEquals(0.0, stats.rapidDistance());
        assertEquals(0.0, stats.estimatedTimeMin());
    }

    @Test
    void outOfBedX350On300Bed() {
        List<ToolpathWarning> warnings =
                Diagnostics.computeWarnings(GCodeParser.parse("G1 X350 F600\n"), PROFILE);
        assertEquals(1, warnings.size());
        assertEquals(ToolpathWarning.OUT_OF_BED, warnings.get(0).kind());
        assertEquals(List.of(1), warnings.get(0).lines());
        assertTrue(warnings.get(0).message().contains("300"));
    }

    @Test
    void outOfBedCoversStartAndEndPoints() {
        List<ToolpathWarning> warnings =
                Diagnostics.computeWarnings(GCodeParser.parse("G0 X10 Y10\nG1 X-5 F600\n"), PROFILE);
        assertEquals(ToolpathWarning.OUT_OF_BED, warnings.get(0).kind());
        assertEquals(List.of(2), warnings.get(0).lines());
    }

    @Test
    void excessiveFeedF2000Vs800Cap() {
        List<ToolpathWarning> warnings =
                Diagnostics.computeWarnings(GCodeParser.parse("F2000\nG1 X10\n"), PROFILE);
        assertEquals(1, warnings.size());
        assertEquals(ToolpathWarning.EXCESSIVE_FEED, warnings.get(0).kind());
        assertEquals(List.of(2), warnings.get(0).lines());
        assertTrue(warnings.get(0).message().contains("800"));
    }

    @Test
    void feedAtCapIsNotFlagged() {
        assertTrue(Diagnostics.computeWarnings(GCodeParser.parse("F800\nG1 X10\n"), PROFILE).isEmpty());
    }
}
