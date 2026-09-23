package dev.radixhomework.gcodeide.view.syntax;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class GCodeWordsTest {

    @Test
    void prefixMatchingIsCaseInsensitiveAndOrdered() {
        List<String> g = GCodeWords.suggestionsFor("G").stream()
                .map(GCodeWords.Entry::code).toList();
        assertEquals(List.of("G0", "G1", "G2", "G3", "G17", "G20", "G21", "G90", "G91"), g);
        assertEquals(List.of("G1", "G17"), GCodeWords.suggestionsFor("g1").stream()
                .map(GCodeWords.Entry::code).toList());
        assertEquals(List.of("M3", "M4", "M5"), GCodeWords.suggestionsFor("M").stream()
                .map(GCodeWords.Entry::code).toList());
    }

    @Test
    void emptyPrefixOffersEverything() {
        assertEquals(GCodeWords.entries().size(), GCodeWords.suggestionsFor("").size());
    }

    @Test
    void everyDocumentedCodeIsSupportedByTheParser() {
        Set<String> supported = Set.of(
                "G0", "G1", "G2", "G3", "G17", "G20", "G21", "G90", "G91",
                "M3", "M4", "M5", "X", "Y", "Z", "I", "J", "R", "F", "S");
        for (GCodeWords.Entry entry : GCodeWords.entries()) {
            assertTrue(supported.contains(entry.code()),
                    entry.code() + " is not in the supported set");
        }
    }

    @Test
    void descriptionsArePresentAndRendered() {
        for (GCodeWords.Entry entry : GCodeWords.entries()) {
            assertTrue(entry.description() != null && !entry.description().isBlank(),
                    entry.code() + " has a description");
            assertTrue(entry.display().contains(" — "));
        }
        assertEquals("G1", GCodeWords.codeOf("G1 — linear feed move"));
    }
}
