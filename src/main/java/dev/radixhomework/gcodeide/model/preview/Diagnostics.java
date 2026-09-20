package dev.radixhomework.gcodeide.model.preview;

import dev.radixhomework.gcodeide.model.parsing.Move;
import dev.radixhomework.gcodeide.model.parsing.MoveKind;
import dev.radixhomework.gcodeide.model.parsing.ParseResult;
import dev.radixhomework.gcodeide.model.profiles.MachineProfile;
import java.util.ArrayList;
import java.util.List;

/**
 * Toolpath statistics and safety warnings (pure model). Computed on demand
 * from (ParseResult, MachineProfile) — never stored in the toolpath model.
 */
public final class Diagnostics {

    private Diagnostics() {
    }

    public static double moveLength(Move move) {
        return Math.hypot(
                Math.hypot(move.end().x() - move.start().x(), move.end().y() - move.start().y()),
                move.end().z() - move.start().z());
    }

    /** True if either endpoint leaves the profile's travel envelope. */
    public static boolean moveOutOfBed(Move move, MachineProfile profile) {
        for (var p : new dev.radixhomework.gcodeide.model.parsing.Position[] {move.start(),
                move.end()}) {
            if (p.x() < 0 || p.x() > profile.bedX()) {
                return true;
            }
            if (p.y() < 0 || p.y() > profile.bedY()) {
                return true;
            }
            if (p.z() < -profile.bedZ() || p.z() > profile.bedZ()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Bounding box, distances, and estimated run time. Cut moves without an
     * active feed are counted at {@code maxCut}; rapids run at {@code maxRapid}.
     */
    public static ToolpathStats computeStatistics(ParseResult result, MachineProfile profile) {
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
                double feed = move.feed() != null && move.feed() > 0 ? move.feed() : profile.maxCut();
                timeMin += length / feed;
            } else {
                rapidDistance += length;
                timeMin += length / profile.maxRapid();
            }
        }

        BBox bbox = Double.isFinite(minX) ? new BBox(minX, minY, maxX, maxY) : null;
        return new ToolpathStats(bbox, cutDistance, rapidDistance, timeMin);
    }

    /** Out-of-bed, excessive-feed, and missing-feed warnings with source lines. */
    public static List<ToolpathWarning> computeWarnings(ParseResult result, MachineProfile profile) {
        List<Integer> outOfBedLines = new ArrayList<>();
        List<Integer> overFeedLines = new ArrayList<>();
        List<Integer> missingFeedLines = new ArrayList<>();

        for (Move move : result.moves()) {
            if (moveOutOfBed(move, profile) && !outOfBedLines.contains(move.line())) {
                outOfBedLines.add(move.line());
            }
            if (move.kind() == MoveKind.CUT) {
                if (move.feed() == null) {
                    if (!missingFeedLines.contains(move.line())) {
                        missingFeedLines.add(move.line());
                    }
                } else if (move.feed() > profile.maxCut()
                        && !overFeedLines.contains(move.line())) {
                    overFeedLines.add(move.line());
                }
            }
        }

        List<ToolpathWarning> warnings = new ArrayList<>();
        if (!outOfBedLines.isEmpty()) {
            warnings.add(new ToolpathWarning(ToolpathWarning.OUT_OF_BED,
                    String.format("Moves outside the %.0fx%.0f mm bed on lines %s",
                            profile.bedX(), profile.bedY(), joinLines(outOfBedLines)),
                    outOfBedLines));
        }
        if (!overFeedLines.isEmpty()) {
            warnings.add(new ToolpathWarning(ToolpathWarning.EXCESSIVE_FEED,
                    String.format("Feed above the %.0f mm/min cap on lines %s",
                            profile.maxCut(), joinLines(overFeedLines)),
                    overFeedLines));
        }
        if (!missingFeedLines.isEmpty()) {
            warnings.add(new ToolpathWarning(ToolpathWarning.MISSING_FEED,
                    String.format("Cut moves without a feed (estimated at %.0f mm/min) on lines %s",
                            profile.maxCut(), joinLines(missingFeedLines)),
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
