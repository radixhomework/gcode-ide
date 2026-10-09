package dev.radixhomework.gcodeide;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import dev.radixhomework.gcodeide.service.ConfigService;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationTest;

class LastDirTest extends ApplicationTest {

    @TempDir
    Path dir;

    @Override
    public void start(Stage stage) {
        stage.show();
    }

    @Test
    void dialogStartsInRememberedDirectory() {
        ConfigService configs = new ConfigService(dir.resolve("config.yaml"));
        configs.updateConfig(c -> c.put("last_open_dir", dir.toString()));

        FileChooser dialog = new FileChooser();
        App.applyLastDir(dialog, configs);
        assertEquals(new File(dir.toString()), dialog.getInitialDirectory());
    }

    @Test
    void missingRememberedDirectoryIsIgnored() {
        ConfigService configs = new ConfigService(dir.resolve("config2.yaml"));
        configs.updateConfig(c -> c.put("last_open_dir",
                dir.resolve("does-not-exist").toString()));

        FileChooser dialog = new FileChooser();
        App.applyLastDir(dialog, configs);
        assertNull(dialog.getInitialDirectory());
    }

    @Test
    void successfulPickPersistsParentDirectory() throws Exception {
        ConfigService configs = new ConfigService(dir.resolve("config3.yaml"));
        Path nested = Files.createDirectories(dir.resolve("gcode"));

        App.rememberLastDir(nested.resolve("part.nc"), configs);

        assertEquals(nested.toAbsolutePath().toString(),
                configs.loadConfig().get("last_open_dir"));
        // and the next dialog starts there
        FileChooser dialog = new FileChooser();
        App.applyLastDir(dialog, configs);
        assertEquals(nested.toAbsolutePath().toFile(), dialog.getInitialDirectory());
    }

    @Test
    void staleUnknownKeysAreLeftInPlace() {
        ConfigService configs = new ConfigService(dir.resolve("config4.yaml"));
        configs.updateConfig(c -> c.put("active_profile", "Some Old Machine"));
        configs.updateConfig(c -> c.put("theme", "Primer Dark"));

        // the stale key survives (ignored, not deleted) and the new key lands
        assertEquals("Some Old Machine", configs.loadConfig().get("active_profile"));
        assertEquals("Primer Dark", configs.loadConfig().get("theme"));
    }
}
