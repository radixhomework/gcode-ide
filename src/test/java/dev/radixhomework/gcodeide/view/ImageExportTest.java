package dev.radixhomework.gcodeide.view;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.radixhomework.gcodeide.model.parsing.GCodeParser;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationTest;

class ImageExportTest extends ApplicationTest {

    private Preview3DView view;

    @Override
    public void start(Stage stage) {
        view = new Preview3DView();
        stage.setScene(new javafx.scene.Scene(view.node(), 500, 350));
        stage.show();
    }

    @Test
    void writesValidPngAndJpg(@TempDir Path dir) throws Exception {
        interact(() -> view.setToolpath(GCodeParser.parse("G0 X10\nG1 X20 F600\n")));
        Path png = dir.resolve("preview.png");
        Path jpg = dir.resolve("preview.jpg");
        interact(() -> {
            try {
                ImageExport.write(view.node(), png);
                ImageExport.write(view.node(), jpg);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });

        BufferedImage pngImage = ImageIO.read(png.toFile());
        assertNotNull(pngImage, "valid PNG written");
        assertTrue(pngImage.getWidth() > 0 && pngImage.getHeight() > 0);

        BufferedImage jpgImage = ImageIO.read(jpg.toFile());
        assertNotNull(jpgImage, "valid JPG written");
        assertTrue(jpgImage.getWidth() > 0 && jpgImage.getHeight() > 0);
    }
}
