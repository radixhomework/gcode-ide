package dev.radixhomework.gcodeide.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.radixhomework.gcodeide.model.parsing.GCodeParser;
import dev.radixhomework.gcodeide.model.parsing.Move;
import dev.radixhomework.gcodeide.model.parsing.MoveKind;
import java.util.ArrayList;
import java.util.List;
import javafx.geometry.Point3D;
import javafx.scene.input.MouseEvent;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.testfx.framework.junit5.ApplicationTest;

class Preview3DViewTest extends ApplicationTest {

    private Preview3DView view;

    @Override
    public void start(Stage stage) {
        view = new Preview3DView();
        stage.setScene(new javafx.scene.Scene(view.node(), 500, 350));
        stage.show();
    }

    private void load(String gcode) {
        interact(() -> view.setToolpath(GCodeParser.parse(gcode)));
    }

    // --- world mapping (re-anchored on content bounds) ---------------------------

    @Test
    void worldMappingCentersContentAndPutsDepthDown() {
        load("G0 X0 Y0\nG1 X30 Y30 Z-1 F600\n");
        Point3D origin = view.toWorld(0, 0, 0);
        assertEquals(-15.0, origin.getX(), 1e-9); // content X centered
        assertEquals(15.0, origin.getZ(), 1e-9); // content Y centered (orientation-preserving -Z)
        assertEquals(0.0, origin.getY(), 1e-9);
        Point3D shallow = view.toWorld(10, 10, -1);
        Point3D deep = view.toWorld(10, 10, -4);
        assertTrue(deep.getY() < shallow.getY(), "deeper passes render lower");
        assertTrue(view.toWorld(0, 30, 0).getZ() < view.toWorld(0, 0, 0).getZ(),
                "bed +Y maps toward -Z (screen-up in a top view)");
    }

    @Test
    void cameraStartsAboveTheContent() {
        load("G1 X10 F600\n");
        Point3D position = Preview3DView.cameraPosition(view.yawAngle(), view.pitchAngle(),
                view.cameraDistance());
        assertTrue(position.getY() > 0, "initial camera above the content plane");
        assertTrue(view.contentVisibleFromCamera());
    }

    @Test
    void previewCanShrinkInASplitPane() {
        // regression: SubScene's min size defaults to its current bounds,
        // which froze the SplitPane divider (the preview could never shrink)
        assertTrue(view.node().getMinWidth() == 0 && view.node().getMinHeight() == 0,
                "preview wrapper min sizes are zero so the divider moves freely");
    }

    @Test
    void contentIsVisibleFromTheCamera() {
        load("G1 X10 F600\n");
        assertTrue(view.contentVisibleFromCamera(), "content inside view cone and clip range");
        assertTrue(view.cameraFarClip() > view.cameraDistance());

        // wheel-zooming out keeps the content visible
        interact(() -> view.dolly(-1000));
        assertTrue(view.contentVisibleFromCamera());
    }

    @Test
    void arcChiralityMatchesBedCoordinatesInView() {
        // G2 X10 Y10 I10 from the origin bulges ABOVE the chord in bed
        // coordinates (parser golden); the world mapping must preserve that
        // side when viewed from above (bed +Y toward screen-top = world -Z)
        var result = GCodeParser.parse("G2 X10 Y10 I10 F600\n");
        var arcMoves = result.moves().stream()
                .filter(Move::fromArc).toList();
        var mid = arcMoves.get((arcMoves.size() - 1) / 2);
        interact(() -> view.setToolpath(result));
        double worldZArcMid = view.toWorld(mid.end().x(), mid.end().y(), 0).getZ();
        double worldZChordMid = view.toWorld(5, 5, 0).getZ();
        assertTrue(worldZArcMid < worldZChordMid,
                "G2 arc bulges to the same side as in bed coordinates");
    }

    // --- rendering ------------------------------------------------------------------

    @Test
    void segmentCountMatchesMoveCount() {
        load("G0 X10 Y10\nG1 X20 Y20 F600\n");
        assertEquals(2, view.segmentCount());
        assertEquals(2, view.renderedNodes(), "one cylinder per move, no bed box");
    }

    @Test
    void strokeStyleDecisions() {
        var light = Preview3DView.PreviewPalette.LIGHT;
        Move rapid = move(MoveKind.RAPID, 0, 0, 10, 0, 1);
        Move cut = move(MoveKind.CUT, 10, 0, 20, 0, 2);
        assertEquals(light.rapid(), Preview3DView.segmentColor(rapid, -2.0, light));
        assertTrue(!Preview3DView.segmentColor(cut, -2.0, light)
                .equals(Preview3DView.depthColor(-0.5, -2.0)),
                "depth ramp distinguishes depths");
        assertTrue(Preview3DView.depthColor(0, -2.0).getHue()
                > Preview3DView.depthColor(-2, -2.0).getHue(),
                "hue decreases with depth");
    }

    @Test
    void setDarkSwapsPaletteAndRestyles() {
        load("G1 X10 F600\n");
        assertEquals(Preview3DView.PreviewPalette.LIGHT, view.palette());
        int rebuilds = view.rebuildCount();
        interact(() -> view.setDark(true));
        assertEquals(Preview3DView.PreviewPalette.DARK, view.palette());
        assertTrue(view.rebuildCount() > rebuilds, "dark switch restyled live");
        interact(() -> view.setDark(false));
        assertEquals(Preview3DView.PreviewPalette.LIGHT, view.palette());
    }

    // --- sync -------------------------------------------------------------------------

    @Test
    void clickOnSegmentEmitsLineSelected() {
        load("G0 X10\nG1 X20 F600\n");
        List<Integer> selected = new ArrayList<>();
        view.addLineSelectedListener(selected::add);
        javafx.scene.shape.Shape3D segment = view.segmentNodeForLine(2);
        interact(() -> {
            javafx.scene.input.PickResult pick =
                    new javafx.scene.input.PickResult(segment, new Point3D(15, 0, 0), 1.0);
            javafx.event.Event.fireEvent(view.subScene(), press(pick));
            javafx.event.Event.fireEvent(view.subScene(), release(pick));
        });
        assertEquals(List.of(2), selected);
    }

    @Test
    void setCurrentLineHighlightsThatLinesSegments() {
        load("G0 X10\nG1 X20 F600\nG1 Z-4 X30\n");
        interact(() -> view.setCurrentLine(2));
        assertEquals(1, view.highlightCount());
        interact(() -> view.setCurrentLine(9));
        assertEquals(0, view.highlightCount());
    }

    @Test
    void staleKeepsLastGoodToolpath() {
        load("G0 X10\nG1 X20 F600\n");
        assertFalse(view.isStale());
        interact(() -> view.setToolpath(GCodeParser.parse("G1 X\n")));
        assertTrue(view.isStale());
        assertEquals(2, view.segmentCount(), "last good toolpath retained");
    }

    @Test
    void dragRotatesAroundBedCenter() {
        load("G1 X10 F600\n");
        double before = view.yawAngle();
        interact(() -> {
            javafx.event.Event.fireEvent(view.subScene(),
                    new MouseEvent(MouseEvent.MOUSE_PRESSED, 0, 0, 0, 0,
                            javafx.scene.input.MouseButton.PRIMARY, 1,
                            false, false, false, false, true, false, false, false, false, false,
                            null));
            javafx.event.Event.fireEvent(view.subScene(), drag(20, 10));
        });
        org.junit.jupiter.api.Assertions.assertNotEquals(before, view.yawAngle());
    }

    @Test
    void rightDragPansWhenPlatformOmitsButtonFlags() {
        interact(() -> load("G1 X10 F600\n"));
        double yawBefore = view.yawAngle();
        // regression for the real-world failure: platform DRAGGED events can
        // omit the button-down flags; dispatch must use the press button.
        interact(() -> {
            javafx.event.Event.fireEvent(view.subScene(),
                    new MouseEvent(MouseEvent.MOUSE_PRESSED, 0, 0, 0, 0,
                            javafx.scene.input.MouseButton.SECONDARY, 1,
                            false, false, false, false, false, false, false, false, false, false,
                            null));
            javafx.event.Event.fireEvent(view.subScene(),
                    new MouseEvent(MouseEvent.MOUSE_DRAGGED, 25, 15, 0, 0,
                            javafx.scene.input.MouseButton.SECONDARY, 1,
                            false, false, false, false, false, false, false, false, false, false,
                            null));
        });
        org.junit.jupiter.api.Assertions.assertTrue(view.panX() != 0 || view.panY() != 0,
                "pan offset changed despite missing button flags");
        org.junit.jupiter.api.Assertions.assertEquals(yawBefore, view.yawAngle());
    }

    @Test
    void leftDragOrbitsWithoutPanning() {
        interact(() -> load("G1 X10 F600\n"));
        double yawBefore = view.yawAngle();
        interact(() -> {
            javafx.event.Event.fireEvent(view.subScene(),
                    new MouseEvent(MouseEvent.MOUSE_PRESSED, 0, 0, 0, 0,
                            javafx.scene.input.MouseButton.PRIMARY, 1,
                            false, false, false, false, true, false, false, false, false, false,
                            null));
            javafx.event.Event.fireEvent(view.subScene(), drag(20, 10));
        });
        org.junit.jupiter.api.Assertions.assertNotEquals(yawBefore, view.yawAngle());
        org.junit.jupiter.api.Assertions.assertEquals(0, view.panX(), 1e-9);
        org.junit.jupiter.api.Assertions.assertEquals(0, view.panY(), 1e-9);
    }

    @Test
    void panPersistsAcrossOrbitAndZoom() {
        interact(() -> load("G1 X10 F600\n"));
        interact(() -> {
            javafx.event.Event.fireEvent(view.subScene(), buttonPress());
            javafx.event.Event.fireEvent(view.subScene(), buttonDrag());
        });
        double panX = view.panX();
        double panY = view.panY();

        interact(() -> {
            javafx.event.Event.fireEvent(view.subScene(),
                    new MouseEvent(MouseEvent.MOUSE_PRESSED, 0, 0, 0, 0,
                            javafx.scene.input.MouseButton.PRIMARY, 1,
                            false, false, false, false, true, false, false, false, false, false,
                            null));
            javafx.event.Event.fireEvent(view.subScene(), drag(30, -10));
        });
        interact(() -> view.dolly(-100));
        org.junit.jupiter.api.Assertions.assertEquals(panX, view.panX(), 1e-9);
        org.junit.jupiter.api.Assertions.assertEquals(panY, view.panY(), 1e-9);
    }

    private static MouseEvent buttonPress() {
        return new MouseEvent(MouseEvent.MOUSE_PRESSED, 0, 0, 0, 0,
                javafx.scene.input.MouseButton.SECONDARY, 1,
                false, false, false, false, false, false, true, false, false, false, null);
    }

    private static MouseEvent buttonDrag() {
        return new MouseEvent(MouseEvent.MOUSE_DRAGGED, 25, 15, 0, 0,
                javafx.scene.input.MouseButton.SECONDARY, 1,
                false, false, false, false, false, false, true, false, false, false, null);
    }

    @Test
    void scrollDolliesCamera() {
        interact(() -> load("G1 X10 F600\n"));
        double before = view.cameraDistance();
        interact(() -> view.dolly(-20));
        org.junit.jupiter.api.Assertions.assertTrue(view.cameraDistance() > before);
    }

    @Test
    void zeroLengthSegmentSkipped() {
        assertNull(Preview3DView.segmentBetween(new Point3D(1, 2, 3), new Point3D(1, 2, 3)));
        javafx.scene.shape.Cylinder cylinder =
                Preview3DView.segmentBetween(new Point3D(0, 0, 0), new Point3D(0, 10, 0));
        assertEquals(10.0, cylinder.getHeight(), 1e-9);
    }

    private static dev.radixhomework.gcodeide.model.parsing.Move move(
            dev.radixhomework.gcodeide.model.parsing.MoveKind kind,
            double x1, double y1, double x2, double y2, int line) {
        return new dev.radixhomework.gcodeide.model.parsing.Move(
                kind, new dev.radixhomework.gcodeide.model.parsing.Position(x1, y1, 0),
                new dev.radixhomework.gcodeide.model.parsing.Position(x2, y2, 0), line,
                kind == dev.radixhomework.gcodeide.model.parsing.MoveKind.CUT ? 600.0 : null,
                false,
                dev.radixhomework.gcodeide.model.parsing.Spindle.OFF);
    }

    private static MouseEvent drag(double dx, double dy) {
        return new MouseEvent(MouseEvent.MOUSE_DRAGGED, dx, dy, 0, 0,
                javafx.scene.input.MouseButton.PRIMARY, 1,
                false, false, false, false, true, false, false, false, false, false, null);
    }

    private static MouseEvent press(javafx.scene.input.PickResult pick) {
        return new MouseEvent(MouseEvent.MOUSE_PRESSED, 5, 5, 0, 0,
                javafx.scene.input.MouseButton.PRIMARY, 1,
                false, false, false, false, true, false, false, false, false, false, pick);
    }

    private static MouseEvent release(javafx.scene.input.PickResult pick) {
        return new MouseEvent(MouseEvent.MOUSE_RELEASED, 5, 5, 0, 0,
                javafx.scene.input.MouseButton.PRIMARY, 1,
                false, false, false, false, false, false, false, false, false, true, pick);
    }
}
