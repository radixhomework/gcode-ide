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

    private SvgFlattener() {
    }

    public static List<Run> flatten(List<SvgPathData.Cmd> cmds) {
        return flatten(cmds, TOLERANCE);
    }

    public static List<Run> flatten(List<SvgPathData.Cmd> cmds, double tolerance) {
        List<Run> runs = new ArrayList<>();
        List<double[]> pts = new ArrayList<>();
        boolean closed = false;
        double cx = 0, cy = 0, sx = 0, sy = 0;
        double lastC2x = 0, lastC2y = 0, lastQx = 0, lastQy = 0;
        boolean lastWasCubic = false, lastWasQuad = false;

        for (SvgPathData.Cmd cmd : cmds) {
            char letter = Character.toUpperCase(cmd.letter());
            boolean rel = cmd.relative();
            double[] a = cmd.args();
            switch (letter) {
                case 'M' -> {
                    finishRun(runs, pts, closed);
                    closed = false;
                    cx = rel ? cx + a[0] : a[0];
                    cy = rel ? cy + a[1] : a[1];
                    sx = cx;
                    sy = cy;
                    pts.add(new double[] {cx, cy});
                }
                case 'L' -> {
                    cx = rel ? cx + a[0] : a[0];
                    cy = rel ? cy + a[1] : a[1];
                    pts.add(new double[] {cx, cy});
                }
                case 'H' -> {
                    cx = rel ? cx + a[0] : a[0];
                    pts.add(new double[] {cx, cy});
                }
                case 'V' -> {
                    cy = rel ? cy + a[0] : a[0];
                    pts.add(new double[] {cx, cy});
                }
                case 'C' -> {
                    double c1x = rel ? cx + a[0] : a[0];
                    double c1y = rel ? cy + a[1] : a[1];
                    double c2x = rel ? cx + a[2] : a[2];
                    double c2y = rel ? cy + a[3] : a[3];
                    double ex = rel ? cx + a[4] : a[4];
                    double ey = rel ? cy + a[5] : a[5];
                    flattenCubic(pts, cx, cy, c1x, c1y, c2x, c2y, ex, ey, tolerance, 0);
                    lastC2x = c2x;
                    lastC2y = c2y;
                    lastWasCubic = true;
                    lastWasQuad = false;
                    cx = ex;
                    cy = ey;
                }
                case 'S' -> {
                    double c2x = rel ? cx + a[0] : a[0];
                    double c2y = rel ? cy + a[1] : a[1];
                    double ex = rel ? cx + a[2] : a[2];
                    double ey = rel ? cy + a[3] : a[3];
                    double c1x = lastWasCubic ? 2 * cx - lastC2x : cx;
                    double c1y = lastWasCubic ? 2 * cy - lastC2y : cy;
                    flattenCubic(pts, cx, cy, c1x, c1y, c2x, c2y, ex, ey, tolerance, 0);
                    lastC2x = c2x;
                    lastC2y = c2y;
                    lastWasCubic = true;
                    lastWasQuad = false;
                    cx = ex;
                    cy = ey;
                }
                case 'Q' -> {
                    double qx = rel ? cx + a[0] : a[0];
                    double qy = rel ? cy + a[1] : a[1];
                    double ex = rel ? cx + a[2] : a[2];
                    double ey = rel ? cy + a[3] : a[3];
                    flattenQuad(pts, cx, cy, qx, qy, ex, ey, tolerance, 0);
                    lastQx = qx;
                    lastQy = qy;
                    lastWasQuad = true;
                    lastWasCubic = false;
                    cx = ex;
                    cy = ey;
                }
                case 'T' -> {
                    double qx = lastWasQuad ? 2 * cx - lastQx : cx;
                    double qy = lastWasQuad ? 2 * cy - lastQy : cy;
                    double ex = rel ? cx + a[0] : a[0];
                    double ey = rel ? cy + a[1] : a[1];
                    flattenQuad(pts, cx, cy, qx, qy, ex, ey, tolerance, 0);
                    lastQx = qx;
                    lastQy = qy;
                    lastWasQuad = true;
                    lastWasCubic = false;
                    cx = ex;
                    cy = ey;
                }
                case 'A' -> {
                    double rx = a[0], ry = a[1], phi = Math.toRadians(a[2]);
                    boolean largeArc = a[3] != 0, sweep = a[4] != 0;
                    double ex = rel ? cx + a[5] : a[5];
                    double ey = rel ? cy + a[6] : a[6];
                    flattenArc(pts, cx, cy, rx, ry, phi, largeArc, sweep, ex, ey, tolerance);
                    lastWasCubic = false;
                    lastWasQuad = false;
                    cx = ex;
                    cy = ey;
                }
                case 'Z' -> {
                    if (!pts.isEmpty()) {
                        pts.add(new double[] {sx, sy});
                        closed = true;
                        finishRun(runs, pts, closed);
                        closed = false;
                        cx = sx;
                        cy = sy;
                    }
                }
                default -> throw new IllegalArgumentException("bad command: " + letter);
            }
        }
        finishRun(runs, pts, closed);
        return runs;
    }

    private static void finishRun(List<Run> runs, List<double[]> pts, boolean closed) {
        if (pts.size() >= 2) {
            runs.add(new Run(new ArrayList<>(pts), closed));
        }
        pts.clear();
    }

    private static void flattenCubic(List<double[]> out, double x0, double y0,
            double c1x, double c1y, double c2x, double c2y, double x1, double y1,
            double tol, int depth) {
        if (flatEnough(x0, y0, c1x, c1y, x1, y1, c2x, c2y, tol) || depth >= 18) {
            out.add(new double[] {x1, y1});
            return;
        }
        // De Casteljau split at t = 0.5
        double a1x = (x0 + c1x) / 2, a1y = (y0 + c1y) / 2;   // P01
        double a2x = (c1x + c2x) / 2, a2y = (c1y + c2y) / 2; // P12
        double b2x = (c2x + x1) / 2, b2y = (c2y + y1) / 2;   // P23
        double p012x = (a1x + a2x) / 2, p012y = (a1y + a2y) / 2;
        double p123x = (a2x + b2x) / 2, p123y = (a2y + b2y) / 2;
        double mx = (p012x + p123x) / 2, my = (p012y + p123y) / 2; // on-curve M
        flattenCubic(out, x0, y0, a1x, a1y, p012x, p012y, mx, my, tol, depth + 1);
        flattenCubic(out, mx, my, p123x, p123y, b2x, b2y, x1, y1, tol, depth + 1);
    }

    private static void flattenQuad(List<double[]> out, double x0, double y0,
            double qx, double qy, double x1, double y1, double tol, int depth) {
        if (flatEnough(x0, y0, qx, qy, x1, y1, qx, qy, tol) || depth >= 18) {
            out.add(new double[] {x1, y1});
            return;
        }
        double c1x = (x0 + qx) / 2, c1y = (y0 + qy) / 2;
        double c2x = (qx + x1) / 2, c2y = (qy + y1) / 2;
        double mx = (c1x + c2x) / 2, my = (c1y + c2y) / 2;
        flattenQuad(out, x0, y0, c1x, c1y, mx, my, tol, depth + 1);
        flattenQuad(out, mx, my, c2x, c2y, x1, y1, tol, depth + 1);
    }

    /** Max perpendicular distance of the control points to the chord. */
    private static boolean flatEnough(double x0, double y0, double c1x, double c1y,
            double x1, double y1, double c2x, double c2y, double tol) {
        double dx = x1 - x0, dy = y1 - y0;
        double len = Math.hypot(dx, dy);
        if (len < 1e-12) {
            return Math.hypot(c1x - x0, c1y - y0) <= tol
                    && Math.hypot(c2x - x0, c2y - y0) <= tol;
        }
        double d1 = Math.abs((c1x - x0) * dy - (c1y - y0) * dx) / len;
        double d2 = Math.abs((c2x - x0) * dy - (c2y - y0) * dx) / len;
        return Math.max(d1, d2) <= tol;
    }

    /** W3C SVG 1.1 F.6.5: endpoint to center parametrization. */
    private static double[] arcCenter(double x1, double y1, double rx, double ry,
            double phi, boolean largeArc, boolean sweep, double x2, double y2) {
        rx = Math.abs(rx);
        ry = Math.abs(ry);
        if (rx < 1e-12 || ry < 1e-12) {
            return null;
        }
        double cosPhi = Math.cos(phi), sinPhi = Math.sin(phi);
        double dx2 = (x1 - x2) / 2, dy2 = (y1 - y2) / 2;
        double x1p = cosPhi * dx2 + sinPhi * dy2;
        double y1p = -sinPhi * dx2 + cosPhi * dy2;
        double lambda = x1p * x1p / (rx * rx) + y1p * y1p / (ry * ry);
        if (lambda > 1) {
            double s = Math.sqrt(lambda);
            rx *= s;
            ry *= s;
        }
        // empirical: fA == fS selects the center on the far side of the chord
        double sign = largeArc == sweep ? 1 : -1;
        double num = rx * rx * ry * ry - rx * rx * y1p * y1p - ry * ry * x1p * x1p;
        double den = rx * rx * y1p * y1p + ry * ry * x1p * x1p;
        double co = Math.sqrt(Math.max(0, num / den)) * sign;
        double cxp = co * rx * y1p / ry;
        double cyp = -co * ry * x1p / rx;
        double cx = cosPhi * cxp - sinPhi * cyp + (x1 + x2) / 2;
        double cy = sinPhi * cxp + cosPhi * cyp + (y1 + y2) / 2;
        return new double[] {cx, cy};
    }

    private static double arcAngle(double ux, double uy, double vx, double vy) {
        double dot = ux * vx + uy * vy;
        double len = Math.hypot(ux, uy) * Math.hypot(vx, vy);
        double a = Math.acos(Math.max(-1, Math.min(1, dot / len)));
        if (ux * vy - uy * vx < 0) {
            a = -a;
        }
        return a;
    }

    private static void flattenArc(List<double[]> out, double x1, double y1,
            double rx, double ry, double phi, boolean largeArc, boolean sweep,
            double x2, double y2, double tol) {
        double cosPhi = Math.cos(phi), sinPhi = Math.sin(phi);
        double[] center = arcCenter(x1, y1, rx, ry, phi, largeArc, sweep, x2, y2);
        if (center == null) { // degenerate radii: straight line
            out.add(new double[] {x2, y2});
            return;
        }
        double theta1 = Math.atan2((y1 - center[1]) / ry, (x1 - center[0]) / rx);
        double theta2 = Math.atan2((y2 - center[1]) / ry, (x2 - center[0]) / rx);
        double delta = theta2 - theta1;
        if (!sweep && delta > 0) {
            delta -= 2 * Math.PI;
        } else if (sweep && delta < 0) {
            delta += 2 * Math.PI;
        }
        if (largeArc && Math.abs(delta) < Math.PI) {
            delta += delta > 0 ? 2 * Math.PI : -2 * Math.PI;
        } else if (!largeArc && Math.abs(delta) > Math.PI) {
            delta -= delta > 0 ? 2 * Math.PI : -2 * Math.PI;
        }
        double rmax = Math.max(rx, ry);
        double step = rmax > tol ? 2 * Math.acos(Math.max(-1, 1 - tol / rmax)) : Math.PI / 4;
        int n = Math.max(2, (int) Math.ceil(Math.abs(delta) / Math.max(1e-6, step)));
        for (int i = 1; i <= n; i++) {
            double theta = theta1 + delta * i / n;
            double px = center[0] + rx * Math.cos(theta) * cosPhi
                    - ry * Math.sin(theta) * sinPhi;
            double py = center[1] + rx * Math.cos(theta) * sinPhi
                    + ry * Math.sin(theta) * cosPhi;
            out.add(new double[] {px, py});
        }
    }
}
