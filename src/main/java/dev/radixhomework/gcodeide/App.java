package dev.radixhomework.gcodeide;

import dev.radixhomework.gcodeide.service.ConfigService;
import dev.radixhomework.gcodeide.util.ConfigPaths;
import dev.radixhomework.gcodeide.view.MainWindowController;
import dev.radixhomework.gcodeide.view.UiTheme;
import java.io.File;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import javafx.application.Application;
import javafx.scene.image.Image;
import javafx.scene.Scene;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import lombok.extern.slf4j.Slf4j;

/** Application entry point. */
@Slf4j
public class App extends Application {

    public static final String VERSION = "0.1.0";

    private static final String GCODE_FILTER = "G-code files (*.nc, *.gcode, *.ngc, *.tap)";

    private ConfigService configService;
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
        configService = new ConfigService(ConfigPaths.configFile());

        Function<String, Optional<Path>> openChooser = title -> {
            FileChooser dialog = newFileChooser(title, GCODE_FILTER, "*.nc", "*.gcode", "*.ngc", "*.tap");
            return Optional.ofNullable(dialog.showOpenDialog(stage)).map(File::toPath)
                    .map(path -> {
                        rememberLastDir(path, configService);
                        return path;
                    });
        };
        controller = MainWindowController.create(openChooser, suggested -> {
            FileChooser dialog = newFileChooser("Save G-code as", GCODE_FILTER,
                    "*.nc", "*.gcode", "*.ngc", "*.tap");
            dialog.setInitialFileName(suggested);
            return Optional.ofNullable(dialog.showSaveDialog(stage)).map(File::toPath)
                    .map(path -> {
                        rememberLastDir(path, configService);
                        return path;
                    });
        }, suggested -> {
            FileChooser dialog = newFileChooser("Save preview image",
                    "Images (*.png, *.jpg)", "*.png", "*.jpg");
            dialog.setInitialFileName(suggested);
            return Optional.ofNullable(dialog.showSaveDialog(stage)).map(File::toPath)
                    .map(path -> {
                        rememberLastDir(path, configService);
                        return path;
                    });
        });
        controller.getDocumentService().setPrompt(MainWindowController.alertPrompt());

        stage.getIcons().add(appIcon());
        stage.titleProperty().bind(controller.titleProperty());
        // persisted theme first (must precede scene creation); AtlantaFX
        // Primer Light is the default when nothing valid was ever selected.
        // app.css (scene stylesheet, higher precedence) layers tokens on top.
        boolean themeRestored = controller.applyTheme(
                String.valueOf(configService.loadConfig().get("theme")));
        if (!themeRestored) {
            atlantafx.base.theme.ThemeManager.instance()
                    .setTheme(new atlantafx.base.theme.PrimerLight());
        }
        controller.setThemePersister(name -> configService.updateConfig(c -> c.put("theme", name)));
        Scene scene = new Scene(controller.root());
        scene.getStylesheets().add(getClass().getResource("/app.css").toExternalForm());
        stage.setScene(scene);
        // theme-family flag on the scene root (adaptive palettes), then the
        // global UA stylesheet for every scene created from here on
        UiTheme.applyDark(scene.getRoot(),
                atlantafx.base.theme.ThemeManager.instance().getTheme().isDarkMode());
        javafx.application.Application.setUserAgentStylesheet(
                atlantafx.base.theme.ThemeManager.instance().getTheme()
                        .getUserAgentStylesheet());
        controller.setDarkListener(dark -> UiTheme.applyDark(scene.getRoot(), dark));

        restoreGeometry(stage, configService);
        stage.setOnCloseRequest(event -> {
            if (!controller.promptSaveBeforeClose()) {
                event.consume();
                return;
            }
            saveGeometry(stage, configService);
        });
        stage.show();
    }

    /** The application icon, scaled down from the packaged 1254x1254 logo. */
    public static Image appIcon() {
        return new Image(App.class.getResourceAsStream("/assets/logo.png"),
                64, 64, true, true);
    }

    private FileChooser newFileChooser(String title, String filterName, String... extensions) {
        FileChooser dialog = new FileChooser();
        dialog.setTitle(title);
        dialog.getExtensionFilters().add(new FileChooser.ExtensionFilter(filterName, extensions));
        applyLastDir(dialog, configService);
        return dialog;
    }

    /** Starts the dialog in the last used directory, when it still exists. */
    static void applyLastDir(FileChooser dialog, ConfigService configs) {
        if (configs.loadConfig().get("last_open_dir") instanceof String dir) {
            File file = new File(dir);
            if (file.isDirectory()) {
                dialog.setInitialDirectory(file);
            }
        }
    }

    /** Persists the parent of a successfully used file as the next initial directory. */
    static void rememberLastDir(Path file, ConfigService configs) {
        Path parent = file.toAbsolutePath().getParent();
        if (parent == null) {
            return;
        }
        try {
            configs.updateConfig(config -> config.put("last_open_dir", parent.toString()));
        } catch (RuntimeException e) {
            log.warn("last directory not remembered: {}", e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    public static void restoreGeometry(Stage stage, ConfigService configs) {
        Object geometry = configs.loadConfig().get("window_geometry");
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

    public static void saveGeometry(Stage stage, ConfigService configs) {
        try {
            configs.updateConfig(config -> config.put("window_geometry", Map.of(
                    "x", stage.getX(), "y", stage.getY(),
                    "w", stage.getWidth(), "h", stage.getHeight(),
                    "maximized", stage.isMaximized())));
        } catch (RuntimeException e) {
            log.warn("window geometry not saved: {}", e.getMessage());
        }
    }
}
