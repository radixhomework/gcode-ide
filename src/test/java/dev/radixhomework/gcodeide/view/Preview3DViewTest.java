package dev.radixhomework.gcodeide.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.radixhomework.gcodeide.model.parsing.GCodeParser;
import dev.radixhomework.gcodeide.model.parsing.Move;
import dev.radixhomework.gcodeide.model.parsing.MoveKind;
import dev.radixhomework.gcodeide.model.profiles.MachineProfile;
import java.util.ArrayList;
import java.util.List;
import javafx.geometry.Point3D;
import javafx.scene.input.MouseEvent;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.testfx.framework.junit5.ApplicationTest;

class Preview3DViewTest extends ApplicationTest {

    private static final MachineProfile PROFILE =
            new MachineProfile("Test Mill", 300.0, 180.0, 45.0, 800.0, 1000.0, 5.0);

    private Preview3DView view;

    @Override
    public void start(Stage stage) {
        view = new Preview3DView();
        stage.setScene(new javafx.scene.Scene(view.node(), 500, 350));
        stage.show();
    }

    @Test
    void worldMappingCentersBedAndPutsDepthDown() {
        Point3D origin = Preview3DView.toWorld(0, 0, 0, PROFILE);
        assertEquals(-150.0, origin.getX(), 1e-9); // bed X centered
        assertEquals(90.0, origin.getZ(), 1e-9); // bed Y centered (orientation-preserving −Z)
        assertEquals(0.0, origin.getY(), 1e-9);
        Point3D shallow = Preview3DView.toWorld(10, 10, -1, PROFILE);
        Point3D deep = Preview3DView.toWorld(10, 10, -4, PROFILE);
        assertTrue(deep.getY() < shallow.getY(), "deeper passes render lower");
        assertTrue(Preview3DView.toWorld(0, 180, 0, PROFILE).getZ() < origin.getZ(),
                "bed +Y maps toward -Z (screen-up in a top view)");
    }

    @Test
    void cameraStartsAboveTheBed() {
        interact(() -> view.setProfile(PROFILE));
        Point3D position = Preview3DView.cameraPosition(view.yawAngle(), view.pitchAngle(),
                view.cameraDistance());
        assertTrue(position.getY() > 0, "initial camera above the bed plane");
        assertTrue(view.bedVisibleFromCamera());
    }

    @Test
    void arcChiralityMatchesBedCoordinatesInView() {
        // G2 X10 Y10 I10 from the origin bulges ABOVE the chord in bed
        // coordinates (parser golden); the world mapping must preserve that
        // side when viewed from above (bed +Y toward screen-top = world -Z)
        var result = dev.radixhomework.gcodeide.model.parsing.GCodeParser.parse("G2 X10 Y10 I10 F600\n");
        var arcMoves = result.moves().stream()
                .filter(dev.radixhomework.gcodeide.model.parsing.Move::fromArc).toList();
        var mid = arcMoves.get((arcMoves.size() - 1) / 2);
        double worldZArcMid = Preview3DView.toWorld(mid.end().x(), mid.end().y(), 0, PROFILE).getZ();
        double worldZChordMid = Preview3DView.toWorld(5, 5, 0, PROFILE).getZ();
        assertTrue(worldZArcMid < worldZChordMid,
                "G2 arc bulges to the same side as in bed coordinates");
    }

    @Test
    void oneSegmentNodePerMovePlusBed() {
        interact(() -> {
            view.setProfile(PROFILE);
            view.setToolpath(GCodeParser.parse("G0 X10\nG1 X20 F600\nG1 Z-4 X30\n"));
        });
        assertEquals(3, view.segmentCount());
        assertEquals(4, view.renderedNodes(), "bed box + one cylinder per move");
    }

    @Test
    void setCurrentLineHighlightsThatLinesSegments() {
        interact(() -> {
            view.setProfile(PROFILE);
            view.setToolpath(GCodeParser.parse("G0 X10\nG1 X20 F600\nG1 Z-4 X30\n"));
        });
        interact(() -> view.setCurrentLine(2));
        assertEquals(1, view.highlightCount());
        interact(() -> view.setCurrentLine(9));
        assertEquals(0, view.highlightCount());
    }

    @Test
    void staleKeepsLastGoodToolpath() {
        interact(() -> {
            view.setProfile(PROFILE);
            view.setToolpath(GCodeParser.parse("G0 X10\nG1 X20 F600\n"));
        });
        assertFalse(view.isStale());
        interact(() -> view.setToolpath(GCodeParser.parse("G1 X\n")));
        assertTrue(view.isStale());
        assertEquals(2, view.segmentCount(), "last good toolpath retained");
    }

    @Test
    void dragRotatesAroundBedCenter() {
        interact(() -> {
            view.setProfile(PROFILE);
            view.setToolpath(GCodeParser.parse("G1 X10 F600\n"));
        });
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
    void clickOnSegmentEmitsLineSelected() {
        interact(() -> {
            view.setProfile(PROFILE);
            view.setToolpath(GCodeParser.parse("G0 X10\nG1 X20 F600\n"));
        });
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
    void segmentColorDecisions() {
        var light = Preview3DView.PreviewPalette.LIGHT;
        Move rapid = move(MoveKind.RAPID, 0, 0, 10, 0, 1);
        Move cut = move(MoveKind.CUT, 10, 0, 20, 0, 2);
        assertEquals(light.rapid(), Preview3DView.segmentColor(rapid, false, -2.0, light));
        assertEquals(light.outOfBed(), Preview3DView.segmentColor(cut, true, -2.0, light));
        assertTrue(!Preview3DView.segmentColor(cut, false, -2.0, light)
                .equals(Preview3DView.depthColor(-0.5, -2.0)),
                "depth ramp distinguishes depths");
        assertTrue(Preview3DView.depthColor(0, -2.0).getHue()
                > Preview3DView.depthColor(-2, -2.0).getHue(),
                "hue decreases with depth");
    }

    @Test
    void setDarkSwapsPaletteAndRestyles() {
        interact(() -> {
            view.setProfile(PROFILE);
            view.setToolpath(GCodeParser.parse("G1 X10 F600\n"));
        });
        assertEquals(Preview3DView.PreviewPalette.LIGHT, view.palette());
        int rebuilds = view.rebuildCount();
        interact(() -> view.setDark(true));
        assertEquals(Preview3DView.PreviewPalette.DARK, view.palette());
        assertTrue(view.rebuildCount() > rebuilds, "dark switch restyled live");
        interact(() -> view.setDark(false));
        assertEquals(Preview3DView.PreviewPalette.LIGHT, view.palette());
    }

    private static dev.radixhomework.gcodeide.model.parsing.Move move(
            dev.radixhomework.gcodeide.model.parsing.MoveKind kind,
            double x1, double y1, double x2, double y2, int line) {
        return new dev.radixhomework.gcodeide.model.parsing.Move(
                kind, new dev.radixhomework.gcodeide.model.parsing.Position(x1, y1, 0),
                new dev.radixhomework.gcodeide.model.parsing.Position(x2, y2, 0), line,
                kind == dev.radixhomework.gcodeide.model.parsing.MoveKind.CUT ? 600.0 : null, false,
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

    @Test
    void scrollDolliesCamera() {
        interact(() -> view.setProfile(PROFILE));
        double before = view.cameraDistance();
        interact(() -> view.dolly(-20));
        org.junit.jupiter.api.Assertions.assertTrue(view.cameraDistance() > before);
    }

    @Test
    void previewCanShrinkInASplitPane() {
        // regression: SubScene's min size defaults to its current bounds,
        // which froze the SplitPane divider (the preview could never shrink)
        assertTrue(view.node().getMinWidth() == 0 && view.node().getMinHeight() == 0,
                "preview wrapper min sizes are zero so the divider moves freely");
    }

    @Test
    void bedIsVisibleFromTheCamera() {
        interact(() -> view.setProfile(PROFILE));
        // regression: the camera must look at the bed and clip range must cover
        // it — the previous translate-only camera failed both
        assertTrue(view.bedVisibleFromCamera(), "bed inside view cone and clip range");
        assertTrue(view.cameraFarClip() > view.cameraDistance());

        // wheel-zooming out keeps the bed visible
        interact(() -> view.dolly(-1000));
        assertTrue(view.bedVisibleFromCamera());
    }

    @Test
    void panDeltaFollowsCameraAxesAndZoom() {
        // straight ahead: screen-right is world +X, nothing else moves
        Point3D ahead = Preview3DView.panDelta(0, 0, 100, 10, 0);
        org.junit.jupiter.api.Assertions.assertTrue(ahead.getX() > 0);
        org.junit.jupiter.api.Assertions.assertEquals(0, ahead.getY(), 1e-9);
        org.junit.jupiter.api.Assertions.assertEquals(0, ahead.getZ(), 1e-9);

        // after a 90° yaw, screen-right is world -Z
        Point3D yawed = Preview3DView.panDelta(90, 0, 100, 10, 0);
        org.junit.jupiter.api.Assertions.assertEquals(0, yawed.getX(), 1e-9);
        org.junit.jupiter.api.Assertions.assertTrue(yawed.getZ() < 0);

        // dragging down moves content down (-up)
        Point3D down = Preview3DView.panDelta(0, 0, 100, 0, 10);
        org.junit.jupiter.api.Assertions.assertTrue(down.getY() < 0);

        // speed couples to zoom: double distance, double delta
        Point3D twice = Preview3DView.panDelta(-30, -35, 200, 10, 10);
        Point3D once = Preview3DView.panDelta(-30, -35, 100, 10, 10);
        org.junit.jupiter.api.Assertions.assertEquals(twice.getX(), once.getX() * 2, 1e-9);
        org.junit.jupiter.api.Assertions.assertEquals(twice.getY(), once.getY() * 2, 1e-9);
    }

    @Test
    void rightDragPansWithoutRotating() {
        interact(() -> view.setProfile(PROFILE));
        double yawBefore = view.yawAngle();
        double pitchBefore = view.pitchAngle();
        interact(() -> {
            javafx.event.Event.fireEvent(view.subScene(), buttonPress());
            javafx.event.Event.fireEvent(view.subScene(), buttonDrag());
        });
        org.junit.jupiter.api.Assertions.assertTrue(view.panX() != 0 || view.panY() != 0,
                "pan offset changed");
        org.junit.jupiter.api.Assertions.assertEquals(yawBefore, view.yawAngle());
        org.junit.jupiter.api.Assertions.assertEquals(pitchBefore, view.pitchAngle());
    }

    @Test
    void leftDragOrbitsWithoutPanning() {
        interact(() -> view.setProfile(PROFILE));
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
        interact(() -> view.setProfile(PROFILE));
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

    @Test
    void rightDragPansWhenPlatformOmitsButtonFlags() {
        interact(() -> view.setProfile(PROFILE));
        double yawBefore = view.yawAngle();
        // regression for the real-world failure: platform DRAGGED events can
        // omit the button-down flags; dispatch must use the press button.
        // (A TestFX robot-gesture test proved flaky, so the same input shape
        // is asserted deterministically here.)
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
    void zeroLengthSegmentSkipped() {
        assertNull(Preview3DView.segmentBetween(new Point3D(1, 2, 3), new Point3D(1, 2, 3)));
        javafx.scene.shape.Cylinder cylinder =
                Preview3DView.segmentBetween(new Point3D(0, 0, 0), new Point3D(0, 10, 0));
        assertEquals(10.0, cylinder.getHeight(), 1e-9);
    }
}
