package dev.radixhomework.gcodeide.view;

import dev.radixhomework.gcodeide.model.parsing.Move;
import dev.radixhomework.gcodeide.model.parsing.MoveKind;
import dev.radixhomework.gcodeide.model.parsing.ParseResult;
import dev.radixhomework.gcodeide.model.preview.Diagnostics;
import dev.radixhomework.gcodeide.model.profiles.MachineProfile;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;
import javafx.geometry.Point2D;
import javafx.scene.canvas.Canvas;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;

/**
 * 2D top-down toolpath preview: an immediate-mode {@link Canvas} in millimeter
 * coordinates with a Y-up transform (design D5). Renders the bed from the
 * active profile, one stroke per move (rapids dashed gray, cuts depth-shaded,
 * out-of-bed red), highlights the current source line, keeps the last good
 * toolpath when a parse fails (stale), and hit-tests clicks back to lines.
 */
public class PreviewView {

    public static final Color RAPID_COLOR = Color.web("#808080");
    public static final Color OUT_OF_BED_COLOR = Color.web("#D62728");
    public static final Color HIGHLIGHT_COLOR = Color.rgb(255, 140, 0, 0.65);
    private static final double HIT_TOLERANCE_MM = 2.0;
    private static final double SHALLOW_HUE = 210; // blue at Z0
    private static final double DEEP_HUE = 0; // red at the deepest cut

    private final Canvas canvas = new Canvas();
    private final Pane wrapper = new Pane(canvas);
    private final List<IntConsumer> lineListeners = new ArrayList<>();
    private final List<IntConsumer> staleListeners = new ArrayList<>();

    private MachineProfile profile;
    private List<Move> moves = List.of();
    private int currentLine = -1;
    private boolean stale;

    // view transform: screenX = panX + x * scale; screenY = panY - y * scale (Y-up)
    private double scale = 1.0;
    private double panX = 0.0;
    private double panY = 0.0;
    private Point2D pressPoint;

    // observability for tests / diagnostics
    private int repaintCount;
    private int highlightCount;
    private double drawnBedX;
    private double drawnBedY;

    public PreviewView() {
        canvas.widthProperty().bind(wrapper.widthProperty());
        canvas.heightProperty().bind(wrapper.heightProperty());
        // the canvas is 0x0 while the profile is being set at startup, so the
        // view transform must re-fit whenever the canvas gains a real size
        canvas.widthProperty().addListener((obs, o, n) -> {
            fitToBed();
            repaint();
        });
        canvas.heightProperty().addListener((obs, o, n) -> {
            fitToBed();
            repaint();
        });

        canvas.addEventFilter(MouseEvent.MOUSE_PRESSED, this::onPressed);
        canvas.addEventFilter(MouseEvent.MOUSE_DRAGGED, this::onDragged);
        canvas.addEventFilter(MouseEvent.MOUSE_RELEASED, this::onReleased);
        canvas.addEventFilter(ScrollEvent.ANY, this::onScroll);
    }

    /** The JavaFX node to place in a layout. */
    public Region node() {
        return wrapper;
    }

    /** The canvas itself (for coordinate mapping and tests). */
    public Canvas canvas() {
        return canvas;
    }

    // -- profile & toolpath ------------------------------------------------------

    public void setProfile(MachineProfile profile) {
        this.profile = profile;
        fitToBed();
        repaint();
    }

    /** Shows the parsed toolpath, or keeps the last good scene when unparseable. */
    public void setToolpath(ParseResult result) {
        if (result.hasErrors()) {
            setStale(true);
            return;
        }
        moves = result.moves();
        setStale(false);
        repaint();
    }

    public boolean isStale() {
        return stale;
    }

    public int segmentCount() {
        return moves.size();
    }

    public int repaintCount() {
        return repaintCount;
    }

    public double drawnBedX() {
        return drawnBedX;
    }

    public double drawnBedY() {
        return drawnBedY;
    }

    /** Number of segments highlighted for {@link #currentLine} in the last repaint. */
    public int highlightCount() {
        return highlightCount;
    }

    // -- sync ----------------------------------------------------------------------

    public void setCurrentLine(int line) {
        this.currentLine = line;
        repaint();
    }

    public void addLineSelectedListener(IntConsumer listener) {
        lineListeners.add(listener);
    }

    public void addStaleListener(IntConsumer listener) { // 1 = stale, 0 = fresh
        staleListeners.add(listener);
    }

    // -- rendering ---------------------------------------------------------------

    private void repaint() {
        repaintCount++;
        var gc = canvas.getGraphicsContext2D();
        double w = canvas.getWidth();
        double h = canvas.getHeight();
        gc.clearRect(0, 0, w, h);
        if (profile == null) {
            return;
        }
        drawnBedX = profile.bedX();
        drawnBedY = profile.bedY();

        gc.setTransform(scale, 0, 0, -scale, panX, panY);

        // bed
        gc.setFill(Color.web("#FAFAFA"));
        gc.fillRect(0, 0, profile.bedX(), profile.bedY());
        gc.setStroke(Color.web("#404040"));
        gc.setLineWidth(1.0 / scale);
        gc.strokeRect(0, 0, profile.bedX(), profile.bedY());

        // toolpath
        double deepest = deepestCutZ(moves);
        highlightCount = 0;
        for (Move move : moves) {
            boolean outOfBed = Diagnostics.moveOutOfBed(move, profile);
            gc.setStroke(strokeColor(move, outOfBed, deepest));
            gc.setLineWidth(1.0 / scale);
            if (isDashed(move, outOfBed)) {
                gc.setLineDashes(4.0 / scale, 3.0 / scale);
            } else {
                gc.setLineDashes();
            }
            gc.strokeLine(move.start().x(), move.start().y(), move.end().x(), move.end().y());
        }

        // current-line highlight overlay
        if (currentLine > 0) {
            gc.setLineDashes();
            gc.setStroke(HIGHLIGHT_COLOR);
            gc.setLineWidth(2.5 / scale);
            for (Move move : moves) {
                if (move.line() == currentLine) {
                    gc.strokeLine(move.start().x(), move.start().y(), move.end().x(), move.end().y());
                    highlightCount++;
                }
            }
        }
        gc.setLineDashes();
        gc.setTransform(1, 0, 0, 1, 0, 0);

        if (stale) {
            gc.setFill(Color.web("#C00000"));
            gc.fillRect(8, 8, 250, 22);
            gc.setFill(Color.WHITE);
            gc.fillText("STALE - showing last good toolpath", 14, 24);
        }
    }

    private void setStale(boolean value) {
        if (value != stale) {
            stale = value;
            staleListeners.forEach(l -> l.accept(value ? 1 : 0));
        }
        repaint();
    }

    // -- pure style decisions (headless-testable) ------------------------------------

    /** Stroke color for a move: red out-of-bed, gray rapid, depth ramp for cuts. */
    public static Color strokeColor(Move move, boolean outOfBed, double deepestCutZ) {
        if (outOfBed) {
            return OUT_OF_BED_COLOR;
        }
        if (move.kind() == MoveKind.RAPID) {
            return RAPID_COLOR;
        }
        return depthColor(move.end().z(), deepestCutZ);
    }

    /** Dashes distinguish rapids; out-of-bed marking wins over the depth ramp, not the dash. */
    public static boolean isDashed(Move move, boolean outOfBed) {
        return move.kind() == MoveKind.RAPID && !outOfBed;
    }

    /** Hue ramp blue (Z0) to red (deepest). */
    public static Color depthColor(double z, double deepestCutZ) {
        if (deepestCutZ >= 0) {
            return Color.hsb(SHALLOW_HUE, 0.78, 0.71);
        }
        double t = Math.max(0.0, Math.min(1.0, z / deepestCutZ));
        double hue = SHALLOW_HUE + (DEEP_HUE - SHALLOW_HUE) * t;
        return Color.hsb(hue, 0.78, 0.71);
    }

    public static double deepestCutZ(List<Move> moves) {
        double deepest = -1.0;
        for (Move move : moves) {
            if (move.kind() == MoveKind.CUT) {
                deepest = Math.min(deepest, move.end().z());
            }
        }
        return deepest;
    }

    /** Nearest segment within {@code toleranceMm} of (x, y); its source line, or -1. */
    public static int hitLine(List<Move> moves, double x, double y, double toleranceMm) {
        int bestLine = -1;
        double best = toleranceMm;
        for (Move move : moves) {
            double d = distanceToSegment(x, y, move.start().x(), move.start().y(),
                    move.end().x(), move.end().y());
            if (d <= best) {
                best = d;
                bestLine = move.line();
            }
        }
        return bestLine;
    }

    private static double distanceToSegment(double px, double py, double x1, double y1,
            double x2, double y2) {
        double dx = x2 - x1;
        double dy = y2 - y1;
        double lengthSq = dx * dx + dy * dy;
        if (lengthSq < 1e-18) {
            return Math.hypot(px - x1, py - y1);
        }
        double t = Math.max(0.0, Math.min(1.0, ((px - x1) * dx + (py - y1) * dy) / lengthSq));
        return Math.hypot(px - (x1 + t * dx), py - (y1 + t * dy));
    }

    // -- interaction ---------------------------------------------------------------------

    private void onPressed(MouseEvent event) {
        pressPoint = new Point2D(event.getX(), event.getY());
    }

    private void onDragged(MouseEvent event) {
        if (pressPoint == null) {
            return;
        }
        panX += event.getX() - pressPoint.getX();
        panY += event.getY() - pressPoint.getY();
        pressPoint = new Point2D(event.getX(), event.getY());
        repaint();
    }

    private void onReleased(MouseEvent event) {
        if (pressPoint == null) {
            return;
        }
        boolean movedFar = pressPoint.distance(event.getX(), event.getY()) > 4.0;
        pressPoint = null;
        if (movedFar) {
            return;
        }
        Point2D scene = toScene(event.getX(), event.getY());
        int line = hitLine(moves, scene.getX(), scene.getY(), HIT_TOLERANCE_MM / scale);
        if (line > 0) {
            lineListeners.forEach(l -> l.accept(line));
        }
    }

    private void onScroll(ScrollEvent event) {
        double factor = event.getDeltaY() > 0 ? 1.15 : 1.0 / 1.15;
        double newScale = Math.max(1e-6, Math.min(1e6, scale * factor));
        // keep the scene point under the cursor fixed while zooming
        Point2D scene = toScene(event.getX(), event.getY());
        scale = newScale;
        panX = event.getX() - scene.getX() * scale;
        panY = event.getY() + scene.getY() * scale;
        event.consume();
        repaint();
    }

    // -- coordinate mapping (exposed for tests) -----------------------------------------

    public Point2D toScreen(double sceneX, double sceneY) {
        return new Point2D(panX + sceneX * scale, panY - sceneY * scale);
    }

    public Point2D toScene(double screenX, double screenY) {
        return new Point2D((screenX - panX) / scale, (panY - screenY) / scale);
    }

    private void fitToBed() {
        double w = canvas.getWidth();
        double h = canvas.getHeight();
        if (profile == null || w <= 0 || h <= 0) {
            return;
        }
        double margin = 5.0;
        scale = Math.min(w / (profile.bedX() + 2 * margin), h / (profile.bedY() + 2 * margin));
        panX = (w - profile.bedX() * scale) / 2;
        panY = (h + profile.bedY() * scale) / 2;
    }
}
