package dev.radixhomework.gcodeide.model.parsing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class TokenizerTest {

    private static List<GCodeParser.Word> tokenize(String line) {
        return GCodeParser.tokenize(line);
    }

    @Test
    void semicolonCommentStripped() {
        assertEquals(tokenize("G1 X10 ; first pass"), tokenize("G1 X10"));
    }

    @Test
    void parenthesizedCommentsStripped() {
        assertEquals(tokenize("(safe) G1 (inner) X5"), tokenize("G1 X5"));
    }

    @Test
    void stripCommentsKeepsCode() {
        assertEquals("G1 X10", GCodeParser.stripComments("G1 X10 ; cut").strip());
    }

    @Test
    void mixedCaseNormalized() {
        List<GCodeParser.Word> words = tokenize("n10 g1 x10 Y20 f600");
        assertEquals(List.of("N", "G", "X", "Y", "F"), words.stream().map(GCodeParser.Word::letter).toList());
        assertEquals(List.of(10.0, 1.0, 10.0, 20.0, 600.0),
                words.stream().map(GCodeParser.Word::value).toList());
    }

    @Test
    void signedAndDecimalNumbers() {
        List<GCodeParser.Word> words = tokenize("G1 X-5.5 Y+2 Z.5");
        assertEquals(List.of(1.0, -5.5, 2.0, 0.5),
                words.stream().map(GCodeParser.Word::value).toList());
    }

    @Test
    void noSpaceBetweenLetterAndNumber() {
        List<GCodeParser.Word> words = tokenize("G1X10Y20");
        assertEquals(List.of("G", "X", "Y"), words.stream().map(GCodeParser.Word::letter).toList());
    }

    @Test
    void wordWithoutNumberRaises() {
        assertThrows(GCodeParser.TokenizerException.class, () -> tokenize("G1 X"));
    }

    @Test
    void strayCharacterRaises() {
        assertThrows(GCodeParser.TokenizerException.class, () -> tokenize("%"));
    }

    @Test
    void blankAndCommentOnlyLinesTokenizeEmpty() {
        assertTrue(tokenize("").isEmpty());
        assertTrue(tokenize("   ").isEmpty());
        assertTrue(tokenize("; just a comment").isEmpty());
        assertTrue(tokenize("(block)").isEmpty());
    }
}
