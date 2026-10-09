package dev.radixhomework.gcodeide.model.preview;

import dev.radixhomework.gcodeide.model.parsing.Move;
import dev.radixhomework.gcodeide.model.parsing.MoveKind;
import dev.radixhomework.gcodeide.model.parsing.ParseResult;
import java.util.ArrayList;
import java.util.List;

/**
 * Toolpath statistics and dialect warnings (pure model). Computed on demand
 * from a {@link ParseResult} — never stored in the toolpath model.
 *
 * <p>Machine-profile-free (change remove-machine-profiles): no bed envelope
 * checks and no feed caps exist. The estimated time covers commanded cut
 * moves only — rapids and feedless cuts contribute distance but no time, and
 * a feedless cut is flagged as a dialect warning.
 */
public final class Diagnostics {

    private Diagnostics() {
    }

    public static double moveLength(Move move) {
        return Math.hypot(
                Math.hypot(move.end().x() - move.start().x(), move.end().y() - move.start().y()),
                move.end().z() - move.start().z());
    }

    /**
     * Bounding box, distances, and estimated cutting time: cut moves with a
     * commanded feed contribute {@code length / feed}; rapids and feedless
     * cuts add no time.
     */
    public static ToolpathStats computeStatistics(ParseResult result) {
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        double cutDistance = 0.0;
        double rapidDistance = 0.0;
        double timeMin = 0.0;

        for (Move move : result.moves()) {
            double length = moveLength(move);
            if (move.kind() == MoveKind.CUT) {
                cutDistance += length;
                minX = Math.min(minX, Math.min(move.start().x(), move.end().x()));
                minY = Math.min(minY, Math.min(move.start().y(), move.end().y()));
                maxX = Math.max(maxX, Math.max(move.start().x(), move.end().x()));
                maxY = Math.max(maxY, Math.max(move.start().y(), move.end().y()));
                if (move.feed() != null && move.feed() > 0) {
                    timeMin += length / move.feed();
                }
            } else {
                rapidDistance += length;
            }
        }

        BBox bbox = Double.isFinite(minX) ? new BBox(minX, minY, maxX, maxY) : null;
        return new ToolpathStats(bbox, cutDistance, rapidDistance, timeMin);
    }

    /** Dialect warnings: cut moves without a commanded feed, with source lines. */
    public static List<ToolpathWarning> computeWarnings(ParseResult result) {
        List<Integer> missingFeedLines = new ArrayList<>();
        for (Move move : result.moves()) {
            if (move.kind() == MoveKind.CUT && move.feed() == null
                    && !missingFeedLines.contains(move.line())) {
                missingFeedLines.add(move.line());
            }
        }

        List<ToolpathWarning> warnings = new ArrayList<>();
        if (!missingFeedLines.isEmpty()) {
            warnings.add(new ToolpathWarning(ToolpathWarning.MISSING_FEED,
                    "Cut moves without a commanded feed on lines " + joinLines(missingFeedLines),
                    missingFeedLines));
        }
        return warnings;
    }

    private static String joinLines(List<Integer> lines) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(lines.get(i));
        }
        return sb.toString();
    }
}
