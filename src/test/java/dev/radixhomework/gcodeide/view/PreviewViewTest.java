package dev.radixhomework.gcodeide.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.radixhomework.gcodeide.model.parsing.GCodeParser;
import dev.radixhomework.gcodeide.model.parsing.Move;
import dev.radixhomework.gcodeide.model.parsing.MoveKind;
import dev.radixhomework.gcodeide.model.parsing.ParseResult;
import dev.radixhomework.gcodeide.model.parsing.Position;
import dev.radixhomework.gcodeide.model.profiles.MachineProfile;
import java.util.ArrayList;
import java.util.List;
import javafx.geometry.Point2D;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.testfx.framework.junit5.ApplicationTest;

class PreviewViewTest extends ApplicationTest {

    private static final MachineProfile PROFILE =
            new MachineProfile("Test Mill", 300.0, 180.0, 45.0, 800.0, 1000.0, 5.0);
    private static final MachineProfile WIDER =
            new MachineProfile("Wide Mill", 400.0, 200.0, 45.0, 800.0, 1000.0, 5.0);

    private PreviewView preview;

    @Override
    public void start(Stage stage) {
        preview = new PreviewView();
        stage.setScene(new javafx.scene.Scene(preview.node(), 500, 350));
        stage.show();
    }

    // --- task 6.1: bed follows the profile -------------------------------------

    @Test
    void bedRedrawsWhenProfileChanges() {
        interact(() -> preview.setProfile(PROFILE));
        assertEquals(300.0, preview.drawnBedX());
        assertEquals(180.0, preview.drawnBedY());
        int before = preview.repaintCount();
        interact(() -> preview.setProfile(WIDER));
        assertEquals(400.0, preview.drawnBedX());
        assertEquals(200.0, preview.drawnBedY());
        assertTrue(preview.repaintCount() > before, "profile change triggered a repaint");
    }

    // --- task 6.2: render-logic (headless style decisions) -----------------------

    @Test
    void strokeStyleDecisions() {
        Move rapid = move(MoveKind.RAPID, 0, 0, 10, 0, 1);
        Move cut = move(MoveKind.CUT, 10, 0, 20, 0, 2);
        assertTrue(PreviewView.isDashed(rapid, false), "rapids dashed");
        assertFalse(PreviewView.isDashed(cut, false), "cuts solid");
        assertFalse(PreviewView.isDashed(rapid, true), "out-of-bed marking wins over dash");
        assertEquals(PreviewView.OUT_OF_BED_COLOR, PreviewView.strokeColor(cut, true, -2.0));
        assertEquals(PreviewView.RAPID_COLOR, PreviewView.strokeColor(rapid, false, -2.0));
        assertTrue(!PreviewView.strokeColor(cut, false, -2.0)
                .equals(PreviewView.depthColor(-0.5, -2.0)),
                "depth ramp distinguishes depths");
        assertTrue(PreviewView.depthColor(0, -2.0).getHue() > PreviewView.depthColor(-2, -2.0).getHue(),
                "hue decreases with depth");
    }

    @Test
    void segmentCountMatchesMoveCount() {
        interact(() -> {
            preview.setProfile(PROFILE);
            preview.setToolpath(GCodeParser.parse("G0 X10 Y10\nG1 X20 Y20 F600\n"));
        });
        assertEquals(2, preview.segmentCount());
    }

    // --- task 6.3: bidirectional sync ----------------------------------------------

    @Test
    void clickEmitsLineSelected() {
        interact(() -> {
            preview.setProfile(PROFILE);
            preview.setToolpath(GCodeParser.parse("G0 X10\nG1 X20 F600\n"));
        });
        List<Integer> selected = new ArrayList<>();
        preview.addLineSelectedListener(selected::add);
        interact(() -> {
            double x = preview.toScreen(15, 0).getX(); // midpoint of the line-2 cut segment
            double y = preview.toScreen(15, 0).getY();
            javafx.scene.input.MouseEvent pressed = new javafx.scene.input.MouseEvent(
                    javafx.scene.input.MouseEvent.MOUSE_PRESSED, x, y, x + 100, y + 100,
                    javafx.scene.input.MouseButton.PRIMARY, 1,
                    false, false, false, false, true, false, false, false, false, false, null);
            javafx.scene.input.MouseEvent released = new javafx.scene.input.MouseEvent(
                    javafx.scene.input.MouseEvent.MOUSE_RELEASED, x, y, x + 100, y + 100,
                    javafx.scene.input.MouseButton.PRIMARY, 1,
                    false, false, false, false, false, false, false, false, false, true, null);
            javafx.event.Event.fireEvent(preview.canvas(), pressed);
            javafx.event.Event.fireEvent(preview.canvas(), released);
        });
        assertEquals(List.of(2), selected);
    }

    @Test
    void setCurrentLineHighlightsThatLinesSegments() {
        interact(() -> {
            preview.setProfile(PROFILE);
            preview.setToolpath(GCodeParser.parse("G0 X10\nG1 X20 F600\n"));
        });
        interact(() -> preview.setCurrentLine(2));
        assertEquals(1, preview.highlightCount());
        interact(() -> preview.setCurrentLine(1));
        assertEquals(1, preview.highlightCount());
        interact(() -> preview.setCurrentLine(9));
        assertEquals(0, preview.highlightCount(), "no moves on that line");
    }

    @Test
    void hitTestFindsNearestSegmentLine() {
        List<Move> moves = GCodeParser.parse("G0 X10\nG1 X20 F600\n").moves();
        assertEquals(2, PreviewView.hitLine(moves, 15, 0.1, 2.0));
        assertEquals(-1, PreviewView.hitLine(moves, 15, 5, 2.0));
    }

    // --- task 6.4: stale handling ------------------------------------------------------

    @Test
    void errorParseKeepsLastGoodSceneAndSetsStale() {
        interact(() -> {
            preview.setProfile(PROFILE);
            preview.setToolpath(GCodeParser.parse("G0 X10\nG1 X20 F600\n"));
        });
        assertFalse(preview.isStale());
        interact(() -> preview.setToolpath(GCodeParser.parse("G1 X\n"))); // syntax error
        assertTrue(preview.isStale());
        assertEquals(2, preview.segmentCount(), "last good scene retained");
        interact(() -> preview.setToolpath(GCodeParser.parse("G1 X10 F600\n")));
        assertFalse(preview.isStale());
    }

    @Test
    void warningOnlyParseIsNotStale() {
        interact(() -> {
            preview.setProfile(PROFILE);
            preview.setToolpath(GCodeParser.parse("G2 X20 Y20\n")); // bad arc: WARNING
        });
        assertFalse(preview.isStale());
    }

    @Test
    void staleListenerFires() {
        interact(() -> {
            preview.setProfile(PROFILE);
            preview.setToolpath(GCodeParser.parse("G1 X10 F600\n"));
        });
        List<Integer> staleEvents = new ArrayList<>();
        preview.addStaleListener(staleEvents::add);
        interact(() -> preview.setToolpath(GCodeParser.parse("G1 X\n")));
        assertEquals(List.of(1), staleEvents);
    }

    private static Move move(MoveKind kind, double x1, double y1, double x2, double y2, int line) {
        return new Move(kind, new Position(x1, y1, 0), new Position(x2, y2, 0), line,
                kind == MoveKind.CUT ? 600.0 : null, false,
                dev.radixhomework.gcodeide.model.parsing.Spindle.OFF);
    }
}
