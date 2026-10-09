package dev.radixhomework.gcodeide.view;

import dev.radixhomework.gcodeide.model.svg.GCodeGenerator;
import dev.radixhomework.gcodeide.model.svg.SvgDocument;
import dev.radixhomework.gcodeide.model.svg.SvgImportOptions;
import java.util.List;
import java.util.function.Consumer;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * The SVG import wizard: parameter grid over the loaded drawing (target
 * width, placement, depth passes, feed, safe Z, spindle), the computed
 * millimeter size, and warnings for unsupported constructs. Generate commits
 * (invoking the callback with the generated G-code text); Cancel closes
 * without importing.
 */
public class ImportWizard {

    private final Stage stage = new Stage();
    private final TextField widthField = new TextField("100");
    private final TextField placeXField = new TextField("0");
    private final TextField placeYField = new TextField("0");
    private final TextField depthField = new TextField("0.5");
    private final TextField finalDepthField = new TextField("-1.0");
    private final TextField feedField = new TextField("600");
    private final TextField safeZField = new TextField("5");
    private final TextField spindleField = new TextField("10000");
    private final Label errorLabel = new Label();
    private final List<String> warnings;
    private final SvgDocument doc;
    private final Consumer<String> onGenerated;

    public ImportWizard(SvgDocument doc, Consumer<String> onGenerated) {
        this.doc = doc;
        this.warnings = doc.warnings();
        this.onGenerated = onGenerated;
        stage.setTitle("Import image");
        stage.setResizable(false);

        double w = doc.width() > 0 ? doc.width() : 100;
        double h = doc.height() > 0 ? doc.height() : 100;
        var sizeLabel = new Label(String.format(java.util.Locale.ROOT,
                "Drawing: %.1f x %.1f units", w, h));

        var grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(8);
        int row = 0;
        addRow(grid, row++, "Width (mm):", widthField);
        addRow(grid, row++, "Placement X (mm):", placeXField);
        addRow(grid, row++, "Placement Y (mm):", placeYField);
        addRow(grid, row++, "Depth per pass (mm):", depthField);
        addRow(grid, row++, "Final depth (mm):", finalDepthField);
        addRow(grid, row++, "Cut feed (mm/min):", feedField);
        addRow(grid, row++, "Safe Z (mm):", safeZField);
        addRow(grid, row++, "Spindle (RPM):", spindleField);

        var content = new VBox(10, sizeLabel, grid);
        if (!warnings.isEmpty()) {
            var warningsLabel = new Label("Warnings:");
            var warningsText = new Label(String.join("\n", warnings));
            warningsText.setWrapText(true);
            content.getChildren().addAll(warningsLabel, warningsText);
        }
        errorLabel.getStyleClass().add("restart-note");
        content.getChildren().add(errorLabel);

        var generateButton = new Button("Generate");
        generateButton.setOnAction(event -> {
            var options = buildOptions();
            if (options != null) {
                stage.close();
                onGenerated.accept(GCodeGenerator.generate(doc, options));
            }
        });
        var cancelButton = new Button("Cancel");
        cancelButton.setOnAction(event -> stage.close());

        var buttonBar = new javafx.scene.layout.HBox(8, generateButton, cancelButton);
        buttonBar.setPadding(new Insets(10, 0, 0, 0));
        content.getChildren().add(buttonBar);

        content.setPadding(new Insets(14));
        var scene = new Scene(content);
        scene.getStylesheets().add(getClass().getResource("/app.css").toExternalForm());
        stage.setScene(scene);
    }

    private void addRow(GridPane grid, int row, String label, TextField field) {
        grid.add(new Label(label), 0, row);
        grid.add(field, 1, row);
        field.setPrefColumnCount(12);
    }

    /** Parses the fields into options; null (with the error label set) when invalid. */
    private SvgImportOptions buildOptions() {
        try {
            var options = new SvgImportOptions(
                    Double.parseDouble(widthField.getText().trim()),
                    Double.parseDouble(placeXField.getText().trim()),
                    Double.parseDouble(placeYField.getText().trim()),
                    Double.parseDouble(depthField.getText().trim()),
                    Double.parseDouble(finalDepthField.getText().trim()),
                    Double.parseDouble(feedField.getText().trim()),
                    Double.parseDouble(safeZField.getText().trim()),
                    (int) Double.parseDouble(spindleField.getText().trim()));
            errorLabel.setText("");
            return options;
        } catch (NumberFormatException e) {
            errorLabel.setText("Invalid number - check the fields.");
            return null;
        }
    }

    public void show() {
        stage.show();
    }

    public boolean isShowing() {
        return stage.isShowing();
    }

    public TextField widthFieldForTest() {
        return widthField;
    }

    public Button generateButtonForTest() {
        return (Button) stage.getScene().getRoot()
                .lookupAll(".button").stream().toList().get(0);
    }

    public Button cancelButtonForTest() {
        return (Button) stage.getScene().getRoot()
                .lookupAll(".button").stream().toList().get(1);
    }
}
