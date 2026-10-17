package dev.radixhomework.gcodeide.model.svg;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class PathOffsetterTest {

    private static List<double[]> square(double x, double y, double w, double h) {
        return List.of(new double[] {x, y}, new double[] {x + w, y},
                new double[] {x + w, y + h}, new double[] {x, y + h});
    }

    private static List<double[]> toList(List<double[]> ring) {
        return new ArrayList<>(ring);
    }

    private static double[] extents(List<double[]> ring) {
        double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY;
        for (var p : ring) {
            minX = Math.min(minX, p[0]);
            minY = Math.min(minY, p[1]);
            maxX = Math.max(maxX, p[0]);
            maxY = Math.max(maxY, p[1]);
        }
        return new double[] {minX, minY, maxX, maxY};
    }

    @Test
    void outwardOffsetGrowsSquareByRadius() {
        var offset = PathOffsetter.offsetClosed(toList(square(0, 0, 40, 40)), 1.5875,
                true, 0.01);
        var e = extents(offset);
        assertEquals(-1.5875, e[0], 0.01);
        assertEquals(-1.5875, e[1], 0.01);
        assertEquals(41.5875, e[2], 0.01);
        assertEquals(41.5875, e[3], 0.01);
        assertTrue(PathOffsetter.isSimple(offset));
    }

    @Test
    void inwardOffsetShrinksSquareByRadius() {
        var offset = PathOffsetter.offsetClosed(toList(square(0, 0, 40, 40)), 1.5875,
                false, 0.01);
        var e = extents(offset);
        assertEquals(1.5875, e[0], 0.01);
        assertEquals(1.5875, e[1], 0.01);
        assertEquals(38.4125, e[2], 0.01);
        assertEquals(38.4125, e[3], 0.01);
        assertTrue(PathOffsetter.isSimple(offset));
    }

    @Test
    void offsetRingIsSimpleForConvexInput() {
        var offset = PathOffsetter.offsetClosed(toList(square(0, 0, 40, 40)), 5, true, 0.01);
        assertTrue(PathOffsetter.isSimple(offset));
    }

    @Test
    void roundJoinsEmitArcPointsAtCorners() {
        var offset = PathOffsetter.offsetClosed(toList(square(0, 0, 40, 40)), 5, true, 0.01);
        // the corner regions must contain intermediate arc points, not just vertices
        int nearCorner = 0;
        for (var p : offset) {
            double dx = p[0] - 40, dy = p[1] - 40;
            if (dx > 0.5 && dy > 0.5 && Math.abs(Math.hypot(dx, dy) - 5) < 1.0) {
                nearCorner++;
            }
        }
        assertTrue(nearCorner >= 2, "round join arc points present at the corner");
    }

    @Test
    void concavePolygonInwardOffsetStaysSimple() {
        // L-shape (concave) inward offset
        var l = List.of(new double[] {0, 0}, new double[] {30, 0}, new double[] {30, 10},
                new double[] {10, 10}, new double[] {10, 30}, new double[] {0, 30});
        var offset = PathOffsetter.offsetClosed(toList(l), 2, false, 0.01);
        assertTrue(PathOffsetter.isSimple(offset));
    }

    @Test
    void inwardOffsetNarrowerThanCutterIsRejected() {
        // 0.5-wide slot, inward by 1.5875: the offset collapses through
        // itself into an inside-out ring that is still simple but encloses
        // more area than the original - offsetIsValid must reject it
        var slot = toList(square(0, 0, 0.5, 10));
        var offset = PathOffsetter.offsetClosed(slot, 1.5875, false, 0.01);
        assertTrue(PathOffsetter.isSimple(offset), "collapsed ring stays simple");
        assertTrue(!PathOffsetter.offsetIsValid(slot, offset, false),
                "collapsed inward offset is not valid");
        var bigSquare = toList(square(0, 0, 40, 40));
        assertTrue(PathOffsetter.offsetIsValid(bigSquare,
                PathOffsetter.offsetClosed(bigSquare, 1.5875, false, 0.01), false),
                "healthy inward offset is valid");
    }
}
