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
import javafx.geometry.Point3D;
import javafx.scene.AmbientLight;
import javafx.scene.Group;
import javafx.scene.PerspectiveCamera;
import javafx.scene.SubScene;
import javafx.scene.control.Label;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.PickResult;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.Box;
import javafx.scene.shape.Cylinder;
import javafx.scene.shape.Shape3D;
import javafx.scene.transform.Rotate;

/**
 * THE toolpath preview: a 3D perspective view of the toolpath against the
 * machine bed. Bed X/Y form the ground plane; G-code Z is world Y, so deeper
 * passes render lower. Cuts are colored by depth ramp, rapids translucent
 * gray, out-of-bed red, the current editor line bright orange. Drag rotates
 * around the bed center, the wheel dollies, and clicking a segment selects
 * its source line (SubScene picking). Uses its own camera via a SubScene.
 */
public class Preview3DView {

    public static final Color BED_COLOR = Color.web("#E8E8E8");
    public static final Color RAPID_COLOR = Color.web("#808080", 0.55);
    public static final Color OUT_OF_BED_COLOR = Color.web("#D62728");
    public static final Color HIGHLIGHT_COLOR = Color.rgb(255, 140, 0);
    public static final double SEGMENT_RADIUS_MM = 0.12;
    /** Diagnostic: log every mouse event the SubScene receives (-Dgcodeide.eventlog=true). */
    private static final boolean EVENT_LOG = Boolean.getBoolean("gcodeide.eventlog");
    /** Drag of this many pixels pans by one camera-distance of world travel. */
    public static final double PAN_PIXELS_PER_DISTANCE = 300.0;
    private static final double SHALLOW_HUE = 210; // blue at Z0
    private static final double DEEP_HUE = 0; // red at the deepest cut

    private final StackPane wrapper = new StackPane();
    private final Group world = new Group();
    private final Group movesGroup = new Group();
    // pan moves content and camera rig together: screen-parallel translation
    private final Group panGroup = new Group();
    private final javafx.scene.transform.Translate panTranslate =
            new javafx.scene.transform.Translate();
    // orbit camera: yawGroup -> pitchGroup -> camera at translateZ = -distance,
    // so the camera always sits on a sphere around the bed center looking at it.
    // Positive pitch places the camera ABOVE the bed (Y = distance·sin(pitch)).
    private final Rotate yaw = new Rotate(-30, Rotate.Y_AXIS);
    private final Rotate pitch = new Rotate(35, Rotate.X_AXIS);
    private final PerspectiveCamera camera = new PerspectiveCamera(true);
    private final Label staleBadge = new Label("  STALE - showing last good toolpath  ");
    private final SubScene subScene;
    private final List<Shape3D> segmentNodes = new ArrayList<>();
    private final List<Move> segmentMoves = new ArrayList<>();
    private final List<IntConsumer> lineListeners = new ArrayList<>();

    private MachineProfile profile;
    private List<Move> moves = List.of();
    private int currentLine = -1;
    private boolean stale;
    private double cameraDistance = 500;
    private Point2D pressPoint;
    private javafx.scene.input.MouseButton pressButton;

    public Preview3DView() {
        world.getChildren().add(movesGroup);
        Group yawGroup = new Group();
        yawGroup.getTransforms().add(yaw);
        Group pitchGroup = new Group();
        pitchGroup.getTransforms().add(pitch);
        camera.setTranslateZ(-cameraDistance);
        // default far clip (100) is shorter than the bed distance: everything
        // would be depth-clipped away
        camera.setNearClip(0.1);
        camera.setFarClip(20000);
        pitchGroup.getChildren().add(camera);
        yawGroup.getChildren().add(pitchGroup);
        panGroup.getTransforms().add(panTranslate);
        panGroup.getChildren().addAll(world, yawGroup, new AmbientLight(Color.WHITE),
                new javafx.scene.DirectionalLight(Color.WHITE));
        Group root = new Group(panGroup);
        subScene = new SubScene(root, 100, 100, true,
                javafx.scene.SceneAntialiasing.BALANCED);
        subScene.setCamera(camera);
        subScene.widthProperty().bind(wrapper.widthProperty());
        subScene.heightProperty().bind(wrapper.heightProperty());
        subScene.addEventFilter(MouseEvent.MOUSE_PRESSED, this::onPressed);
        subScene.addEventFilter(MouseEvent.MOUSE_DRAGGED, this::onDragged);
        subScene.addEventFilter(MouseEvent.MOUSE_RELEASED, this::onReleased);
        subScene.addEventFilter(ScrollEvent.ANY, this::onScroll);
        subScene.addEventFilter(javafx.scene.input.ContextMenuEvent.CONTEXT_MENU_REQUESTED,
                event -> event.consume()); // right button pans; no menu
        if (EVENT_LOG) {
            subScene.addEventFilter(MouseEvent.ANY, event -> System.out.printf(
                    "EVT %-28s button=%-9s primary=%-5s secondary=%-5s at %.0f,%.0f%n",
                    event.getEventType(), event.getButton(), event.isPrimaryButtonDown(),
                    event.isSecondaryButtonDown(), event.getX(), event.getY()));
        }
        fitCameraDistance();

        staleBadge.setStyle("-fx-background-color: #C00000; -fx-text-fill: white; -fx-font-weight: bold;");
        staleBadge.setVisible(false);
        staleBadge.setMouseTransparent(true);
        StackPane.setAlignment(staleBadge, javafx.geometry.Pos.TOP_LEFT);
        wrapper.getChildren().addAll(subScene, staleBadge);
    }

    /** The JavaFX node to place in a layout. */
    public StackPane node() {
        return wrapper;
    }

    /** The SubScene, for tests firing events. */
    public SubScene subScene() {
        return subScene;
    }

    // -- pure color decisions (shared, headless-testable) ---------------------------

    /** Hue ramp blue (Z0) to red (deepest). */
    public static Color depthColor(double z, double deepestCutZ) {
        if (deepestCutZ >= 0) {
            return Color.hsb(SHALLOW_HUE, 0.78, 0.71);
        }
        double t = Math.max(0.0, Math.min(1.0, z / deepestCutZ));
        return Color.hsb(SHALLOW_HUE + (DEEP_HUE - SHALLOW_HUE) * t, 0.78, 0.71);
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

    /** Base color for a segment: red out-of-bed, gray rapid, depth ramp for cuts. */
    public static Color segmentColor(Move move, boolean outOfBed, double deepestCutZ) {
        if (outOfBed) {
            return OUT_OF_BED_COLOR;
        }
        if (move.kind() == MoveKind.RAPID) {
            return RAPID_COLOR;
        }
        return depthColor(move.end().z(), deepestCutZ);
    }

    // -- world mapping ----------------------------------------------------------------

    /**
     * Bed coordinates to centered 3D world coordinates: bed X → X (centered),
     * G-code Z → Y (negative down: deeper is lower), bed Y → −Z (centered) —
     * the negation keeps the mapping orientation-preserving so a top-down
     * view shows bed +Y toward the top of the screen, like the canonical 2D
     * orientation, and arcs curve the same way as in bed coordinates.
     */
    public static Point3D toWorld(double x, double y, double z, MachineProfile profile) {
        return new Point3D(x - profile.bedX() / 2, z, profile.bedY() / 2 - y);
    }

    /** A cylinder from {@code from} to {@code to}, or null for zero-length segments. */
    static Cylinder segmentBetween(Point3D from, Point3D to) {
        Point3D direction = to.subtract(from);
        double length = direction.magnitude();
        if (length < 1e-9) {
            return null;
        }
        Cylinder cylinder = new Cylinder(SEGMENT_RADIUS_MM, length);
        Point3D mid = from.midpoint(to);
        cylinder.setTranslateX(mid.getX());
        cylinder.setTranslateY(mid.getY());
        cylinder.setTranslateZ(mid.getZ());
        Point3D axis = Rotate.Y_AXIS.crossProduct(direction);
        double angle = Math.toDegrees(Math.acos(direction.normalize().dotProduct(Rotate.Y_AXIS)));
        if (axis.magnitude() > 1e-9) {
            // pivot at the local origin: the cylinder is already translated to the midpoint
            cylinder.getTransforms().add(new Rotate(angle, axis));
        }
        return cylinder;
    }

    // -- profile & toolpath ---------------------------------------------------------------

    public void setProfile(MachineProfile profile) {
        this.profile = profile;
        rebuild();
        fitCameraDistance();
    }

    public MachineProfile profile() {
        return profile;
    }

    /** Shows the parsed toolpath, or keeps the last good scene when unparseable. */
    public void setToolpath(ParseResult result) {
        if (result.hasErrors()) {
            setStale(true);
            return;
        }
        moves = result.moves();
        setStale(false);
        rebuild();
    }

    public boolean isStale() {
        return stale;
    }

    public int segmentCount() {
        return moves.size();
    }

    /** Nodes in the 3D scene graph for the toolpath: the bed box plus one cylinder per segment. */
    public int renderedNodes() {
        return movesGroup.getChildren().size();
    }

    public int highlightCount() {
        return currentLine <= 0 ? 0
                : (int) segmentMoves.stream()
                        .filter(m -> m != null && m.line() == currentLine).count();
    }

    /** The rendered node for the first segment of a source line (tests, picking support). */
    public Shape3D segmentNodeForLine(int line) {
        for (int i = 0; i < segmentMoves.size(); i++) {
            Move move = segmentMoves.get(i);
            if (move != null && move.line() == line) {
                return segmentNodes.get(i);
            }
        }
        return null;
    }

    /**
     * World-space pan delta for a right-drag of ({@code dx}, {@code dy})
     * pixels: moves along the camera's current right/up axes, scaled by the
     * camera distance so speed couples to zoom (grab semantics: content
     * follows the mouse).
     */
    public static Point3D panDelta(double yawDeg, double pitchDeg, double distance,
            double dxPixels, double dyPixels) {
        double yaw = Math.toRadians(yawDeg);
        double pitch = Math.toRadians(pitchDeg);
        // camera local axes in world space (matches the orbit group order)
        Point3D right = new Point3D(Math.cos(yaw), 0, -Math.sin(yaw));
        Point3D up = new Point3D(Math.sin(yaw) * Math.sin(pitch), Math.cos(pitch),
                Math.cos(yaw) * Math.sin(pitch));
        double scale = distance / PAN_PIXELS_PER_DISTANCE;
        return right.multiply(dxPixels * scale).subtract(up.multiply(dyPixels * scale));
    }

    public double panX() {
        return panTranslate.getX();
    }

    public double panY() {
        return panTranslate.getY();
    }

    public double panZ() {
        return panTranslate.getZ();
    }

    public double yawAngle() {
        return yaw.getAngle();
    }

    public double pitchAngle() {
        return pitch.getAngle();
    }

    public double cameraDistance() {
        return cameraDistance;
    }

    public double cameraFarClip() {
        return camera.getFarClip();
    }

    /**
     * Camera world position for the orbit (yaw°, pitch°, distance): rotation of
     * the local offset (0, 0, -distance). The view axis always points at the
     * bed center (origin).
     */
    public static Point3D cameraPosition(double yawDeg, double pitchDeg, double distance) {
        double yaw = Math.toRadians(yawDeg);
        double pitch = Math.toRadians(pitchDeg);
        return new Point3D(
                -distance * Math.cos(pitch) * Math.sin(yaw),
                distance * Math.sin(pitch),
                -distance * Math.cos(pitch) * Math.cos(yaw));
    }

    /** True when the bed fits inside the camera's view cone and clip range. */
    public boolean bedVisibleFromCamera() {
        if (profile == null) {
            return false;
        }
        double distance = cameraDistance;
        if (cameraFarClip() <= distance) {
            return false;
        }
        Point3D position = cameraPosition(yaw.getAngle(), pitch.getAngle(), distance);
        Point3D forward = position.normalize().multiply(-1); // orbit: looks at origin
        double halfFovRad = Math.toRadians(14); // ~30° vertical FOV with margin
        for (double x : new double[] {-profile.bedX() / 2, profile.bedX() / 2}) {
            for (double z : new double[] {-profile.bedY() / 2, profile.bedY() / 2}) {
                for (double y : new double[] {0, -5}) {
                    Point3D corner = new Point3D(x, y, z).subtract(position).normalize();
                    if (Math.acos(corner.dotProduct(forward)) > halfFovRad) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    // -- sync --------------------------------------------------------------------------

    public void setCurrentLine(int line) {
        this.currentLine = line;
        restyle();
    }

    public void addLineSelectedListener(IntConsumer listener) {
        lineListeners.add(listener);
    }

    // -- rendering ------------------------------------------------------------------------

    private void rebuild() {
        movesGroup.getChildren().clear();
        segmentNodes.clear();
        segmentMoves.clear();
        if (profile == null) {
            return;
        }
        Box bed = new Box(profile.bedX(), 1.0, profile.bedY());
        bed.setMaterial(new PhongMaterial(BED_COLOR));
        bed.setTranslateY(-0.5); // top surface at Y = 0
        movesGroup.getChildren().add(bed);
        segmentNodes.add(bed); // placeholder keeps indices aligned; not a segment
        segmentMoves.add(null);

        double deepest = deepestCutZ(moves);
        for (Move move : moves) {
            Point3D from = toWorld(move.start().x(), move.start().y(), move.start().z(), profile);
            Point3D to = toWorld(move.end().x(), move.end().y(), move.end().z(), profile);
            Cylinder segment = segmentBetween(from, to);
            if (segment == null) {
                continue;
            }
            segment.setMaterial(new PhongMaterial(
                    segmentColor(move, Diagnostics.moveOutOfBed(move, profile), deepest)));
            segment.setUserData(move.line());
            movesGroup.getChildren().add(segment);
            segmentNodes.add(segment);
            segmentMoves.add(move);
        }
        restyle();
    }

    private void restyle() {
        double deepest = deepestCutZ(moves);
        for (int i = 0; i < segmentMoves.size(); i++) {
            Move move = segmentMoves.get(i);
            if (move == null) {
                continue; // bed placeholder
            }
            Shape3D node = segmentNodes.get(i);
            PhongMaterial material = new PhongMaterial(segmentColor(move,
                    profile != null && Diagnostics.moveOutOfBed(move, profile), deepest));
            if (move.line() == currentLine) {
                material.setDiffuseColor(HIGHLIGHT_COLOR);
                material.setSpecularColor(Color.ORANGE);
            }
            node.setMaterial(material);
        }
    }

    private void setStale(boolean value) {
        stale = value;
        staleBadge.setVisible(value);
    }

    // -- interaction ------------------------------------------------------------------------

    private void onPressed(MouseEvent event) {
        pressPoint = new Point2D(event.getX(), event.getY());
        // dispatch drags on the button that started the gesture: the
        // isXxxButtonDown flags are not reliable in platform DRAGGED events
        pressButton = event.getButton();
        event.consume();
    }

    private void onDragged(MouseEvent event) {
        if (pressPoint == null) {
            return;
        }
        // harden for real-world event shapes: the initiating button decides,
        // with the secondary flag and middle button as additional pan paths
        boolean pan = pressButton == javafx.scene.input.MouseButton.SECONDARY
                || pressButton == javafx.scene.input.MouseButton.MIDDLE
                || event.isSecondaryButtonDown();
        if (pan) {
            Point3D delta = panDelta(yaw.getAngle(), pitch.getAngle(), cameraDistance,
                    event.getX() - pressPoint.getX(), event.getY() - pressPoint.getY());
            panTranslate.setX(panTranslate.getX() + delta.getX());
            panTranslate.setY(panTranslate.getY() + delta.getY());
            panTranslate.setZ(panTranslate.getZ() + delta.getZ());
        } else if (pressButton == javafx.scene.input.MouseButton.PRIMARY
                || event.isPrimaryButtonDown()) {
            yaw.setAngle(yaw.getAngle() - (event.getX() - pressPoint.getX()) * 0.4);
            pitch.setAngle(Math.max(-89,
                    Math.min(89, pitch.getAngle() + (event.getY() - pressPoint.getY()) * 0.4)));
        }
        pressPoint = new Point2D(event.getX(), event.getY());
        event.consume();
    }

    private void onReleased(MouseEvent event) {
        if (pressPoint == null) {
            return;
        }
        boolean movedFar = pressPoint.distance(event.getX(), event.getY()) > 4.0;
        boolean wasPrimary = pressButton == javafx.scene.input.MouseButton.PRIMARY;
        pressPoint = null;
        pressButton = null;
        if (!movedFar && wasPrimary) {
            emitPickedLine(event.getPickResult());
        }
        event.consume();
    }

    private void emitPickedLine(PickResult pickResult) {
        if (pickResult == null || !(pickResult.getIntersectedNode() instanceof Shape3D node)) {
            return;
        }
        if (node.getUserData() instanceof Integer line && line > 0) {
            lineListeners.forEach(l -> l.accept(line));
        }
    }

    private void onScroll(ScrollEvent event) {
        dolly(event.getDeltaY());
        event.consume();
    }

    /** Wheel zoom: positive deltaY moves in, negative moves out. */
    public void dolly(double deltaY) {
        double factor = deltaY > 0 ? 0.9 : 1.0 / 0.9;
        cameraDistance = Math.max(50, Math.min(5000, cameraDistance * factor));
        placeCamera();
    }

    /** Frames the bed: far enough that its corners fit the ~30° vertical FOV. */
    private void fitCameraDistance() {
        if (profile != null) {
            cameraDistance = Math.max(profile.bedX(), profile.bedY()) * 2.5;
        }
        placeCamera();
    }

    private void placeCamera() {
        camera.setTranslateZ(-cameraDistance);
    }
}
