package dev.radixhomework.gcodeide.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.radixhomework.gcodeide.service.DocumentService.SaveDecision;
import dev.radixhomework.gcodeide.service.ProfileService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationTest;

/** Integration: controller + services + views wired, including the demo.nc end-to-end. */
class MainWindowIntegrationTest extends ApplicationTest {

    @TempDir
    Path dir;

    private ProfileService profileService;
    private MainWindowController controller;
    private Path saveTarget;

    @Override
    public void start(Stage stage) throws IOException {
        Path userProfiles = Files.createDirectories(dir.resolve("profiles"));
        Files.writeString(userProfiles.resolve("wide.yaml"), """
                name: Wide Mill
                bed: { x: 400.0, y: 200.0, z: 45.0 }
                feeds: { max_cut: 800.0, max_rapid: 1000.0 }
                safe_z: 5.0
                """);
        profileService = new ProfileService(userProfiles, dir.resolve("config.yaml"));
        saveTarget = dir.resolve("saved.nc");
        controller = MainWindowController.create(profileService,
                title -> Optional.empty(),
                suggested -> Optional.of(saveTarget));
        // keep the cancel-only default prompt: no real modal can ever block a test
        stage.setScene(new javafx.scene.Scene(controller.root(), 900, 600));
        stage.show();
    }

    // --- task 7.2: debounced live re-parse ----------------------------------------

    @Test
    void debounceUpdatesPreviewAndStatus() throws Exception {
        interact(() -> controller.loadText("G0 X10\n"));
        waitUntil(() -> controller.getPreview().segmentCount() == 1);
        interact(() -> controller.getEditor().setText("G0 X10\nG1 X20 F600\n"));
        waitUntil(() -> controller.getPreview().segmentCount() == 2);
        waitUntil(() -> controller.statsText().contains("cut 10.0 mm"));
    }

    @Test
    void syntaxErrorSetsStaleIndicator() throws Exception {
        interact(() -> controller.loadText("G0 X10\nG1 X20 F600\n"));
        waitUntil(() -> controller.getPreview().segmentCount() == 2);
        interact(() -> controller.getEditor().setText("G0 X10\nG1 X\n"));
        waitUntil(controller.getPreview()::isStale);
        assertEquals(2, controller.getPreview().segmentCount());
    }

    // --- task 7.3: status bar & profile selector -------------------------------------

    @Test
    void statisticsRenderedForKnownProgram() throws Exception {
        interact(() -> controller.loadText("G1 X10 F600\n"));
        waitUntil(() -> controller.statsText().contains("bbox"));
        String stats = controller.statsText();
        assertTrue(stats.contains("bbox 0.0..10.0 x 0.0..0.0 mm"), stats);
        assertTrue(stats.contains("cut 10.0 mm"), stats);
        assertTrue(stats.contains("rapid 0.0 mm"), stats);
        assertTrue(stats.contains("est. 0.0 min"), stats); // 10 mm at 600 mm/min
    }

    @Test
    void caretPositionShownInStatus() {
        interact(() -> controller.loadText("G1 X10\nG1 X20\n"));
        interact(() -> controller.getEditor().gotoLine(2));
        // gotoLine selects the paragraph, so the caret reports the selection end
        assertTrue(controller.posText().startsWith("2:"));
    }

    @Test
    void profileSwitchRecomputesWarningsAndBed() throws Exception {
        interact(() -> controller.loadText("G1 X350 F600\n"));
        waitUntil(() -> "1 warning(s)".equals(controller.warningsText()));
        assertTrue(controller.warningsTooltip().contains("300"));
        interact(() -> controller.selectProfile("Wide Mill"));
        assertEquals("", controller.warningsText());
        assertEquals(400.0, controller.getPreview().drawnBedX());
    }

    // --- task 7.4: config persistence -----------------------------------------------

    @Test
    void selectedProfileActiveAfterConfigReload() throws IOException {
        interact(() -> controller.selectProfile("Wide Mill"));
        ProfileService reloaded = new ProfileService(
                dir.resolve("profiles"), dir.resolve("config.yaml"));
        assertEquals("Wide Mill", reloaded.active().name());
        Map<String, Object> config = reloaded.loadConfig();
        assertEquals("Wide Mill", config.get("active_profile"));
    }

    @Test
    void geometryRoundTripsThroughConfig() {
        interact(() -> {
            Stage stage = new Stage();
            stage.setWidth(777);
            stage.setHeight(555);
            dev.radixhomework.gcodeide.App.saveGeometry(stage, profileService);
            Stage restored = new Stage();
            dev.radixhomework.gcodeide.App.restoreGeometry(restored, profileService);
            assertEquals(777, restored.getWidth());
            assertEquals(555, restored.getHeight());
            stage.close();
            restored.close();
        });
    }

    // --- task 7.1: document lifecycle through the controller ---------------------------

    @Test
    void saveClearsModifiedThroughController() {
        interact(() -> controller.getEditor().setText("G1 X10 F600\n"));
        assertTrue(controller.getDocumentService().isModified());
        interact(() -> assertTrue(controller.onSave()));
        assertEquals("G1 X10 F600\n", read(saveTarget));
        assertFalse(controller.getDocumentService().isModified());
        assertTrue(controller.titleProperty().get().startsWith("saved.nc"));
    }

    @Test
    void cancelPromptKeepsContent() {
        interact(() -> controller.getEditor().setText("G1 X10 F600\n"));
        controller.getDocumentService().setPrompt(name -> SaveDecision.CANCEL);
        interact(() -> controller.onNew());
        assertEquals("G1 X10 F600\n", controller.getEditor().getText());
        assertTrue(controller.getDocumentService().isModified());
    }

    @Test
    void openFileShowsContentAndTitle() throws IOException {
        Path file = dir.resolve("part.nc");
        Files.writeString(file, "G0 X5\nG1 X10 F600\n");
        interact(() -> assertTrue(controller.openFile(file)));
        assertEquals("G0 X5\nG1 X10 F600\n", controller.getEditor().getText());
        assertTrue(controller.titleProperty().get().contains("part.nc"));
    }

    // --- task 8.1: demo.nc end to end ----------------------------------------------------

    @Test
    void demoProgramEndToEnd() throws Exception {
        Path demo = dir.resolve("demo.nc");
        try (var in = getClass().getResourceAsStream("/assets/examples/demo.nc")) {
            Files.copy(in, demo, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        interact(() -> assertTrue(controller.openFile(demo)));
        assertFalse(controller.getPreview().isStale());
        waitUntil(() -> controller.getPreview().segmentCount() > 50);

        String stats = controller.statsText();
        assertTrue(stats.contains("bbox 10.0..75.0 x 10.0..30.0 mm"), stats);
        assertTrue(stats.contains("cut 118.0 mm"), stats);
        assertTrue(stats.contains("rapid 664.4 mm"), stats);
        assertTrue(stats.contains("est. 0.9 min"), stats);

        assertEquals("1 warning(s)", controller.warningsText());
        assertTrue(controller.warningsTooltip().contains("300x180"), controller.warningsTooltip());
        assertTrue(controller.warningsTooltip().contains("16"), controller.warningsTooltip());

        interact(() -> controller.getEditor().gotoLine(8)); // the G2 arc line
        waitUntil(() -> controller.getPreview().highlightCount() > 1);
    }

    // -- helpers -----------------------------------------------------------------

    private static void waitUntil(java.util.function.BooleanSupplier condition)
            throws InterruptedException {
        for (int i = 0; i < 200; i++) { // up to 10 s
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("condition not met in time");
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
