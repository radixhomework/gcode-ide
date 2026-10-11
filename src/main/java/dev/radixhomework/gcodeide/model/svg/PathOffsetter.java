package dev.radixhomework.gcodeide.model.svg;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Offsets closed polygons by a radius with round joins (pure Java, no
 * JavaFX). Inward offsets of a ring are computed as outward offsets of the
 * reversed ring, so corner geometry is handled by one code path. Runs whose
 * offset self-intersects (geometry too tight for the cutter) are reported by
 * {@link #isSimple(List)} so callers can fall back to the on-line path.
 */
public final class PathOffsetter {

    private PathOffsetter() {
    }

    /**
     * Offsets a closed ring by {@code radius}: outward (true) grows the ring,
     * inward (false) shrinks it. Returns the offset ring (same start vertex).
     */
    public static List<double[]> offsetClosed(List<double[]> ring, double radius,
            boolean outward, double tolerance) {
        List<double[]> work = new ArrayList<>(ring);
        // flattened closed runs repeat their start point; drop the duplicate
        // so the wrap-around vertex has a real incoming edge
        if (work.size() >= 2 && Math.hypot(
                work.get(work.size() - 1)[0] - work.get(0)[0],
                work.get(work.size() - 1)[1] - work.get(0)[1]) < 1e-9) {
            work.remove(work.size() - 1);
        }
        if (work.size() < 3) {
            return new ArrayList<>(ring);
        }
        if (signedArea(work) < 0) {
            Collections.reverse(work); // normalize to CCW: right-of-travel = outward
        }
        boolean inward = !outward;
        if (inward) {
            Collections.reverse(work); // CW traversal: right-of-travel = inward
        }
        List<double[]> out = offsetRightOfTravel(work, radius, tolerance);
        if (inward) {
            Collections.reverse(out);
        }
        return dedupe(out);
    }

    /**
     * True when an offset ring from {@link #offsetClosed} is usable for
     * toolpaths: simple, and its enclosed area moved in the expected
     * direction (inward shrinks, outward grows). An inward offset of
     * geometry tighter than the cutter collapses through itself into an
     * inside-out ring that can stay simple but encloses more area.
     */
    public static boolean offsetIsValid(List<double[]> original, List<double[]> offset,
            boolean outward) {
        if (original.size() < 3 || offset.size() < 3 || !isSimple(offset)) {
            return false;
        }
        double a0 = Math.abs(signedArea(original));
        double a1 = Math.abs(signedArea(offset));
        return outward ? a1 > a0 : a1 < a0;
    }

    /** True when the closed ring has no non-adjacent segment intersections. */
    public static boolean isSimple(List<double[]> ring) {
        int n = ring.size();
        for (int i = 0; i < n; i++) {
            var a1 = ring.get(i);
            var a2 = ring.get((i + 1) % n);
            for (int j = i + 1; j < n; j++) {
                if ((j + 1) % n == i || (i + 1) % n == j) {
                    continue; // adjacent segments share a vertex
                }
                var b1 = ring.get(j);
                var b2 = ring.get((j + 1) % n);
                if (segmentsIntersect(a1, a2, b1, b2)) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Signed area (positive = counterclockwise in X/Y). */
    public static double signedArea(List<double[]> ring) {
        double sum = 0;
        for (int i = 0; i < ring.size(); i++) {
            var p = ring.get(i);
            var q = ring.get((i + 1) % ring.size());
            sum += p[0] * q[1] - q[0] * p[1];
        }
        return sum / 2;
    }

    /** Offsets a CCW ring to the right of travel by the radius. */
    private static List<double[]> offsetRightOfTravel(List<double[]> ring, double r,
            double tol) {
        int n = ring.size();
        List<double[]> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            double[] prev = ring.get((i - 1 + n) % n);
            double[] cur = ring.get(i);
            double[] next = ring.get((i + 1) % n);

            double[] right1 = rightNormal(prev, cur);
            double[] right2 = rightNormal(cur, next);
            double cross = (cur[0] - prev[0]) * (next[1] - cur[1])
                    - (cur[1] - prev[1]) * (next[0] - cur[0]);

            if (cross > 1e-9) {
                // convex corner (CCW ring): round join, arc from right1 to
                // right2 rotating clockwise (angle decreasing)
                double a1 = Math.atan2(right1[1], right1[0]);
                double a2 = Math.atan2(right2[1], right2[0]);
                double sweep = a2 - a1;
                while (sweep < 0) {
                    sweep += 2 * Math.PI;
                }
                double stepAngle = r > tol
                        ? 2 * Math.acos(Math.max(-1, 1 - tol / r))
                        : Math.PI / 4;
                int steps = Math.max(1, (int) Math.ceil(Math.abs(sweep)
                        / Math.max(1e-6, stepAngle)));
                for (int k = 0; k <= steps; k++) {
                    double ang = a1 + sweep * k / steps;
                    out.add(new double[] {cur[0] + r * Math.cos(ang),
                            cur[1] + r * Math.sin(ang)});
                }
            } else {
                // concave corner: the right-offset edges intersect at the miter
                double[] p = lineIntersect(
                        prev[0] + r * right1[0], prev[1] + r * right1[1],
                        cur[0] + r * right1[0], cur[1] + r * right1[1],
                        cur[0] + r * right2[0], cur[1] + r * right2[1],
                        next[0] + r * right2[0], next[1] + r * right2[1]);
                if (p != null) {
                    out.add(p);
                } else {
                    out.add(new double[] {cur[0] + r * right1[0],
                            cur[1] + r * right1[1]});
                }
            }
        }
        return dedupe(out);
    }

    /** Right-of-travel unit normal of the segment from -> to. */
    private static double[] rightNormal(double[] from, double[] to) {
        double dx = to[0] - from[0], dy = to[1] - from[1];
        double len = Math.hypot(dx, dy);
        if (len < 1e-12) {
            return new double[] {0, 0};
        }
        return new double[] {dy / len, -dx / len};
    }

    private static double[] lineIntersect(double x1, double y1, double x2, double y2,
            double x3, double y3, double x4, double y4) {
        double den = (x1 - x2) * (y3 - y4) - (y1 - y2) * (x3 - x4);
        if (Math.abs(den) < 1e-12) {
            return null;
        }
        double t = ((x1 - x3) * (y3 - y4) - (y1 - y3) * (x3 - x4)) / den;
        return new double[] {x1 + t * (x2 - x1), y1 + t * (y2 - y1)};
    }

    private static boolean segmentsIntersect(double[] p1, double[] p2,
            double[] p3, double[] p4) {
        double d1 = cross(p3, p4, p1);
        double d2 = cross(p3, p4, p2);
        double d3 = cross(p1, p2, p3);
        double d4 = cross(p1, p2, p4);
        return ((d1 > 0 && d2 < 0) || (d1 < 0 && d2 > 0))
                && ((d3 > 0 && d4 < 0) || (d3 < 0 && d4 > 0));
    }

    private static double cross(double[] a, double[] b, double[] p) {
        return (b[0] - a[0]) * (p[1] - a[1]) - (b[1] - a[1]) * (p[0] - a[0]);
    }

    private static List<double[]> dedupe(List<double[]> pts) {
        List<double[]> out = new ArrayList<>();
        for (double[] p : pts) {
            if (out.isEmpty()
                    || Math.hypot(p[0] - out.get(out.size() - 1)[0],
                            p[1] - out.get(out.size() - 1)[1]) > 1e-9) {
                out.add(p);
            }
        }
        if (out.size() >= 2 && Math.hypot(
                out.get(0)[0] - out.get(out.size() - 1)[0],
                out.get(0)[1] - out.get(out.size() - 1)[1]) < 1e-9) {
            out.remove(out.size() - 1);
        }
        return out;
    }
}
