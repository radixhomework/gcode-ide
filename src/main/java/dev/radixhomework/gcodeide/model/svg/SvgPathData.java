package dev.radixhomework.gcodeide.model.svg;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tokenizer for SVG path {@code d} data: splits it into command letters with
 * their argument groups (implicit repeats expanded into repeated commands,
 * so {@code M 0 0 10 10} yields M(0,0) then L(10,10) per the SVG grammar).
 * Absolute/relative letters are preserved for the interpreter.
 */
public final class SvgPathData {

    /** One path command: the (possibly relative) letter and its arguments. */
    public record Cmd(char letter, double[] args) {

        public boolean relative() {
            return Character.isLowerCase(letter);
        }

        public char absolute() {
            return Character.toUpperCase(letter);
        }

        // array components need content-based object methods (record defaults
        // would compare identity)
        @Override
        public boolean equals(Object o) {
            return o instanceof Cmd other
                    && letter == other.letter
                    && Arrays.equals(args, other.args);
        }

        @Override
        public int hashCode() {
            return 31 * Character.hashCode(letter) + Arrays.hashCode(args);
        }

        @Override
        public String toString() {
            return letter + Arrays.toString(args);
        }
    }

    private static final Pattern TOKEN =
            Pattern.compile("([A-Za-z])|([+-]?(?:\\d+\\.?\\d*|\\.\\d+)(?:[eE][+-]?\\d+)?)");

    private static final Map<Character, Integer> ARGS = Map.of(
            'M', 2, 'L', 2, 'H', 1, 'V', 1,
            'C', 6, 'S', 2, 'Q', 4, 'T', 2,
            'A', 7, 'Z', 0);

    private SvgPathData() {
    }

    /** Tokenizer state: current command, implicit-moveto flag, pending args. */
    private static final class ParseState {
        char cur;
        boolean movetoEmitted;
        final List<Double> nums = new ArrayList<>();
    }

    /** Parses a {@code d} attribute; throws IllegalArgumentException on bad syntax. */
    public static List<Cmd> parse(String d) {
        List<Cmd> cmds = new ArrayList<>();
        ParseState state = new ParseState();
        Matcher m = TOKEN.matcher(d == null ? "" : d);
        while (m.find()) {
            if (m.group(1) != null) {
                startCommand(cmds, state, m.group());
            } else {
                addNumber(cmds, state, m.group());
            }
        }
        flushGroup(cmds, state);
        if (!state.nums.isEmpty()) {
            throw new IllegalArgumentException("incomplete argument group for " + state.cur);
        }
        return cmds;
    }

    private static void startCommand(List<Cmd> cmds, ParseState state, String token) {
        flushGroup(cmds, state);
        char letter = token.charAt(0);
        state.cur = letter; // case preserved: lowercase = relative
        if (!ARGS.containsKey(Character.toUpperCase(state.cur))) {
            throw new IllegalArgumentException("unknown path command: " + token);
        }
        state.movetoEmitted = false;
        if (ARGS.get(Character.toUpperCase(state.cur)) == 0) {
            cmds.add(new Cmd(letter, new double[0]));
            state.cur = 0;
        }
        state.nums.clear();
    }

    private static void addNumber(List<Cmd> cmds, ParseState state, String token) {
        if (state.cur == 0) {
            throw new IllegalArgumentException("number before any command");
        }
        state.nums.add(Double.parseDouble(token));
        if (state.nums.size() == ARGS.get(Character.toUpperCase(state.cur))) {
            // moveto continuation is an implicit lineto (SVG 1.1 §8.3.2)
            char emit = state.cur;
            if (Character.toUpperCase(state.cur) == 'M' && state.movetoEmitted) {
                emit = Character.toUpperCase(state.cur) == state.cur ? 'L' : 'l';
            }
            emitArgs(cmds, emit, state.nums);
            state.nums.clear();
            state.movetoEmitted = true;
        }
    }

    private static void flushGroup(List<Cmd> cmds, ParseState state) {
        if (state.cur == 0 || state.nums.isEmpty()) {
            state.nums.clear();
            return;
        }
        if (state.nums.size() != ARGS.get(Character.toUpperCase(state.cur))) {
            throw new IllegalArgumentException("incomplete argument group for " + state.cur);
        }
        char emit = Character.toUpperCase(state.cur) == 'M' && state.movetoEmitted
                ? 'l' : state.cur;
        emitArgs(cmds, emit, state.nums);
    }

    private static void emitArgs(List<Cmd> cmds, char emit, List<Double> nums) {
        double[] args = new double[nums.size()];
        for (int i = 0; i < args.length; i++) {
            args[i] = nums.get(i);
        }
        cmds.add(new Cmd(emit, args));
    }
}
