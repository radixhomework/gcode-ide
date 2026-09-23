package dev.radixhomework.gcodeide;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import dev.radixhomework.gcodeide.service.ProfileService;
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
        ProfileService profiles = new ProfileService(null, dir.resolve("config.yaml"));
        profiles.updateConfig(c -> c.put("last_open_dir", dir.toString()));

        FileChooser dialog = new FileChooser();
        App.applyLastDir(dialog, profiles);
        assertEquals(new File(dir.toString()), dialog.getInitialDirectory());
    }

    @Test
    void missingRememberedDirectoryIsIgnored() {
        ProfileService profiles = new ProfileService(null, dir.resolve("config2.yaml"));
        profiles.updateConfig(c -> c.put("last_open_dir",
                dir.resolve("does-not-exist").toString()));

        FileChooser dialog = new FileChooser();
        App.applyLastDir(dialog, profiles);
        assertNull(dialog.getInitialDirectory());
    }

    @Test
    void successfulPickPersistsParentDirectory() throws Exception {
        ProfileService profiles = new ProfileService(null, dir.resolve("config3.yaml"));
        Path nested = Files.createDirectories(dir.resolve("gcode"));

        App.rememberLastDir(nested.resolve("part.nc"), profiles);

        assertEquals(nested.toAbsolutePath().toString(),
                profiles.loadConfig().get("last_open_dir"));
        // and the next dialog starts there
        FileChooser dialog = new FileChooser();
        App.applyLastDir(dialog, profiles);
        assertEquals(nested.toAbsolutePath().toFile(), dialog.getInitialDirectory());
    }
}
