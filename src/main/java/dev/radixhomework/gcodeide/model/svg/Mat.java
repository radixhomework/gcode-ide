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
     *  transforms outside the supported subset (translate/scale/rotate/matrix).
     *  Scanned by hand (not regex) so no input can trigger backtracking blowups. */
    public static Mat parse(String transform) {
        Mat result = IDENTITY;
        String s = transform == null ? "" : transform;
        int i = 0;
        while (i < s.length()) {
            int nameEnd = nameEnd(s, i);
            String op = nameEnd > i ? s.substring(i, nameEnd) : "";
            int open = skipSpaces(s, nameEnd);
            int close = open < s.length() && s.charAt(open) == '('
                    ? s.indexOf(')', open) : -1;
            if (!op.isEmpty() && close > open) {
                result = result.multiply(parseOp(op, args(s, open, close)));
                i = close + 1;
            } else {
                i = nameEnd > i ? nameEnd : i + 1;
            }
        }
        return result;
    }

    /** End of a transform name starting at {@code from} (equal when none). */
    private static int nameEnd(String s, int from) {
        int i = from;
        while (i < s.length()
                && (Character.isLetterOrDigit(s.charAt(i)) || s.charAt(i) == '_')) {
            i++;
        }
        return i;
    }

    private static int skipSpaces(String s, int from) {
        int i = from;
        while (i < s.length() && Character.isWhitespace(s.charAt(i))) {
            i++;
        }
        return i;
    }

    private static double[] args(String s, int open, int close) {
        return java.util.Arrays.stream(s.substring(open + 1, close).trim().split("[\\s,]+"))
                .mapToDouble(t -> t.isBlank() ? 0 : Double.parseDouble(t))
                .toArray();
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
                double cos = Math.cos(rad);
                double sin = Math.sin(rad);
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
