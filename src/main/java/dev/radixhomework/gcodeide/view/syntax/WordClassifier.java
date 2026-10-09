package dev.radixhomework.gcodeide.view.syntax;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pure word classification powering syntax coloring (unit-tested without
 * rendered colors). Categories mirror what the parser actually interprets;
 * words the parser recognizes but does not use, and unknown words, fall back.
 */
public final class WordClassifier {

    public static final String COMMENT = "comment";
    public static final String G_WORD = "g-word";
    public static final String M_WORD = "m-word";
    public static final String AXIS_PARAM = "axis-param";
    public static final String LINE_NUMBER = "line-number";
    public static final String FALLBACK = "fallback";

    private static final Set<Integer> SUPPORTED_G_CODES = Set.of(0, 1, 2, 3, 17, 20, 21, 90, 91);
    private static final Set<Integer> SUPPORTED_M_CODES = Set.of(3, 4, 5);
    private static final Set<String> AXIS_PARAM_LETTERS = Set.of(
            "X", "Y", "Z", "I", "J", "K", "R", "F", "S", "T");

    private static final Pattern WORD_RE =
            Pattern.compile("([A-Za-z])\\s*([+-]?(?:\\d+\\.?\\d*|\\.\\d+))");
    private static final Pattern PAREN_COMMENT_RE = Pattern.compile("\\([^)]*\\)");

    private WordClassifier() {
    }

    /** A styled span: half-open [start, end) with a category constant. */
    public record Span(int start, int end, String category) {
    }

    /**
     * Classifies a source line into styled spans for everything that is not
     * plain text: comments, G/M words, axis/parameter words, N line numbers,
     * and the fallback style for recognized-but-unused or unknown words.
     */
    public static List<Span> classifyLine(String text) {
        List<Span> spans = new ArrayList<>();

        int commentStart = text.indexOf(';');
        int codeEnd = commentStart < 0 ? text.length() : commentStart;
        String code = text.substring(0, codeEnd);

        for (Matcher m = PAREN_COMMENT_RE.matcher(code); m.find(); ) {
            spans.add(new Span(m.start(), m.end(), COMMENT));
        }
        // blank out comments with same-width spaces so later word offsets stay true
        String commented = PAREN_COMMENT_RE.matcher(code)
                .replaceAll(m -> " ".repeat(m.group().length()));

        Matcher matcher = WORD_RE.matcher(commented);
        while (matcher.find()) {
            String letter = matcher.group(1).toUpperCase();
            double value = Double.parseDouble(matcher.group(2));
            String category;
            if (letter.equals("G")) {
                category = SUPPORTED_G_CODES.contains(asCode(value)) ? G_WORD : FALLBACK;
            } else if (letter.equals("M")) {
                category = SUPPORTED_M_CODES.contains(asCode(value)) ? M_WORD : FALLBACK;
            } else if (letter.equals("N")) {
                category = LINE_NUMBER;
            } else if (AXIS_PARAM_LETTERS.contains(letter)) {
                category = AXIS_PARAM;
            } else {
                category = FALLBACK;
            }
            spans.add(new Span(matcher.start(), matcher.end(), category));
        }

        if (commentStart >= 0) {
            spans.add(new Span(commentStart, text.length(), COMMENT));
        }
        return spans;
    }

    private static int asCode(double value) {
        return value == (int) value ? (int) value : -1;
    }
}
