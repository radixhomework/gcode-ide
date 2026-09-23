package dev.radixhomework.gcodeide.view.syntax;

import java.util.List;
import java.util.Locale;

/**
 * The documented GRBL word table powering autocompletion (pure Java). The
 * code set mirrors what the parser and the syntax classifier support.
 */
public final class GCodeWords {

    /** A completion suggestion: the word plus a one-line description. */
    public record Entry(String code, String description) {

        /** Popup rendering: {@code G1 — linear feed move}. */
        public String display() {
            return code + " — " + description;
        }
    }

    private static final List<Entry> ENTRIES = List.of(
            new Entry("G0", "rapid positioning move"),
            new Entry("G1", "linear feed move"),
            new Entry("G2", "clockwise arc (IJ or R form)"),
            new Entry("G3", "counterclockwise arc (IJ or R form)"),
            new Entry("G17", "XY plane selection"),
            new Entry("G20", "inches"),
            new Entry("G21", "millimeters"),
            new Entry("G90", "absolute distance mode"),
            new Entry("G91", "incremental distance mode"),
            new Entry("M3", "spindle on clockwise"),
            new Entry("M4", "spindle on counterclockwise"),
            new Entry("M5", "spindle off"),
            new Entry("X", "X axis coordinate"),
            new Entry("Y", "Y axis coordinate"),
            new Entry("Z", "Z axis coordinate (depth, negative into stock)"),
            new Entry("I", "arc center X offset (relative to arc start)"),
            new Entry("J", "arc center Y offset (relative to arc start)"),
            new Entry("R", "arc radius (negative selects the major arc)"),
            new Entry("F", "feed rate (mm/min)"),
            new Entry("S", "spindle speed (RPM)"));

    private static final String SEPARATOR = " — ";

    private GCodeWords() {
    }

    /** Entries whose code starts with the prefix (case-insensitive), in table order. */
    public static List<Entry> suggestionsFor(String prefix) {
        String p = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
        return ENTRIES.stream()
                .filter(e -> e.code().toLowerCase(Locale.ROOT).startsWith(p))
                .toList();
    }

    /** The code part of a popup display string. */
    public static String codeOf(String display) {
        int cut = display.indexOf(SEPARATOR);
        return cut < 0 ? display : display.substring(0, cut);
    }

    /** All entries (for tests and future hover docs). */
    public static List<Entry> entries() {
        return ENTRIES;
    }
}
