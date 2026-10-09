package dev.radixhomework.gcodeide.model.svg;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class SvgDocumentTest {

    private static String fixture() throws IOException {
        try (InputStream in = SvgDocumentTest.class.getResourceAsStream("/svg/fixture.svg")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void loadsSupportedGeometryAndWarnsOnUnsupported() throws IOException {
        SvgDocument doc = SvgDocument.load(fixture());
        assertEquals(3, doc.runs().size(), "rect + circle + open curve");
        assertEquals(2, doc.warnings().size(), "text + image warnings");
        assertTrue(doc.warnings().stream().anyMatch(w -> w.contains("text")));
        assertTrue(doc.warnings().stream().anyMatch(w -> w.contains("image")));
        assertEquals(210.0, doc.width());
        assertEquals(297.0, doc.height());
    }

    @Test
    void transformedRectIsPlacedByItsTransform() throws IOException {
        SvgDocument doc = SvgDocument.load(fixture());
        // rect local (0,0)-(10,10) under translate(10,5) scale(2)
        // -> (10,5)-(30,25)
        var run = doc.runs().get(0);
        double minX = run.points().stream().mapToDouble(p -> p[0]).min().orElseThrow();
        double minY = run.points().stream().mapToDouble(p -> p[1]).min().orElseThrow();
        double maxX = run.points().stream().mapToDouble(p -> p[0]).max().orElseThrow();
        double maxY = run.points().stream().mapToDouble(p -> p[1]).max().orElseThrow();
        assertEquals(10.0, minX, 1e-9);
        assertEquals(5.0, minY, 1e-9);
        assertEquals(30.0, maxX, 1e-9);
        assertEquals(25.0, maxY, 1e-9);
        assertTrue(run.closed());
    }

    @Test
    void fillOnlyShapesAreSkipped() {
        SvgDocument doc = SvgDocument.load(
                "<svg width=\"10\" height=\"10\"><rect x=\"0\" y=\"0\" width=\"5\" height=\"5\" fill=\"red\"/></svg>");
        assertTrue(doc.runs().isEmpty());
        assertTrue(doc.warnings().isEmpty());
    }

    @Test
    void unparseableSvgThrows() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> SvgDocument.load("<svg><unclosed>"));
    }
}
