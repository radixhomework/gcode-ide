package dev.radixhomework.gcodeide.model.svg;

import java.util.ArrayList;
import java.util.List;

/**
 * Converts parsed SVG path commands into flattened polylines ("runs") whose
 * deviation from the true curves stays within a tolerance. Pure Java.
 */
public final class SvgFlattener {

    /** A polyline: points in user units; closed runs end at their start point. */
    public record Run(List<double[]> points, boolean closed) {
    }

    /** Max deviation of the flattened polyline from the true curve (user units). */
    public static final double TOLERANCE = 0.01;

    /** 2D point; keeps the curve helpers under the parameter-count limit. */
    private record Pt(double x, double y) {

        double[] xy() {
            return new double[] {x, y};
        }
    }

    /** Endpoint-to-center arc data shared by the arc helpers. */
    private record ArcSpec(double rx, double ry, double phi, boolean largeArc,
            boolean sweep, double tol) {
    }

    /** Running position and reflection state while flattening. */
    private static final class Cursor {
        double cx;
        double cy;
        double sx;
        double sy;
        double lastC2x;
        double lastC2y;
        double lastQx;
        double lastQy;
        boolean lastWasCubic;
        boolean lastWasQuad;
    }

    private SvgFlattener() {
    }

    public static List<Run> flatten(List<SvgPathData.Cmd> cmds) {
        return flatten(cmds, TOLERANCE);
    }

    public static List<Run> flatten(List<SvgPathData.Cmd> cmds, double tolerance) {
        List<Run> runs = new ArrayList<>();
        List<double[]> pts = new ArrayList<>();
        boolean closed = false;
        Cursor cur = new Cursor();
        for (SvgPathData.Cmd cmd : cmds) {
            char letter = Character.toUpperCase(cmd.letter());
            boolean rel = cmd.relative();
            double[] a = cmd.args();
            switch (letter) {
                case 'M' -> {
                    finishRun(runs, pts, closed);
                    closed = false;
                    cur.cx = resolved(cur.cx, rel, a[0]);
                    cur.cy = resolved(cur.cy, rel, a[1]);
                    cur.sx = cur.cx;
                    cur.sy = cur.cy;
                    pts.add(new double[] {cur.cx, cur.cy});
                }
                case 'L' -> lineTo(pts, cur, rel, a);
                case 'H' -> {
                    cur.cx = resolved(cur.cx, rel, a[0]);
                    pts.add(new double[] {cur.cx, cur.cy});
                }
                case 'V' -> {
                    cur.cy = resolved(cur.cy, rel, a[0]);
                    pts.add(new double[] {cur.cx, cur.cy});
                }
                case 'C' -> cubicTo(pts, cur, rel, a, tolerance);
                case 'S' -> smoothCubicTo(pts, cur, rel, a, tolerance);
                case 'Q' -> quadTo(pts, cur, rel, a, tolerance);
                case 'T' -> smoothQuadTo(pts, cur, rel, a, tolerance);
                case 'A' -> arcTo(pts, cur, rel, a, tolerance);
                case 'Z' -> closed = closeSubpath(pts, cur, runs, closed);
                default -> throw new IllegalArgumentException("bad command: " + letter);
            }
        }
        finishRun(runs, pts, closed);
        return runs;
    }

    /** Absolute coordinate for a possibly relative delta. */
    private static double resolved(double base, boolean rel, double delta) {
        return rel ? base + delta : delta;
    }

    private static void lineTo(List<double[]> pts, Cursor cur, boolean rel, double[] a) {
        cur.cx = resolved(cur.cx, rel, a[0]);
        cur.cy = resolved(cur.cy, rel, a[1]);
        pts.add(new double[] {cur.cx, cur.cy});
    }

    private static void cubicTo(List<double[]> pts, Cursor cur, boolean rel,
            double[] a, double tol) {
        Pt end = new Pt(resolved(cur.cx, rel, a[4]), resolved(cur.cy, rel, a[5]));
        flattenCubic(pts, new Pt(cur.cx, cur.cy),
                new Pt(resolved(cur.cx, rel, a[0]), resolved(cur.cy, rel, a[1])),
                new Pt(resolved(cur.cx, rel, a[2]), resolved(cur.cy, rel, a[3])),
                end, tol, 0);
        rememberCubic(cur, end, a[2], a[3], rel);
    }

    /** Stores the second control point for a following S (reflected) command. */
    private static void rememberCubic(Cursor cur, Pt end, double c2x, double c2y,
            boolean rel) {
        cur.lastC2x = resolved(cur.cx, rel, c2x);
        cur.lastC2y = resolved(cur.cy, rel, c2y);
        cur.lastWasCubic = true;
        cur.lastWasQuad = false;
        cur.cx = end.x();
        cur.cy = end.y();
    }

    private static void smoothCubicTo(List<double[]> pts, Cursor cur, boolean rel,
            double[] a, double tol) {
        Pt end = new Pt(resolved(cur.cx, rel, a[2]), resolved(cur.cy, rel, a[3]));
        Pt c2 = new Pt(resolved(cur.cx, rel, a[0]), resolved(cur.cy, rel, a[1]));
        Pt c1 = new Pt(cur.lastWasCubic ? 2 * cur.cx - cur.lastC2x : cur.cx,
                cur.lastWasCubic ? 2 * cur.cy - cur.lastC2y : cur.cy);
        flattenCubic(pts, new Pt(cur.cx, cur.cy), c1, c2, end, tol, 0);
        rememberCubic(cur, end, a[0], a[1], rel);
    }

    private static void quadTo(List<double[]> pts, Cursor cur, boolean rel,
            double[] a, double tol) {
        Pt end = new Pt(resolved(cur.cx, rel, a[2]), resolved(cur.cy, rel, a[3]));
        flattenQuad(pts, new Pt(cur.cx, cur.cy),
                new Pt(resolved(cur.cx, rel, a[0]), resolved(cur.cy, rel, a[1])),
                end, tol, 0);
        rememberQuad(cur, end, a[0], a[1], rel);
    }

    private static void smoothQuadTo(List<double[]> pts, Cursor cur, boolean rel,
            double[] a, double tol) {
        Pt end = new Pt(resolved(cur.cx, rel, a[0]), resolved(cur.cy, rel, a[1]));
        Pt control = new Pt(cur.lastWasQuad ? 2 * cur.cx - cur.lastQx : cur.cx,
                cur.lastWasQuad ? 2 * cur.cy - cur.lastQy : cur.cy);
        flattenQuad(pts, new Pt(cur.cx, cur.cy), control, end, tol, 0);
        rememberQuad(cur, end, control.x(), control.y(), false);
    }

    private static void rememberQuad(Cursor cur, Pt end, double qx, double qy,
            boolean rel) {
        cur.lastQx = resolved(cur.cx, rel, qx);
        cur.lastQy = resolved(cur.cy, rel, qy);
        cur.lastWasQuad = true;
        cur.lastWasCubic = false;
        cur.cx = end.x();
        cur.cy = end.y();
    }

    private static void arcTo(List<double[]> pts, Cursor cur, boolean rel,
            double[] a, double tol) {
        Pt end = new Pt(resolved(cur.cx, rel, a[5]), resolved(cur.cy, rel, a[6]));
        var spec = new ArcSpec(a[0], a[1], Math.toRadians(a[2]), a[3] != 0,
                a[4] != 0, tol);
        flattenArc(pts, new Pt(cur.cx, cur.cy), end, spec);
        cur.lastWasCubic = false;
        cur.lastWasQuad = false;
        cur.cx = end.x();
        cur.cy = end.y();
    }

    /** Closes back to the subpath start; returns the updated closed flag. */
    private static boolean closeSubpath(List<double[]> pts, Cursor cur,
            List<Run> runs, boolean closed) {
        if (pts.isEmpty()) {
            return closed;
        }
        pts.add(new double[] {cur.sx, cur.sy});
        finishRun(runs, pts, true);
        cur.cx = cur.sx;
        cur.cy = cur.sy;
        return false;
    }

    private static void finishRun(List<Run> runs, List<double[]> pts, boolean closed) {
        if (pts.size() >= 2) {
            runs.add(new Run(new ArrayList<>(pts), closed));
        }
        pts.clear();
    }

    private static void flattenCubic(List<double[]> out, Pt p0, Pt c1, Pt c2,
            Pt p1, double tol, int depth) {
        if (flatEnough(p0, c1, p1, c2, tol) || depth >= 18) {
            out.add(p1.xy());
            return;
        }
        // De Casteljau split at t = 0.5
        Pt a1 = new Pt((p0.x() + c1.x()) / 2, (p0.y() + c1.y()) / 2);        // P01
        Pt a2 = new Pt((c1.x() + c2.x()) / 2, (c1.y() + c2.y()) / 2);       // P12
        Pt b2 = new Pt((c2.x() + p1.x()) / 2, (c2.y() + p1.y()) / 2);       // P23
        Pt p012 = new Pt((a1.x() + a2.x()) / 2, (a1.y() + a2.y()) / 2);
        Pt p123 = new Pt((a2.x() + b2.x()) / 2, (a2.y() + b2.y()) / 2);
        Pt m = new Pt((p012.x() + p123.x()) / 2, (p012.y() + p123.y()) / 2); // on-curve M
        flattenCubic(out, p0, a1, p012, m, tol, depth + 1);
        flattenCubic(out, m, p123, b2, p1, tol, depth + 1);
    }

    private static void flattenQuad(List<double[]> out, Pt p0, Pt c, Pt p1,
            double tol, int depth) {
        if (flatEnough(p0, c, p1, c, tol) || depth >= 18) {
            out.add(p1.xy());
            return;
        }
        Pt c1 = new Pt((p0.x() + c.x()) / 2, (p0.y() + c.y()) / 2);
        Pt c2 = new Pt((c.x() + p1.x()) / 2, (c.y() + p1.y()) / 2);
        Pt m = new Pt((c1.x() + c2.x()) / 2, (c1.y() + c2.y()) / 2);
        flattenQuad(out, p0, c1, m, tol, depth + 1);
        flattenQuad(out, m, c2, p1, tol, depth + 1);
    }

    /** Max perpendicular distance of the control points to the chord. */
    private static boolean flatEnough(Pt p0, Pt c1, Pt p1, Pt c2, double tol) {
        double dx = p1.x() - p0.x();
        double dy = p1.y() - p0.y();
        double len = Math.hypot(dx, dy);
        if (len < 1e-12) {
            return Math.hypot(c1.x() - p0.x(), c1.y() - p0.y()) <= tol
                    && Math.hypot(c2.x() - p0.x(), c2.y() - p0.y()) <= tol;
        }
        double d1 = Math.abs((c1.x() - p0.x()) * dy - (c1.y() - p0.y()) * dx) / len;
        double d2 = Math.abs((c2.x() - p0.x()) * dy - (c2.y() - p0.y()) * dx) / len;
        return Math.max(d1, d2) <= tol;
    }

    /** W3C SVG 1.1 F.6.5: endpoint to center parametrization, or null for
     *  degenerate radii (the arc degenerates to a straight line). */
    private static Pt arcCenter(Pt from, Pt to, ArcSpec spec) {
        double rx = Math.abs(spec.rx());
        double ry = Math.abs(spec.ry());
        if (rx < 1e-12 || ry < 1e-12) {
            return null;
        }
        double cosPhi = Math.cos(spec.phi());
        double sinPhi = Math.sin(spec.phi());
        double dx2 = (from.x() - to.x()) / 2;
        double dy2 = (from.y() - to.y()) / 2;
        double x1p = cosPhi * dx2 + sinPhi * dy2;
        double y1p = -sinPhi * dx2 + cosPhi * dy2;
        double lambda = x1p * x1p / (rx * rx) + y1p * y1p / (ry * ry);
        if (lambda > 1) {
            double s = Math.sqrt(lambda);
            rx *= s;
            ry *= s;
        }
        // empirical: fA == fS selects the center on the far side of the chord
        double sign = spec.largeArc() == spec.sweep() ? 1 : -1;
        double num = rx * rx * ry * ry - rx * rx * y1p * y1p - ry * ry * x1p * x1p;
        double den = rx * rx * y1p * y1p + ry * ry * x1p * x1p;
        double co = Math.sqrt(Math.max(0, num / den)) * sign;
        double cxp = co * rx * y1p / ry;
        double cyp = -co * ry * x1p / rx;
        return new Pt(cosPhi * cxp - sinPhi * cyp + (from.x() + to.x()) / 2,
                sinPhi * cxp + cosPhi * cyp + (from.y() + to.y()) / 2);
    }

    private static void flattenArc(List<double[]> out, Pt from, Pt to, ArcSpec spec) {
        double cosPhi = Math.cos(spec.phi());
        double sinPhi = Math.sin(spec.phi());
        Pt center = arcCenter(from, to, spec);
        if (center == null) { // degenerate radii: straight line
            out.add(to.xy());
            return;
        }
        double theta1 = Math.atan2((from.y() - center.y()) / spec.ry(),
                (from.x() - center.x()) / spec.rx());
        double theta2 = Math.atan2((to.y() - center.y()) / spec.ry(),
                (to.x() - center.x()) / spec.rx());
        double delta = theta2 - theta1;
        if (!spec.sweep() && delta > 0) {
            delta -= 2 * Math.PI;
        } else if (spec.sweep() && delta < 0) {
            delta += 2 * Math.PI;
        }
        if (spec.largeArc() && Math.abs(delta) < Math.PI) {
            delta += delta > 0 ? 2 * Math.PI : -2 * Math.PI;
        } else if (!spec.largeArc() && Math.abs(delta) > Math.PI) {
            delta -= delta > 0 ? 2 * Math.PI : -2 * Math.PI;
        }
        double rmax = Math.max(spec.rx(), spec.ry());
        double step = rmax > spec.tol()
                ? 2 * Math.acos(Math.max(-1, 1 - spec.tol() / rmax)) : Math.PI / 4;
        int n = Math.max(2, (int) Math.ceil(Math.abs(delta) / Math.max(1e-6, step)));
        for (int i = 1; i <= n; i++) {
            double theta = theta1 + delta * i / n;
            double px = center.x() + spec.rx() * Math.cos(theta) * cosPhi
                    - spec.ry() * Math.sin(theta) * sinPhi;
            double py = center.y() + spec.rx() * Math.cos(theta) * sinPhi
                    + spec.ry() * Math.sin(theta) * cosPhi;
            out.add(new double[] {px, py});
        }
    }
}
