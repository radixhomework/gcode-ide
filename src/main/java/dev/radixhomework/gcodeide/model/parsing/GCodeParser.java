package dev.radixhomework.gcodeide.model.parsing;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Two-stage GRBL-dialect G-code parser (pure Java, no JavaFX).
 *
 * <p>Stage 1 tokenizes each line into words (letter + number, case-insensitive),
 * stripping {@code ;} and parenthesized comments. Stage 2 runs a modal state
 * machine (position, motion mode G0-G3, G90/G91, G20/G21, F, spindle
 * M3/M4/M5) and emits a flat list of line-attributed moves, arcs flattened
 * into sub-segments.
 *
 * <p>Severity policy (drives the preview's stale indication): ERROR = the line
 * could not even be tokenized, skipped; WARNING = well-formed words that
 * cannot be interpreted (arc without I/J or R, radius too small), skipped;
 * INFO = unsupported words/codes, parsing continues with best-effort state.
 */
public final class GCodeParser {

    public static final double DEFAULT_ARC_TOLERANCE_MM = 0.01;
    private static final double INCH_TO_MM = 25.4;
    private static final double TWO_PI = 2.0 * Math.PI;
    private static final double EPS = 1e-9;

    private static final Pattern WORD_RE =
            Pattern.compile("([A-Za-z])\\s*([+-]?(?:\\d+\\.?\\d*|\\.\\d+))");
    private static final Pattern PAREN_COMMENT_RE = Pattern.compile("\\([^)]*\\)");

    private GCodeParser() {
    }

    /** A tokenized word: uppercase letter, numeric value, raw text. */
    public record Word(String letter, double value, String raw) {
    }

    /** Raised when a line is not syntactically valid G-code words. */
    public static final class TokenizerException extends IllegalArgumentException {
        public TokenizerException(String message) {
            super(message);
        }
    }

    /** Removes parenthesized comments and the trailing {@code ;} comment. */
    public static String stripComments(String line) {
        String withoutParens = PAREN_COMMENT_RE.matcher(line).replaceAll(" ");
        int semicolon = withoutParens.indexOf(';');
        return semicolon < 0 ? withoutParens : withoutParens.substring(0, semicolon);
    }

    /** Tokenizes one line; throws {@link TokenizerException} on junk text. */
    public static List<Word> tokenize(String line) {
        String stripped = stripComments(line);
        List<Word> words = new ArrayList<>();
        int pos = 0;
        Matcher matcher = WORD_RE.matcher(stripped);
        while (matcher.find()) {
            if (!stripped.substring(pos, matcher.start()).isBlank()) {
                int from = Math.max(0, matcher.start() - 1);
                int to = Math.min(stripped.length(), matcher.start() + 8);
                throw new TokenizerException(
                        "unrecognized text near '" + stripped.substring(from, to) + "'");
            }
            words.add(new Word(matcher.group(1).toUpperCase(), Double.parseDouble(matcher.group(2)),
                    matcher.group(0)));
            pos = matcher.end();
        }
        String tail = stripped.substring(pos).strip();
        if (!tail.isEmpty()) {
            throw new TokenizerException("unrecognized text '" + tail + "'");
        }
        return words;
    }

    /** Parses G-code text into the canonical toolpath model (mm, absolute, Y-up). */
    public static ParseResult parse(String text) {
        return parse(text, DEFAULT_ARC_TOLERANCE_MM);
    }

    public static ParseResult parse(String text, double arcToleranceMm) {
        return new Interpreter(arcToleranceMm).run(text);
    }

    /** Modal state machine over tokenized lines (GRBL power-on defaults). */
    private static final class Interpreter {
        private final double tol;
        private Position pos = new Position();
        private int motion = 0; // G0
        private boolean absolute = true; // G90
        private boolean inches = false; // G21
        private Double feed; // mm/min
        private Spindle spindle = Spindle.OFF;
        private final List<Move> moves = new ArrayList<>();
        private final List<Diagnostic> diagnostics = new ArrayList<>();

        Interpreter(double tol) {
            this.tol = tol;
        }

        ParseResult run(String text) {
            String[] lines = text.split("\n", -1);
            for (int i = 0; i < lines.length; i++) {
                int lineno = i + 1;
                List<Word> words;
                try {
                    words = tokenize(lines[i]);
                } catch (TokenizerException e) {
                    diag(lineno, Severity.ERROR, "malformed line skipped: " + e.getMessage());
                    continue;
                }
                if (!words.isEmpty()) {
                    line(lineno, words);
                }
            }
            return new ParseResult(moves, diagnostics);
        }

        private void line(int lineno, List<Word> words) {
            Map<String, Double> params = new HashMap<>();
            Integer motionWord = null;
            for (Word word : words) {
                String letter = word.letter();
                double value = word.value();
                switch (letter) {
                    case "G" -> {
                        int code = value == (int) value ? (int) value : -1;
                        switch (code) {
                            case 0, 1, 2, 3 -> motionWord = code;
                            case 90 -> absolute = true;
                            case 91 -> absolute = false;
                            case 20 -> inches = true;
                            case 21 -> inches = false;
                            case 17 -> { /* XY plane: the plane the model works in */ }
                            default -> diag(lineno, Severity.INFO,
                                    "unsupported code " + fmtCode("G", value) + " ignored");
                        }
                    }
                    case "M" -> {
                        if (value == 3) {
                            spindle = Spindle.CW;
                        } else if (value == 4) {
                            spindle = Spindle.CCW;
                        } else if (value == 5) {
                            spindle = Spindle.OFF;
                        } else {
                            diag(lineno, Severity.INFO,
                                    "unsupported code " + fmtCode("M", value) + " ignored");
                        }
                    }
                    case "N" -> { /* line number, consumed */ }
                    case "X", "Y", "Z", "I", "J", "R", "F", "S" -> params.put(letter, value);
                    default -> diag(lineno, Severity.INFO,
                            "unsupported word " + fmtCode(letter, value) + " ignored");
                }
            }

            if (params.containsKey("F")) {
                feed = mm(params.get("F"));
            }
            if (motionWord != null) {
                motion = motionWord;
            }

            boolean hasAxis = params.containsKey("X") || params.containsKey("Y")
                    || params.containsKey("Z");
            boolean hasArcData = params.containsKey("I") || params.containsKey("J")
                    || params.containsKey("R");
            if (motion == 0 || motion == 1) {
                if (hasAxis) {
                    linearMove(lineno, params);
                }
            } else if (hasAxis || hasArcData) {
                arcMove(lineno, params);
            }
        }

        private void linearMove(int lineno, Map<String, Double> params) {
            Position start = pos;
            Position end = target(params);
            pos = end;
            MoveKind kind = motion == 0 ? MoveKind.RAPID : MoveKind.CUT;
            moves.add(new Move(kind, start, end, lineno,
                    kind == MoveKind.CUT ? feed : null, false, spindle));
        }

        private void arcMove(int lineno, Map<String, Double> params) {
            Position start = pos;
            Position end = target(params);
            boolean clockwise = motion == 2;

            Position center;
            if (params.containsKey("R")) {
                double signedRadius = mm(params.get("R"));
                center = centerFromRadius(start, end, signedRadius, clockwise);
                if (center == null) {
                    diag(lineno, Severity.WARNING, String.format(
                            "arc skipped: R %.6g too small for the given endpoints",
                            Math.abs(signedRadius)));
                    return;
                }
            } else if (params.containsKey("I") || params.containsKey("J")) {
                double i = mm(params.getOrDefault("I", 0.0));
                double j = mm(params.getOrDefault("J", 0.0));
                center = new Position(start.x() + i, start.y() + j, 0);
            } else {
                diag(lineno, Severity.WARNING, "arc skipped: neither I/J offsets nor R radius given");
                return;
            }

            double radius = Math.hypot(start.x() - center.x(), start.y() - center.y());
            if (radius < EPS) {
                diag(lineno, Severity.WARNING, "arc skipped: zero radius");
                return;
            }

            double startAngle = Math.atan2(start.y() - center.y(), start.x() - center.x());
            double sweep = sweep(start, end, center, clockwise);
            boolean fullCircle = !params.containsKey("R")
                    && Math.abs(end.x() - start.x()) < EPS
                    && Math.abs(end.y() - start.y()) < EPS
                    && Math.abs(end.z() - start.z()) < EPS;
            if (fullCircle) {
                sweep = clockwise ? -TWO_PI : TWO_PI;
            } else if (Math.abs(sweep) < EPS) {
                sweep = clockwise ? -TWO_PI : TWO_PI; // degenerate: force a path
            }

            int segments = segmentCount(radius, Math.abs(sweep));
            Position previous = start;
            for (int i = 1; i <= segments; i++) {
                Position point;
                if (i == segments) {
                    point = end; // land exactly on the commanded endpoint
                } else {
                    double angle = startAngle + sweep * i / segments;
                    point = new Position(
                            center.x() + radius * Math.cos(angle),
                            center.y() + radius * Math.sin(angle),
                            start.z() + (end.z() - start.z()) * i / segments);
                }
                moves.add(new Move(MoveKind.CUT, previous, point, lineno, feed, true, spindle));
                previous = point;
            }
            pos = end;
        }

        private Position target(Map<String, Double> params) {
            double x = params.containsKey("X") ? mm(params.get("X")) : absolute ? pos.x() : 0.0;
            double y = params.containsKey("Y") ? mm(params.get("Y")) : absolute ? pos.y() : 0.0;
            double z = params.containsKey("Z") ? mm(params.get("Z")) : absolute ? pos.z() : 0.0;
            if (absolute) {
                return new Position(x, y, z);
            }
            return new Position(pos.x() + x, pos.y() + y, pos.z() + z);
        }

        private double sweep(Position start, Position end, Position center, boolean clockwise) {
            double a0 = Math.atan2(start.y() - center.y(), start.x() - center.x());
            double a1 = Math.atan2(end.y() - center.y(), end.x() - center.x());
            double raw = clockwise ? (a0 - a1) % TWO_PI : (a1 - a0) % TWO_PI;
            return clockwise ? -raw : raw;
        }

        private Position centerFromRadius(
                Position start, Position end, double signedRadius, boolean clockwise) {
            double dx = end.x() - start.x();
            double dy = end.y() - start.y();
            double chord = Math.hypot(dx, dy);
            double radius = Math.abs(signedRadius);
            if (chord < EPS || radius < EPS || chord > 2 * radius + EPS) {
                return null;
            }
            double h = Math.sqrt(Math.max(0.0, radius * radius - (chord / 2) * (chord / 2)));
            double midX = (start.x() + end.x()) / 2;
            double midY = (start.y() + end.y()) / 2;
            double ux = dx / chord;
            double uy = dy / chord;
            // R > 0 selects the minor (<180°) arc, R < 0 the major one; pick by swept angle
            for (double sign : new double[] {1.0, -1.0}) {
                Position candidate = new Position(midX - sign * h * uy, midY + sign * h * ux, 0);
                double swept = Math.abs(sweep(start, end, candidate, clockwise));
                if (signedRadius > 0 && swept <= Math.PI + EPS) {
                    return candidate;
                }
                if (signedRadius < 0 && swept >= Math.PI - EPS) {
                    return candidate;
                }
            }
            return null;
        }

        private int segmentCount(double radius, double sweep) {
            double ratio = Math.max(-1.0, Math.min(1.0, 1.0 - tol / radius));
            double maxStep = 2.0 * Math.acos(ratio);
            if (maxStep <= EPS) {
                return 1;
            }
            return Math.max(1, (int) Math.ceil(sweep / maxStep));
        }

        private double mm(double value) {
            return inches ? value * INCH_TO_MM : value;
        }

        private void diag(int line, Severity severity, String message) {
            diagnostics.add(new Diagnostic(line, severity, message));
        }
    }

    private static String fmtCode(String letter, double value) {
        return letter + (value == (long) value
                ? String.valueOf((long) value)
                : String.valueOf(value));
    }
}
