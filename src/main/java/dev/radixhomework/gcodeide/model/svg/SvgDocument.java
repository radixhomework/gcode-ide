package dev.radixhomework.gcodeide.model.svg;

import java.util.ArrayList;
import java.util.List;

/**
 * Parsed SVG document: stroked geometry as runs in user units, the declared
 * document size, and warnings for unsupported constructs. Pure Java (DOM via
 * the JDK); no JavaFX.
 */
public record SvgDocument(List<SvgRun> runs, List<String> warnings,
        double width, double height) {

    public SvgDocument {
        runs = List.copyOf(runs);
        warnings = List.copyOf(warnings);
    }

    public static SvgDocument load(String svgText) {
        return SvgDocumentLoader.load(svgText);
    }

    public boolean isEmpty() {
        return runs.isEmpty();
    }
}
