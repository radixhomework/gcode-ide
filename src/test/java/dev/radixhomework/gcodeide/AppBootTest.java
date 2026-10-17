package dev.radixhomework.gcodeide;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import atlantafx.base.theme.Theme;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.testfx.framework.junit5.ApplicationTest;

/** Boots the real {@link App#start(Stage)}: config load, controller wiring,
 *  icon, theme restore, scene, geometry restore. Reads the developer's real
 *  config file (read-only at boot); on CI no config exists, so the
 *  PrimerLight default branch runs. */
class AppBootTest extends ApplicationTest {

    private Stage stage;

    @Override
    public void start(Stage stage) {
        this.stage = stage;
        new App().start(stage);
    }

    @AfterEach
    void hideAppWindow() {
        // the booted IDE window must not keep rendering behind the later
        // view tests: full-suite runs turned flaky from the extra load
        interact(() -> stage.hide());
    }

    @Test
    void bootWiresSceneStylesheetThemeAndTitle() {
        Scene scene = stage.getScene();
        assertNotNull(scene, "scene set on the primary stage");
        assertNotNull(scene.getRoot());
        assertTrue(scene.getStylesheets().stream()
                .anyMatch(url -> url.endsWith("/app.css")), "app.css attached");
        Theme theme = atlantafx.base.theme.ThemeManager.instance().getTheme();
        assertNotNull(theme, "a theme is active (restored or the default)");
        assertNotNull(stage.getTitle());
        assertTrue(!stage.getTitle().isBlank(), "title bound to the document");
        assertNotNull(stage.getIcons());
    }

    @Test
    void geometryRestoreToleratesAnEmptyConfig() {
        // explicit second pass over restoreGeometry with nothing persisted
        interact(() -> new App().restoreGeometry(stage,
                new dev.radixhomework.gcodeide.service.ConfigService(null)));
        assertNotNull(stage.getScene());
    }
}
