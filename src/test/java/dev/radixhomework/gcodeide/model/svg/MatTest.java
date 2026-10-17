package dev.radixhomework.gcodeide.model.svg;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class MatTest {

    @Test
    void singleArgumentScaleIsUniform() {
        assertArrayEquals(new double[] {6, 8}, Mat.parse("scale(2)").apply(3, 4));
    }

    @Test
    void transformsComposeInAttributeOrder() {
        // SVG applies the rightmost transform to the point first:
        // scale, then translate
        assertArrayEquals(new double[] {10, 5},
                Mat.parse("translate(10 5) scale(2)").apply(0, 0));
    }

    @Test
    void threeArgumentRotateKeepsThePivotFixed() {
        assertArrayEquals(new double[] {10, 0},
                Mat.parse("rotate(90 10 0)").apply(10, 0), 1e-9);
        // a point 10 units right of the pivot ends up 10 units below it
        // (SVG y-down: positive angles rotate clockwise on screen)
        assertArrayEquals(new double[] {10, 10},
                Mat.parse("rotate(90 10 0)").apply(20, 0), 1e-9);
    }

    @Test
    void unsupportedTransformThrows() {
        assertThrows(UnsupportedOperationException.class, () -> Mat.parse("skewX(30)"));
    }
}
