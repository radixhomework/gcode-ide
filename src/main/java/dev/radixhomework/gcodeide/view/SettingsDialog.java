package dev.radixhomework.gcodeide.view;

import java.util.List;
import java.util.function.Consumer;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * The dedicated settings window (change settings-dialog): a tabbed window
 * with staged semantics — edits stage while open, nothing takes effect until
 * Apply (commit, stay open) or OK (commit, close); Cancel discards uncommitted
 * changes. Settings that cannot apply live are wrapped with a visible
 * "takes effect after restart" mention (design D4) and their commit hooks
 * persist only.
 *
 * <p>Built on a plain {@link Stage} rather than {@code Dialog}: Dialog's
 * close path is unreliable on this stack (close() no-ops), while Stage
 * show/hide is the same proven path as the main window. The button bar sits
 * in the lower-right corner with Cancel nearest to the window borders.
 */
public class SettingsDialog {

    public static final String RESTART_NOTE =
            "Takes effect after the application is closed and reopened.";

    private final Stage stage = new Stage();
    private final BorderPane layout;
    private final ComboBox<String> themeCombo = new ComboBox<>();
    private final Button applyButton = new Button("Apply");
    private final Button okButton = new Button("OK");
    private final Button cancelButton = new Button("Cancel");

    /**
     * @param themes          the installed themes (menu order)
     * @param committedTheme  the currently committed theme name
     * @param themeCommit     applies and persists a theme by name
     */
    public SettingsDialog(List<atlantafx.base.theme.Theme> themes, String committedTheme,
            Consumer<String> themeCommit) {
        stage.setTitle("Settings");
        stage.setResizable(true);

        themeCombo.getItems().addAll(themes.stream()
                .map(atlantafx.base.theme.Theme::getName).toList());
        themeCombo.getSelectionModel().select(committedTheme);

        // staged: the combo only records a pending value; commit happens on Apply/OK
        var appearanceGrid = new GridPane();
        appearanceGrid.setHgap(10);
        appearanceGrid.setVgap(10);
        appearanceGrid.add(new Label("Theme:"), 0, 0);
        appearanceGrid.add(themeCombo, 1, 0);
        GridPane.setHgrow(themeCombo, Priority.ALWAYS);

        var tabs = new TabPane(new Tab("Appearance", appearanceGrid));
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        // lower-right corner: Cancel nearest to the right and bottom borders
        var spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        var buttonBar = new HBox(8, spacer, applyButton, okButton, cancelButton);
        buttonBar.setPadding(new javafx.geometry.Insets(8, 12, 12, 12));

        this.layout = new BorderPane(tabs, null, null, buttonBar, null);
        var scene = new javafx.scene.Scene(layout);
        scene.getStylesheets().add(getClass().getResource("/app.css").toExternalForm());
        stage.setScene(scene);
        stage.setWidth(640);
        stage.setHeight(440);
        stage.setMinWidth(520);
        stage.setMinHeight(380);

        applyButton.setOnAction(event -> {
            themeCommit.accept(themeCombo.getValue()); // commit, stay open
            event.consume();
        });
        okButton.setOnAction(event -> {
            themeCommit.accept(themeCombo.getValue()); // commit, close
            closeWindow();
        });
        cancelButton.setOnAction(event -> closeWindow()); // discard pending
    }

    /** Shows the non-modal settings window. */
    public void show() {
        stage.show();
    }

    public boolean isShowing() {
        return stage.isShowing();
    }

    /** The theme combo, for tests driving staged edits. */
    public ComboBox<String> themeComboForTest() {
        return themeCombo;
    }

    /** A button in the settings window by role (tests fire these). */
    public Button buttonFor(javafx.scene.control.ButtonType role) {
        if (role == javafx.scene.control.ButtonType.APPLY) {
            return applyButton;
        }
        if (role == javafx.scene.control.ButtonType.OK) {
            return okButton;
        }
        return cancelButton;
    }

    /** Hides the window. Seam for tests: hide() can hang in
     *  Platform.startup-based JVMs (works under a real Application launch). */
    void closeWindow() {
        stage.close();
    }

    /** Applies the theme-family flag (adaptive palettes in app.css). */
    public void setDark(boolean dark) {
        UiTheme.applyDark(layout, dark);
    }

    /** Wraps a control with the visible restart-required mention (design D4). */
    public static Region restartRequiredRow(Region control) {
        var note = new Label(RESTART_NOTE);
        note.getStyleClass().add("restart-note");
        note.setWrapText(true);
        var box = new VBox(4, control, note);
        box.getStyleClass().add("restart-required");
        return box;
    }
}
