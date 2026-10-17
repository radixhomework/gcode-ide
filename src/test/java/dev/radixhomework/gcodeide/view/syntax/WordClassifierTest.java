package dev.radixhomework.gcodeide.view.syntax;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class WordClassifierTest {

    @Test
    void classifyRepresentativeLine() {
        List<WordClassifier.Span> spans = WordClassifier.classifyLine("N10 G1 X10 Y20 F600 ; first pass");
        assertTrue(spans.contains(new WordClassifier.Span(0, 3, WordClassifier.LINE_NUMBER)));
        assertTrue(spans.contains(new WordClassifier.Span(4, 6, WordClassifier.G_WORD)));
        assertTrue(spans.contains(new WordClassifier.Span(7, 10, WordClassifier.AXIS_PARAM)));
        assertTrue(spans.contains(new WordClassifier.Span(11, 14, WordClassifier.AXIS_PARAM)));
        assertTrue(spans.contains(new WordClassifier.Span(15, 19, WordClassifier.AXIS_PARAM)));
        assertTrue(spans.contains(new WordClassifier.Span(20, 32, WordClassifier.COMMENT)));
    }

    @Test
    void classifyUnknownAndUnusedWordsFallBack() {
        assertEquals(
                List.of(new WordClassifier.Span(0, 3, WordClassifier.FALLBACK),
                        new WordClassifier.Span(4, 6, WordClassifier.FALLBACK),
                        new WordClassifier.Span(7, 9, WordClassifier.FALLBACK)),
                WordClassifier.classifyLine("G43 H1 M8"));
    }

    @Test
    void classifyParenthesizedComment() {
        List<WordClassifier.Span> spans = WordClassifier.classifyLine("(setup) G1 X5");
        assertTrue(spans.contains(new WordClassifier.Span(0, 7, WordClassifier.COMMENT)));
        assertTrue(spans.contains(new WordClassifier.Span(8, 10, WordClassifier.G_WORD)));
        assertTrue(spans.contains(new WordClassifier.Span(11, 13, WordClassifier.AXIS_PARAM)));
    }

    @Test
    void classifySupportedMCodes() {
        List<WordClassifier.Span> spans = WordClassifier.classifyLine("M3 S10000 M5");
        String line = "M3 S10000 M5";
        var byText = new java.util.HashMap<String, String>();
        for (WordClassifier.Span span : spans) {
            byText.put(line.substring(span.start(), span.end()), span.category());
        }
        assertEquals(WordClassifier.M_WORD, byText.get("M3"));
        assertEquals(WordClassifier.M_WORD, byText.get("M5"));
        assertEquals(WordClassifier.AXIS_PARAM, byText.get("S10000"));
    }
}
