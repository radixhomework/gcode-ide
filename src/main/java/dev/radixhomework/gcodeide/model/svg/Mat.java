package dev.radixhomework.gcodeide.model.svg;

/** 2D affine transform (a b e / b d f) for SVG user units. Pure model. */
public record Mat(double a, double b, double c, double d, double e, double f) {

    public static final Mat IDENTITY = new Mat(1, 0, 0, 1, 0, 0);

    public Mat multiply(Mat o) {
        // this (applied first) then o
        return new Mat(
                o.a * a + o.c * b,
                o.b * a + o.d * b,
                o.a * c + o.c * d,
                o.b * c + o.d * d,
                a * o.e + c * o.f + e,
                b * o.e + d * o.f + f);
    }

    public double[] apply(double x, double y) {
        return new double[] {a * x + c * y + e, b * x + d * y + f};
    }

    /** Parses a transform attribute; throws UnsupportedOperationException for
     *  transforms outside the supported subset (translate/scale/rotate/matrix). */
    public static Mat parse(String transform) {
        Mat result = IDENTITY;
        var m = java.util.regex.Pattern
                .compile("(\\w+)\\s*\\(([^)]*)\\)")
                .matcher(transform == null ? "" : transform);
        while (m.find()) {
            String op = m.group(1);
            double[] v = java.util.Arrays.stream(m.group(2).trim().split("[\\s,]+"))
                    .mapToDouble(s -> s.isBlank() ? 0 : Double.parseDouble(s))
                    .toArray();
            result = result.multiply(parseOp(op, v));
        }
        return result;
    }

    private static Mat parseOp(String op, double[] v) {
        switch (op) {
            case "translate":
                return new Mat(1, 0, 0, 1, v[0], v.length > 1 ? v[1] : 0);
            case "scale":
                double sx = v[0];
                double sy = v.length > 1 ? v[1] : sx;
                return new Mat(sx, 0, 0, sy, 0, 0);
            case "rotate":
                double rad = Math.toRadians(v[0]);
                double cos = Math.cos(rad), sin = Math.sin(rad);
                Mat rotation = new Mat(cos, sin, -sin, cos, 0, 0);
                if (v.length >= 3) {
                    rotation = rotation.multiply(
                            new Mat(1, 0, 0, 1, v[1], v[2]))
                            .multiply(new Mat(cos, sin, -sin, cos, 0, 0))
                            .multiply(new Mat(1, 0, 0, 1, -v[1], -v[2]));
                }
                return rotation;
            case "matrix":
                return new Mat(v[0], v[1], v[2], v[3], v[4], v[5]);
            default:
                throw new UnsupportedOperationException("transform '" + op + "'");
        }
    }
}
