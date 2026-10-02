package dev.radixhomework.gcodeide.view;

import dev.radixhomework.gcodeide.model.parsing.ParseResult;
import dev.radixhomework.gcodeide.model.preview.Diagnostics;
import dev.radixhomework.gcodeide.model.preview.ToolpathStats;
import dev.radixhomework.gcodeide.model.preview.ToolpathWarning;
import dev.radixhomework.gcodeide.service.DocumentService;
import dev.radixhomework.gcodeide.service.DocumentService.PathChooser;
import dev.radixhomework.gcodeide.service.DocumentService.SaveDecision;
import dev.radixhomework.gcodeide.service.ParseService;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URL;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import javafx.animation.PauseTransition;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.layout.Region;
import javafx.util.Duration;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * Wires the editor and preview views to the backend services: document
 * actions, debounced live re-parse (300 ms), status bar statistics/warnings,
 * and the window title.
 */
@Slf4j
public class MainWindowController {

    public static final int DEBOUNCE_MS = 300;

    @FXML
    private SplitPane splitPane;
    @FXML
    private Label posLabel;
    @FXML
    private Label statsLabel;
    @FXML
    private Label warningsLabel;

    /** Installed AtlantaFX themes, in menu order. */
    private static final List<atlantafx.base.theme.Theme> THEMES = List.of(
            new atlantafx.base.theme.PrimerLight(),
            new atlantafx.base.theme.PrimerDark(),
            new atlantafx.base.theme.NordLight(),
            new atlantafx.base.theme.NordDark(),
            new atlantafx.base.theme.CupertinoLight(),
            new atlantafx.base.theme.CupertinoDark(),
            new atlantafx.base.theme.Dracula());

    @Getter
    private final GCodeEditorView editor = new GCodeEditorView();
    @Getter
    private final Preview3DView preview = new Preview3DView();
    @Getter
    private final DocumentService documentService;
    @Getter
    private final ParseService parseService = new ParseService();

    private final PauseTransition debounce = new PauseTransition(Duration.millis(DEBOUNCE_MS));
    private final javafx.beans.property.StringProperty title =
            new javafx.beans.property.SimpleStringProperty("G-Code IDE");
    private final Function<String, Optional<Path>> openChooser;
    private final DocumentService.PathChooser previewImageChooser;
    private boolean loadingText;
    @Getter
    private javafx.scene.Parent rootNode;

    /** Loads the FXML with this controller; used by App and by GUI tests. */
    public static MainWindowController create(
            Function<String, Optional<Path>> openChooser, PathChooser saveAsChooser) {
        return create(openChooser, saveAsChooser, suggested -> Optional.empty());
    }

    public static MainWindowController create(
            Function<String, Optional<Path>> openChooser, PathChooser saveAsChooser,
            PathChooser previewImageChooser) {
        try {
            URL fxml = MainWindowController.class.getResource("/view/MainWindow.fxml");
            FXMLLoader loader = new FXMLLoader(fxml);
            MainWindowController controller = new MainWindowController(
                    openChooser,
                    new DocumentService(displayName -> SaveDecision.CANCEL, saveAsChooser),
                    previewImageChooser);
            loader.setController(controller);
            controller.rootNode = loader.load();
            return controller;
        } catch (IOException e) {
            throw new IllegalStateException("cannot load MainWindow.fxml", e);
        }
    }

    /** Alert-backed save prompt for the real app; tests install their own stubs. */
    public static DocumentService.SavePrompt alertPrompt() {
        return displayName -> {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION,
                    "The document " + displayName + " has unsaved changes.",
                    ButtonType.YES, ButtonType.NO, ButtonType.CANCEL);
            alert.setHeaderText("Unsaved changes");
            ButtonType result = alert.showAndWait().orElse(ButtonType.CANCEL);
            if (result == ButtonType.YES) {
                return SaveDecision.SAVE;
            }
            if (result == ButtonType.NO) {
                return SaveDecision.DISCARD;
            }
            return SaveDecision.CANCEL;
        };
    }

    public MainWindowController(
            Function<String, Optional<Path>> openChooser, DocumentService documentService) {
        this(openChooser, documentService, suggested -> Optional.empty());
    }

    public MainWindowController(
            Function<String, Optional<Path>> openChooser, DocumentService documentService,
            PathChooser previewImageChooser) {
        this.openChooser = openChooser;
        this.documentService = documentService;
        this.previewImageChooser = previewImageChooser;
    }

    @FXML
    public void initialize() {
        splitPane.getItems().addAll(editor.node(), preview.node());
        splitPane.setDividerPositions(0.5);

        editor.addCaretListener((line, column) -> posLabel.setText(line + ":" + column));
        editor.addCurrentLineListener(preview::setCurrentLine);
        preview.addLineSelectedListener(editor::gotoLine);

        editor.node().textProperty().addListener((obs, o, n) -> {
            if (!loadingText) {
                documentService.markModified();
                debounce.playFromStart();
            }
            updateTitle();
        });
        debounce.setOnFinished(e -> reparseNow());
        parseService.addParseListener((result, stale) -> {
            preview.setToolpath(result); // the preview keeps the last good scene itself
            refreshStatus();
        });

        documentService.addDocumentListener(this::updateTitle);
        warningsLabel.visibleProperty().bind(warningsLabel.textProperty().isNotEmpty());
        warningsLabel.managedProperty().bind(warningsLabel.visibleProperty());
        updateTitle();
    }

    // -- menu actions ------------------------------------------------------------

    @FXML
    public void onNew() {
        if (documentService.newDocument(editor::getText)) {
            loadText("");
        }
    }

    @FXML
    public void onOpen() {
        Optional<Path> chosen = openChooser.apply("Open G-code file");
        chosen.ifPresent(this::openFile);
    }

    public boolean openFile(Path file) {
        try {
            Optional<String> content = documentService.open(file, editor::getText);
            if (content.isPresent()) {
                loadText(content.get());
                return true;
            }
            return false;
        } catch (UncheckedIOException e) {
            Alert alert = new Alert(Alert.AlertType.WARNING,
                    "Could not open " + file + ":\n" + e.getCause().getMessage(),
                    ButtonType.OK);
            alert.setHeaderText("Open failed");
            alert.showAndWait();
            return false;
        }
    }

    @FXML
    public boolean onSave() {
        boolean saved = documentService.save(editor.getText());
        if (saved) {
            updateTitle();
        }
        return saved;
    }

    @FXML
    public void onSaveAs() {
        documentService.markModified(); // force a path choice even when unmodified
        onSave();
    }

    /** Saves the current preview rendering as PNG or JPG. */
    @FXML
    public void onSavePreviewImage() {
        Optional<Path> chosen = previewImageChooser.choose("preview.png");
        chosen.ifPresent(target -> {
            try {
                ImageExport.write(preview.node(), target);
                log.info("preview image saved to {}", target);
            } catch (IOException | RuntimeException e) {
                log.warn("preview image not saved: {}", e.getMessage());
                Alert alert = new Alert(Alert.AlertType.WARNING,
                        "Could not save the preview image:\n" + e.getMessage(), ButtonType.OK);
                alert.setHeaderText("Save preview image failed");
                alert.showAndWait();
            }
        });
    }

    /** True when closing may proceed (content saved or discarded). */
    public boolean promptSaveBeforeClose() {
        return documentService.maybeSave(editor::getText);
    }

    // -- live parse & status --------------------------------------------------------

    /** Replaces the document text without marking it modified (new/open). */
    public void loadText(String text) {
        loadingText = true;
        try {
            editor.setText(text);
        } finally {
            loadingText = false;
        }
        reparseNow();
    }

    /** Applies a theme by name (App restore path); returns false for unknown names.
     *  Restore does NOT persist — only a settings commit does. */
    public boolean applyTheme(String name) {
        var found = THEMES.stream()
                .filter(t -> t.getName().equals(name))
                .findFirst();
        found.ifPresent(t -> {
            atlantafx.base.theme.ThemeManager.instance().setTheme(t);
            applyDarkFlag(t);
        });
        return found.isPresent();
    }

    private void applyDarkFlag(atlantafx.base.theme.Theme theme) {
        boolean dark = theme.isDarkMode();
        UiTheme.applyDark(editor.node(), dark);
        UiTheme.applyDark(preview.node(), dark);
        preview.setDark(dark);
        if (darkListener != null) {
            darkListener.accept(dark);
        }
    }

    /** Listener for the theme family flag (App restyles the scene root). */
    public void setDarkListener(java.util.function.Consumer<Boolean> listener) {
        this.darkListener = listener;
    }

    private java.util.function.Consumer<Boolean> darkListener = dark -> { };

    /** Persister for the selected theme name (wired by App to the config). */
    public void setThemePersister(java.util.function.Consumer<String> persister) {
        this.themePersister = persister;
    }

    private java.util.function.Consumer<String> themePersister = name -> { };

    /** Commits a theme selection from the settings dialog (apply + persist). */
    private void commitTheme(String name) {
        applyTheme(name);
        // global UA stylesheet: restyles ALL scenes, including the already
        // created main window (ThemeManager.setTheme alone only covers scenes
        // created after it)
        THEMES.stream()
                .filter(t -> t.getName().equals(name))
                .findFirst()
                .ifPresent(t -> javafx.application.Application
                        .setUserAgentStylesheet(t.getUserAgentStylesheet()));
        themePersister.accept(name);
    }

    /** Opens the dedicated settings window (File > Settings...). */
    @FXML
    public void onOpenSettings() {
        lastSettingsDialog = new SettingsDialog(THEMES,
                atlantafx.base.theme.ThemeManager.instance().getTheme() == null
                        ? THEMES.get(0).getName()
                        : atlantafx.base.theme.ThemeManager.instance().getTheme().getName(),
                this::commitTheme);
        lastSettingsDialog.setDark(atlantafx.base.theme.ThemeManager.instance()
                .getTheme() != null
                && atlantafx.base.theme.ThemeManager.instance().getTheme().isDarkMode());
        lastSettingsDialog.show();
    }

    /** The most recently opened settings dialog (tests). */
    public SettingsDialog lastSettingsDialog() {
        return lastSettingsDialog;
    }

    private SettingsDialog lastSettingsDialog;

    public void reparseNow() {
        parseService.parse(editor.getText());
    }

    void refreshStatus() {
        ParseResult result = parseService.lastGood();
        if (result == null) {
            statsLabel.setText("");
            warningsLabel.setText("");
            warningsLabel.setTooltip(null);
            return;
        }
        ToolpathStats stats = Diagnostics.computeStatistics(result);
        List<ToolpathWarning> warnings = Diagnostics.computeWarnings(result);
        String bboxText = stats.cutBBox() == null ? "bbox -"
                : String.format(java.util.Locale.ROOT, "bbox %.1f..%.1f x %.1f..%.1f mm",
                        stats.cutBBox().minX(), stats.cutBBox().maxX(),
                        stats.cutBBox().minY(), stats.cutBBox().maxY());
        statsLabel.setText(String.format(java.util.Locale.ROOT,
                "%s | cut %.1f mm | rapid %.1f mm | est. %.1f min",
                bboxText, stats.cutDistance(), stats.rapidDistance(), stats.estimatedTimeMin()));
        if (warnings.isEmpty()) {
            warningsLabel.setText("");
            warningsLabel.setTooltip(null);
        } else {
            warningsLabel.setText(warnings.size() + " warning(s)");
            warningsLabel.setTooltip(new javafx.scene.control.Tooltip(
                    warnings.stream().map(ToolpathWarning::message)
                            .reduce((a, b) -> a + "\n" + b).orElse("")));
        }
    }

    public String statsText() {
        return statsLabel.getText();
    }

    public String posText() {
        return posLabel.getText();
    }

    public String warningsText() {
        return warningsLabel.getText();
    }

    public String warningsTooltip() {
        return warningsLabel.getTooltip() == null ? "" : warningsLabel.getTooltip().getText();
    }

    private void updateTitle() {
        String marker = documentService.isModified() ? "*" : "";
        title.set(documentService.displayName() + marker + " - G-Code IDE");
    }

    public javafx.beans.property.StringProperty titleProperty() {
        return title;
    }

    public Parent root() {
        return rootNode;
    }
}
