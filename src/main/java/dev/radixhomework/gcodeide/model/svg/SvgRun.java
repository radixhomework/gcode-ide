package dev.radixhomework.gcodeide.model.svg;

import java.util.List;

/**
 * A stroked geometry run in user units: polyline points plus a closed flag.
 * Pure model — no JavaFX.
 */
public record SvgRun(List<double[]> points, boolean closed) {

    public SvgRun {
        points = List.copyOf(points);
    }
}
