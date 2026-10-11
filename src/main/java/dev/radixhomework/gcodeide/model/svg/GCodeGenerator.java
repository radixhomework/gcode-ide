package dev.radixhomework.gcodeide.model.svg;

import java.util.ArrayList;
import java.util.List;

/**
 * Generates GRBL G-code from imported SVG geometry: header, spindle on,
 * per-run rapids at safe height, plunges per depth pass, cut runs, spindle
 * off. Output is millimeters; SVG Y-down is flipped to bed Y-up. Placement
 * is a free offset (the app is machine-profile-free, no clamping).
 *
 * When a cut side (Inside/Outside) is selected, closed runs are offset by
 * half the cutter diameter so the tool edge follows the true boundary; runs
 * whose offset self-intersects fall back to on-line with a warning comment.
 */
public final class GCodeGenerator {

    private GCodeGenerator() {
    }

    public static String generate(SvgDocument doc, SvgImportOptions options) {
        return generateResult(doc, options).gcode();
    }

    /** Generated program plus warnings (self-intersection fallbacks); the
     *  warnings are also embedded in the program as G-code comments. */
    public record GenerateResult(String gcode, List<String> warnings) {
    }

    public static GenerateResult generateResult(SvgDocument doc, SvgImportOptions options) {
        var sb = new StringBuilder();
        List<String> warnings = new ArrayList<>();
        sb.append("G21 G90 G17\n");
        sb.append("M3 S").append(options.spindleRpm()).append('\n');
        if (options.cutSide() != CutSide.ON_LINE) {
            sb.append("(cut side: ").append(options.cutSide() == CutSide.INSIDE
                    ? "inside" : "outside").append(")\n");
        }

        int runIndex = 0;
        for (SvgRun run : doc.runs()) {
            if (run.points().size() < 2) {
                continue;
            }
            runIndex++;
            double[][] mm = toMm(run, doc, options);
            boolean closed = run.closed();
            if (closed && options.cutSide() != CutSide.ON_LINE) {
                double radius = options.cutterDiameterMm() / 2;
                var ring = new ArrayList<double[]>();
                for (double[] p : mm) {
                    ring.add(p.clone());
                }
                var offset = PathOffsetter.offsetClosed(ring, radius,
                        options.cutSide() == CutSide.OUTSIDE, SvgFlattener.TOLERANCE);
                if (PathOffsetter.offsetIsValid(ring, offset,
                        options.cutSide() == CutSide.OUTSIDE)) {
                    mm = offset.toArray(new double[0][]);
                } else {
                    String warning = "run " + runIndex + ": " + options.cutSide()
                            + " offset self-intersects (geometry too tight); cut on-line";
                    warnings.add(warning);
                    sb.append("(warning: ").append(warning).append(")\n");
                }
            } else if (!closed && options.cutSide() != CutSide.ON_LINE) {
                String warning = "run " + runIndex + ": side offsets apply to closed"
                        + " shapes only in this version; open path cut on-line";
                warnings.add(warning);
                sb.append("(warning: ").append(warning).append(")\n");
            }
            for (double depth = firstPassDepth(options);; depth -= options.depthPerPass()) {
                if (depth < options.finalDepth()) {
                    depth = options.finalDepth();
                }
                sb.append(String.format(java.util.Locale.ROOT, "G0 Z%.3f%n", options.safeZ()));
                sb.append(String.format(java.util.Locale.ROOT, "G0 X%.3f Y%.3f%n", mm[0][0], mm[0][1]));
                sb.append(String.format(java.util.Locale.ROOT, "G1 Z%.3f F%.0f%n", depth,
                        options.cutFeed()));
                for (int i = 1; i < mm.length; i++) {
                    sb.append(String.format(java.util.Locale.ROOT, "G1 X%.3f Y%.3f%n",
                            mm[i][0], mm[i][1]));
                }
                if (run.closed()) {
                    sb.append(String.format(java.util.Locale.ROOT, "G1 X%.3f Y%.3f%n",
                            mm[0][0], mm[0][1]));
                }
                if (depth <= options.finalDepth()) {
                    break;
                }
            }
        }
        sb.append("G0 Z").append(String.format(java.util.Locale.ROOT, "%.3f", options.safeZ())).append('\n');
        sb.append("M5\n");
        return new GenerateResult(sb.toString(), warnings);
    }

    private static double firstPassDepth(SvgImportOptions options) {
        return Math.max(options.finalDepth(), -options.depthPerPass());
    }

    /** Transforms one run to output millimeters: scale to width, SVG Y-down
     *  flipped to bed Y-up, free placement offset added. */
    private static double[][] toMm(SvgRun run, SvgDocument doc, SvgImportOptions options) {
        var bounds = contentBounds(doc);
        double scale = scaleFor(doc, options);
        double[][] mm = new double[run.points().size()][2];
        for (int i = 0; i < run.points().size(); i++) {
            double ux = run.points().get(i)[0];
            double uy = run.points().get(i)[1];
            mm[i][0] = options.placeX() + (ux - bounds[0]) * scale;
            mm[i][1] = options.placeY() + (bounds[3] - uy) * scale; // SVG Y-down flip
        }
        return mm;
    }

    /** Scale from user units to mm for the requested target width. */
    public static double scaleFor(SvgDocument doc, SvgImportOptions options) {
        double[] bounds = contentBounds(doc);
        double width = bounds[2] - bounds[0];
        if (width <= 1e-9) {
            return 1.0;
        }
        return options.widthMm() / width;
    }

    /** Generated content height in mm at the target scale (wizard label). */
    public static double heightMm(SvgDocument doc, SvgImportOptions options) {
        double[] bounds = contentBounds(doc);
        return (bounds[3] - bounds[1]) * scaleFor(doc, options);
    }

    private static double[] contentBounds(SvgDocument doc) {
        double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY;
        for (SvgRun run : doc.runs()) {
            for (double[] p : run.points()) {
                minX = Math.min(minX, p[0]);
                minY = Math.min(minY, p[1]);
                maxX = Math.max(maxX, p[0]);
                maxY = Math.max(maxY, p[1]);
            }
        }
        if (!Double.isFinite(minX)) {
            return new double[] {0, 0, 0, 0};
        }
        return new double[] {minX, minY, maxX, maxY};
    }
}
