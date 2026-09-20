package dev.radixhomework.gcodeide;

import dev.radixhomework.gcodeide.service.ProfileService;
import dev.radixhomework.gcodeide.util.ConfigPaths;
import dev.radixhomework.gcodeide.view.MainWindowController;
import java.io.File;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import lombok.extern.slf4j.Slf4j;

/** Application entry point. */
@Slf4j
public class App extends Application {

    public static final String VERSION = "0.1.0";

    private static final String GCODE_FILTER = "G-code files (*.nc, *.gcode, *.ngc, *.tap)";

    private ProfileService profileService;
    private MainWindowController controller;

    public static void main(String[] args) {
        if (handleVersionArg(args)) {
            return;
        }
        launch(args);
    }

    /** Handles {@code --version} before the FX toolkit starts (a GUI binary may have no stdout). */
    static boolean handleVersionArg(String[] args) {
        for (String arg : args) {
            if ("--version".equals(arg)) {
                if (System.out != null) {
                    System.out.println("gcode-ide " + VERSION);
                }
                return true;
            }
        }
        return false;
    }

    @Override
    public void start(Stage stage) {
        profileService = new ProfileService(ConfigPaths.profilesDir(), ConfigPaths.configFile());

        Function<String, Optional<Path>> openChooser = title -> {
            FileChooser dialog = new FileChooser();
            dialog.setTitle(title);
            dialog.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter(GCODE_FILTER, "*.nc", "*.gcode", "*.ngc", "*.tap"));
            return Optional.ofNullable(dialog.showOpenDialog(stage)).map(File::toPath);
        };
        controller = MainWindowController.create(profileService, openChooser, suggested -> {
            FileChooser dialog = new FileChooser();
            dialog.setTitle("Save G-code as");
            dialog.setInitialFileName(suggested);
            dialog.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter(GCODE_FILTER, "*.nc", "*.gcode", "*.ngc", "*.tap"));
            return Optional.ofNullable(dialog.showSaveDialog(stage)).map(File::toPath);
        });
        controller.getDocumentService().setPrompt(MainWindowController.alertPrompt());

        stage.titleProperty().bind(controller.titleProperty());
        Scene scene = new Scene(controller.root());
        stage.setScene(scene);
        restoreGeometry(stage, profileService);
        stage.setOnCloseRequest(event -> {
            if (!controller.promptSaveBeforeClose()) {
                event.consume();
                return;
            }
            saveGeometry(stage, profileService);
        });
        stage.show();
    }

    @SuppressWarnings("unchecked")
    public static void restoreGeometry(Stage stage, ProfileService profiles) {
        Object geometry = profiles.loadConfig().get("window_geometry");
        if (!(geometry instanceof Map<?, ?> map)) {
            return;
        }
        try {
            if (map.get("w") instanceof Number w && map.get("h") instanceof Number h) {
                stage.setWidth(w.doubleValue());
                stage.setHeight(h.doubleValue());
            }
            if (map.get("x") instanceof Number x && map.get("y") instanceof Number y) {
                stage.setX(x.doubleValue());
                stage.setY(y.doubleValue());
            }
            if (Boolean.TRUE.equals(map.get("maximized"))) {
                stage.setMaximized(true);
            }
        } catch (RuntimeException e) {
            log.warn("window geometry not restored: {}", e.getMessage());
        }
    }

    public static void saveGeometry(Stage stage, ProfileService profiles) {
        try {
            profiles.updateConfig(config -> config.put("window_geometry", Map.of(
                    "x", stage.getX(), "y", stage.getY(),
                    "w", stage.getWidth(), "h", stage.getHeight(),
                    "maximized", stage.isMaximized())));
        } catch (RuntimeException e) {
            log.warn("window geometry not saved: {}", e.getMessage());
        }
    }
}
