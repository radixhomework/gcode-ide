package dev.radixhomework.gcodeide.model.svg;

import java.util.ArrayList;
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
    }

    private static final Pattern TOKEN =
            Pattern.compile("([MLHVCSQTAZmlhvcsqtaz])|([+-]?(?:\\d+\\.?\\d*|\\.\\d+)(?:[eE][+-]?\\d+)?)");

    private static final Map<Character, Integer> ARGS = Map.of(
            'M', 2, 'L', 2, 'H', 1, 'V', 1,
            'C', 6, 'S', 2, 'Q', 4, 'T', 2,
            'A', 7, 'Z', 0);

    private SvgPathData() {
    }

    /** Parses a {@code d} attribute; throws IllegalArgumentException on bad syntax. */
    public static List<Cmd> parse(String d) {
        List<Cmd> cmds = new ArrayList<>();
        Matcher m = TOKEN.matcher(d == null ? "" : d);
        char cur = 0;
        boolean movetoEmitted = false;
        List<Double> nums = new ArrayList<>();

        while (m.find()) {
            String token = m.group();
            if (m.group(1) != null) {
                flushGroup(cmds, cur, movetoEmitted, nums);
                char letter = token.charAt(0);
                cur = letter; // case preserved: lowercase = relative
                if (!ARGS.containsKey(Character.toUpperCase(cur))) {
                    throw new IllegalArgumentException("unknown path command: " + token);
                }
                movetoEmitted = false;
                if (ARGS.get(Character.toUpperCase(cur)) == 0) {
                    cmds.add(new Cmd(letter, new double[0]));
                    cur = 0;
                }
                nums.clear();
            } else {
                if (cur == 0) {
                    throw new IllegalArgumentException("number before any command");
                }
                nums.add(Double.parseDouble(token));
                if (nums.size() == ARGS.get(Character.toUpperCase(cur))) {
                    // moveto continuation is an implicit lineto (SVG 1.1 §8.3.2)
                    char emit = cur;
                    if (Character.toUpperCase(cur) == 'M' && movetoEmitted) {
                        emit = Character.toUpperCase(cur) == cur ? 'L' : 'l';
                    }
                    double[] args = new double[nums.size()];
                    for (int i = 0; i < args.length; i++) {
                        args[i] = nums.get(i);
                    }
                    cmds.add(new Cmd(emit, args));
                    nums.clear();
                    movetoEmitted = true;
                }
            }
        }
        flushGroup(cmds, cur, movetoEmitted, nums);
        if (!nums.isEmpty()) {
            throw new IllegalArgumentException("incomplete argument group for " + cur);
        }
        return cmds;
    }

    private static void flushGroup(List<Cmd> cmds, char cur, boolean movetoEmitted,
            List<Double> nums) {
        if (cur == 0 || nums.isEmpty()) {
            nums.clear();
            return;
        }
        if (nums.size() != ARGS.get(Character.toUpperCase(cur))) {
            throw new IllegalArgumentException("incomplete argument group for " + cur);
        }
        char emit = Character.toUpperCase(cur) == 'M' && movetoEmitted ? 'l' : cur;
        double[] args = new double[nums.size()];
        for (int i = 0; i < args.length; i++) {
            args[i] = nums.get(i);
        }
        cmds.add(new Cmd(emit, args));
        nums.clear();
    }
}
