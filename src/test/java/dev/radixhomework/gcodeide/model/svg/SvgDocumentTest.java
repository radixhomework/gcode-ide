package dev.radixhomework.gcodeide.model.svg;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
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
    void fillOnlyShapeImportsAsOutline() {
        // paint (fill/stroke/none) does not matter: the outline is the toolpath
        SvgDocument doc = SvgDocument.load(
                "<svg width=\"10\" height=\"10\"><rect x=\"0\" y=\"0\" width=\"5\" height=\"5\" fill=\"red\"/></svg>");
        assertEquals(1, doc.runs().size());
        assertTrue(doc.runs().get(0).closed());
        assertTrue(doc.warnings().isEmpty());
    }

    @Test
    void noPaintGeometryImports() {
        // common cutter-file style: pure geometry, no fill/stroke anywhere
        SvgDocument doc = SvgDocument.load(
                "<svg width=\"10\" height=\"10\"><rect x=\"0\" y=\"0\" width=\"50\" height=\"50\"/></svg>");
        assertEquals(1, doc.runs().size());
        assertTrue(doc.warnings().isEmpty());
    }

    @Test
    void drawingWithOnlyNonShapesYieldsHintWarning() {
        SvgDocument doc = SvgDocument.load(
                "<svg width=\"10\" height=\"10\"><text x=\"0\" y=\"0\">hi</text></svg>");
        assertTrue(doc.runs().isEmpty());
        assertTrue(doc.warnings().stream()
                .anyMatch(w -> w.contains("no shape geometry")), "hint warning present");
    }

    @Test
    void classStyleStrokeIsApplied() {
        SvgDocument doc = SvgDocument.load("""
                <svg width="100" height="100">
                  <style type="text/css">
                    .st0 { fill:none; stroke:#000000; }
                    path { fill:none; stroke:green; }
                  </style>
                  <path class="st0" d="M 0 0 L 50 50"/>
                  <path d="M 0 0 L 50 50"/>
                </svg>""");
        assertEquals(2, doc.runs().size());
        assertTrue(doc.warnings().isEmpty());
    }

    @Test
    void cdataStyleBlockIsApplied() {
        SvgDocument doc = SvgDocument.load("""
                <svg width="100" height="100">
                  <style><![CDATA[.a{stroke:#000;fill:none}]]></style>
                  <path class="a" d="M 0 0 L 50 50"/>
                </svg>""");
        assertEquals(1, doc.runs().size());
        assertTrue(doc.warnings().isEmpty());
    }

    @Test
    void rootStrokeIsInherited() {
        SvgDocument doc = SvgDocument.load("""
                <svg width="100" height="100" stroke="black">
                  <circle cx="50" cy="50" r="20" fill="none"/>
                </svg>""");
        assertEquals(1, doc.runs().size());
    }

    @Test
    void unparseableSvgThrows() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> SvgDocument.load("<svg><unclosed>"));
    }

    @Test
    void doctypeAndExternalEntitiesAreRejected() {
        // untrusted SVG must never resolve external entities (XXE): with the
        // hardened parser any DOCTYPE refuses the document outright
        String svg = """
                <!DOCTYPE svg [<!ENTITY xxe SYSTEM "file:///etc/hostname">]>
                <svg width="10" height="10"><rect width="5" height="5"/>&xxe;</svg>""";
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> SvgDocument.load(svg));
    }
}
