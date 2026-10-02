package dev.radixhomework.gcodeide.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.radixhomework.gcodeide.service.ConfigService;
import dev.radixhomework.gcodeide.service.DocumentService.SaveDecision;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationTest;

/** Integration: controller + services + views wired, including the demo.nc end-to-end. */
class MainWindowIntegrationTest extends ApplicationTest {

    @TempDir
    Path dir;

    private ConfigService configService;
    private MainWindowController controller;
    private Path saveTarget;
    private final java.util.concurrent.atomic.AtomicReference<Path> previewImageTarget =
            new java.util.concurrent.atomic.AtomicReference<>();

    @Override
    public void start(Stage stage) throws IOException {
        configService = new ConfigService(dir.resolve("config.yaml"));
        saveTarget = dir.resolve("saved.nc");
        controller = MainWindowController.create(
                title -> Optional.empty(),
                suggested -> Optional.of(saveTarget),
                suggested -> Optional.ofNullable(previewImageTarget.get()));
        // keep the cancel-only default prompt: no real modal can ever block a test
        stage.setScene(new javafx.scene.Scene(controller.root(), 900, 600));
        stage.show();
    }

    // --- debounced live re-parse -----------------------------------------------

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

    // --- status bar ---------------------------------------------------------------

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
    void missingFeedWarningShown() throws Exception {
        interact(() -> controller.loadText("G1 X10\n"));
        waitUntil(() -> "1 warning(s)".equals(controller.warningsText()));
        assertTrue(controller.warningsTooltip().contains("without a commanded feed"));
    }

    @Test
    void caretPositionShownInStatus() {
        interact(() -> controller.loadText("G1 X10\nG1 X20\n"));
        interact(() -> controller.getEditor().gotoLine(2));
        // gotoLine selects the paragraph, so the caret reports the selection end
        assertTrue(controller.posText().startsWith("2:"));
    }

    // --- config persistence ---------------------------------------------------------

    @Test
    void geometryRoundTripsThroughConfig() {
        interact(() -> {
            Stage stage = new Stage();
            stage.setWidth(777);
            stage.setHeight(555);
            dev.radixhomework.gcodeide.App.saveGeometry(stage, configService);
            Stage restored = new Stage();
            dev.radixhomework.gcodeide.App.restoreGeometry(restored, configService);
            assertEquals(777, restored.getWidth());
            assertEquals(555, restored.getHeight());
            stage.close();
            restored.close();
        });
    }

    // --- document lifecycle through the controller ------------------------------------

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

    // --- 3D preview integration ---------------------------------------------------------

    @Test
    void previewIs3dAndSyncsBothDirections() throws Exception {
        interact(() -> controller.loadText("G0 X10\nG1 X20 F600\n"));
        waitUntil(() -> controller.getPreview().segmentCount() == 2);
        assertEquals(2, controller.getPreview().renderedNodes(), "one cylinder per move");
        assertFalse(controller.getPreview().isStale());

        // editor -> preview highlight
        interact(() -> controller.getEditor().gotoLine(2));
        waitUntil(() -> controller.getPreview().highlightCount() == 1);

        // preview -> editor: click the line-2 segment (pick result)
        List<Integer> selected = new ArrayList<>();
        controller.getPreview().addLineSelectedListener(selected::add);
        javafx.scene.shape.Shape3D segment = controller.getPreview().segmentNodeForLine(2);
        interact(() -> {
            javafx.scene.input.PickResult pick = new javafx.scene.input.PickResult(
                    segment, new javafx.geometry.Point3D(15, 0, 0), 1.0);
            javafx.event.Event.fireEvent(controller.getPreview().subScene(), pickPress(pick));
            javafx.event.Event.fireEvent(controller.getPreview().subScene(), pickRelease(pick));
        });
        assertEquals(List.of(2), selected);
        interact(() -> org.junit.jupiter.api.Assertions.assertEquals(2,
                controller.getEditor().node().getCurrentParagraph() + 1));
    }

    // --- save preview image ------------------------------------------------------------

    @Test
    void savePreviewImageActionWritesFile() throws Exception {
        Path target = dir.resolve("shot.png");
        previewImageTarget.set(target);
        interact(() -> controller.loadText("G1 X10 F600\n"));
        waitUntil(() -> controller.getPreview().segmentCount() == 1);
        interact(() -> controller.onSavePreviewImage());
        assertTrue(java.nio.file.Files.exists(target), "image written via the menu action");
        java.awt.image.BufferedImage image = javax.imageio.ImageIO.read(target.toFile());
        org.junit.jupiter.api.Assertions.assertNotNull(image, "valid image file");
        assertTrue(image.getWidth() > 0 && image.getHeight() > 0);
    }

    // --- settings dialog ---------------------------------------------------------------

    @Test
    void settingsApplyCommitsPersistsAndStaysOpen() {
        interact(() -> controller.setThemePersister(
                name -> configService.updateConfig(c -> c.put("theme", name))));
        interact(() -> controller.applyTheme("Primer Light"));
        var dialog = new java.util.concurrent.atomic.AtomicReference<SettingsDialog>();
        interact(() -> {
            controller.onOpenSettings();
            dialog.set(controller.lastSettingsDialog());
        });
        assertTrue(dialog.get().isShowing());

        // stage without committing: nothing applied, nothing persisted
        interact(() -> dialog.get().themeComboForTest().getSelectionModel().select("Nord Dark"));
        assertEquals("Primer Light",
                atlantafx.base.theme.ThemeManager.instance().getTheme().getName());
        assertEquals(null, configService.loadConfig().get("theme"));

        // Apply: commits + persists, dialog stays open
        interact(() -> dialog.get().buttonFor(javafx.scene.control.ButtonType.APPLY).fire());
        assertEquals("Nord Dark",
                atlantafx.base.theme.ThemeManager.instance().getTheme().getName());
        assertEquals("Nord Dark", configService.loadConfig().get("theme"));
        assertTrue(dialog.get().isShowing(), "Apply keeps the window open");
    }

    @Test
    void settingsOkCommitsAndCloses() {
        interact(() -> controller.applyTheme("Primer Light"));
        var closed = new java.util.concurrent.atomic.AtomicBoolean();
        List<String> persisted = new ArrayList<>();
        var dialog = new java.util.concurrent.atomic.AtomicReference<SettingsDialog>();
        interact(() -> dialog.set(dialogWithCloseFlag(closed, name -> {
            controller.applyTheme(name); // real apply path
            persisted.add(name);
        })));
        interact(() -> dialog.get().themeComboForTest().getSelectionModel().select("Dracula"));
        interact(() -> dialog.get().buttonFor(javafx.scene.control.ButtonType.OK).fire());
        assertEquals("Dracula",
                atlantafx.base.theme.ThemeManager.instance().getTheme().getName());
        assertEquals(List.of("Dracula"), persisted);
        assertTrue(closed.get(), "OK closes the window");
    }

    @Test
    void settingsCancelDiscardsUncommittedEdits() {
        interact(() -> controller.applyTheme("Primer Light"));
        var closed = new java.util.concurrent.atomic.AtomicBoolean();
        List<String> persisted = new ArrayList<>();
        var dialog = new java.util.concurrent.atomic.AtomicReference<SettingsDialog>();
        interact(() -> dialog.set(dialogWithCloseFlag(closed,
                name -> persisted.add(name))));
        interact(() -> dialog.get().themeComboForTest().getSelectionModel().select("Dracula"));
        interact(() -> dialog.get().buttonFor(javafx.scene.control.ButtonType.CANCEL).fire());
        // staged edit discarded: previous theme still active, nothing persisted
        assertEquals("Primer Light",
                atlantafx.base.theme.ThemeManager.instance().getTheme().getName());
        assertEquals(List.of(), persisted);
        assertTrue(closed.get(), "Cancel closes the window");
    }

    @Test
    void restorePathAppliesWithoutPersistingAndRejectsUnknown() {
        interact(() -> assertTrue(controller.applyTheme("Primer Dark")));
        assertEquals("Primer Dark",
                atlantafx.base.theme.ThemeManager.instance().getTheme().getName());
        assertEquals(null, configService.loadConfig().get("theme"));
        interact(() -> assertTrue(!controller.applyTheme("No Such Theme")));
        assertEquals("Primer Dark",
                atlantafx.base.theme.ThemeManager.instance().getTheme().getName());
    }

    @Test
    void darkFlagTogglesEditorAndPreviewWithTheme() {
        interact(() -> assertTrue(controller.applyTheme("Primer Dark")));
        assertTrue(dev.radixhomework.gcodeide.view.UiTheme.isDark(controller.getEditor().node()),
                "editor carries the dark flag");
        assertEquals(Preview3DView.PreviewPalette.DARK, controller.getPreview().palette());

        interact(() -> assertTrue(controller.applyTheme("Primer Light")));
        assertTrue(!dev.radixhomework.gcodeide.view.UiTheme.isDark(controller.getEditor().node()));
        assertEquals(Preview3DView.PreviewPalette.LIGHT, controller.getPreview().palette());
    }

    @Test
    void restartRequiredRowCarriesTheMention() {
        var row = (javafx.scene.layout.VBox) SettingsDialog.restartRequiredRow(
                new javafx.scene.control.Label("setting"));
        assertTrue(row.getChildren().stream()
                        .anyMatch(child -> child instanceof javafx.scene.control.Label label
                                && label.getText().equals(SettingsDialog.RESTART_NOTE)),
                "the restart mention is part of the wrapped row");
    }

    // --- demo.nc end to end ---------------------------------------------------------------

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
        // cut-only estimate: 118 mm at 600 mm/min
        assertTrue(stats.contains("est. 0.2 min"), stats);

        // machine-profile-free: no out-of-bed / feed-cap warnings; the demo
        // commands a feed on every cut, so it is warning-free
        assertEquals("", controller.warningsText());

        interact(() -> controller.getEditor().gotoLine(8)); // the G2 arc line
        waitUntil(() -> controller.getPreview().highlightCount() > 1);
    }

    // -- helpers -----------------------------------------------------------------

    private SettingsDialog dialogWithCloseFlag(java.util.concurrent.atomic.AtomicBoolean closed,
            java.util.function.Consumer<String> commit) {
        // closeWindow is overridden: real Stage.hide() hangs in the test JVM
        // (Platform.startup environment), while the real app closes normally
        return new SettingsDialog(
                List.of(new atlantafx.base.theme.PrimerLight(),
                        new atlantafx.base.theme.PrimerDark(),
                        new atlantafx.base.theme.Dracula()),
                "Primer Light", commit) {
            @Override
            void closeWindow() {
                closed.set(true);
            }
        };
    }

    private static javafx.scene.input.MouseEvent pickPress(javafx.scene.input.PickResult pick) {
        return new javafx.scene.input.MouseEvent(javafx.scene.input.MouseEvent.MOUSE_PRESSED,
                5, 5, 0, 0, javafx.scene.input.MouseButton.PRIMARY, 1,
                false, false, false, false, true, false, false, false, false, false, pick);
    }

    private static javafx.scene.input.MouseEvent pickRelease(javafx.scene.input.PickResult pick) {
        return new javafx.scene.input.MouseEvent(javafx.scene.input.MouseEvent.MOUSE_RELEASED,
                5, 5, 0, 0, javafx.scene.input.MouseButton.PRIMARY, 1,
                false, false, false, false, false, false, false, false, false, true, pick);
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
