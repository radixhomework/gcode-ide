package dev.radixhomework.gcodeide.model.svg;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class SvgFlattenerTest {

    private static final double TOL = 0.01;

    @Test
    void lineProducesTwoPointRun() {
        var runs = SvgFlattener.flatten(SvgPathData.parse("M 0 0 L 30 0"));
        assertEquals(1, runs.size());
        assertEquals(2, runs.get(0).points().size());
        assertTrue(!runs.get(0).closed());
    }

    @Test
    void closedShapeEndsAtItsStartPoint() {
        var runs = SvgFlattener.flatten(SvgPathData.parse("M 0 0 L 10 0 L 10 10 Z"));
        assertEquals(1, runs.size());
        assertTrue(runs.get(0).closed());
        var pts = runs.get(0).points();
        assertEquals(pts.get(0)[0], pts.get(pts.size() - 1)[0], 1e-9);
        assertEquals(pts.get(0)[1], pts.get(pts.size() - 1)[1], 1e-9);
    }

    private static double maxDeviationFromCubic(List<double[]> pts,
            double c1x, double c1y, double c2x, double c2y) {
        // dense sampling of the true cubic
        double max = 0;
        int startIdx = 1; // skip the start point (exact)
        for (int i = startIdx; i < pts.size(); i++) {
            double t = (double) (i - startIdx) / (pts.size() - 1 - startIdx);
            double bx = bezier1(t, pts.get(i - 1)[0], c1x, c2x, pts.get(i)[0]);
            double by = bezier1(t, pts.get(i - 1)[1], c1y, c2y, pts.get(i)[1]);
            max = Math.max(max, distancePointToPolyline(bx, by, pts));
        }
        return max;
    }

    private static double bezier1(double t, double p0, double p1, double p2, double p3) {
        double u = 1 - t;
        return u * u * u * p0 + 3 * u * u * t * p1 + 3 * u * t * t * p2 + t * t * t * p3;
    }

    private static double distancePointToPolyline(double px, double py,
            List<double[]> pts) {
        double best = Double.POSITIVE_INFINITY;
        for (int i = 1; i < pts.size(); i++) {
            best = Math.min(best, distancePointToSegment(px, py,
                    pts.get(i - 1), pts.get(i)));
        }
        return best;
    }

    private static double distancePointToSegment(double px, double py, double[] a, double[] b) {
        double dx = b[0] - a[0], dy = b[1] - a[1];
        double len2 = dx * dx + dy * dy;
        if (len2 < 1e-18) {
            return Math.hypot(px - a[0], py - a[1]);
        }
        double t = Math.max(0, Math.min(1, ((px - a[0]) * dx + (py - a[1]) * dy) / len2));
        return Math.hypot(px - (a[0] + t * dx), py - (a[1] + t * dy));
    }

    @Test
    void cubicFlatteningWithinTolerance() {
        var runs = SvgFlattener.flatten(SvgPathData.parse("M 0 0 C 40 100 80 -100 120 0"));
        var pts = runs.get(0).points();
        // dense true-curve sampling: deviation of the polyline from the curve
        double max = 0;
        for (int i = 1; i <= 400; i++) {
            double t = i / 400.0;
            double u = 1 - t;
            double bx = 3 * u * u * t * 40 + 3 * u * t * t * 80 + t * t * t * 120;
            double by = 3 * u * u * t * 100 + 3 * u * t * t * (-100);
            max = Math.max(max, distancePointToPolyline(bx, by, pts));
        }
        assertTrue(max <= SvgFlattener.TOLERANCE + 1e-9, "deviation " + max);
    }

    @Test
    void arcFlatteningWithinTolerance() {
        // (0,0) -> (50,50) at r=50: the chord is a diameter, center (25,25)
        var runs = SvgFlattener.flatten(SvgPathData.parse("M 0 0 A 50 50 0 0 1 50 50"));
        var pts = runs.get(0).points();
        assertEquals(50.0, pts.get(pts.size() - 1)[0], 0.01);
        assertEquals(50.0, pts.get(pts.size() - 1)[1], 0.01);
        // every flattened segment midpoint must lie on the true circle
        // (empirically verified center (50,0) for fA=0, fS=1 on these endpoints)
        double max = 0;
        for (int i = 1; i < pts.size(); i++) {
            double mx = (pts.get(i - 1)[0] + pts.get(i)[0]) / 2;
            double my = (pts.get(i - 1)[1] + pts.get(i)[1]) / 2;
            max = Math.max(max, Math.abs(Math.hypot(mx - 50, my - 0) - 50));
        }
        assertTrue(max <= SvgFlattener.TOLERANCE + 1e-9, "sagitta " + max);
    }

    @Test
    void arcLargeSweepGoesTheLongWay() {
        var short_ = SvgFlattener.flatten(SvgPathData.parse("M 0 0 A 50 50 0 0 1 50 50"));
        var long_ = SvgFlattener.flatten(SvgPathData.parse("M 0 0 A 50 50 0 1 1 50 50"));
        double lenShort = 0, lenLong = 0;
        for (int i = 1; i < short_.get(0).points().size(); i++) {
            lenShort += dist(short_.get(0).points().get(i - 1), short_.get(0).points().get(i));
        }
        for (int i = 1; i < long_.get(0).points().size(); i++) {
            lenLong += dist(long_.get(0).points().get(i - 1), long_.get(0).points().get(i));
        }
        assertTrue(lenLong > lenShort * 2, "large-arc sweeps the long way: "
                + lenShort + " vs " + lenLong);
    }

    private static double dist(double[] a, double[] b) {
        return Math.hypot(b[0] - a[0], b[1] - a[1]);
    }
}
