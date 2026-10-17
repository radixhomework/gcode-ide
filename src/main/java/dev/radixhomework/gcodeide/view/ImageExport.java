package dev.radixhomework.gcodeide.view;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;
import javafx.scene.Node;
import javafx.scene.image.WritableImage;

/**
 * Saves a rendered JavaFX node as PNG or JPG (by file extension). Uses the
 * pixel reader directly instead of {@code javafx.embed.swing} so the app does
 * not depend on the javafx-swing module; JPG composites over white because
 * the format has no alpha.
 */
public final class ImageExport {

    private ImageExport() {
    }

    public static void write(Node node, Path target) throws IOException {
        WritableImage snapshot = node.snapshot(null, null);
        int width = (int) Math.max(1, snapshot.getWidth());
        int height = (int) Math.max(1, snapshot.getHeight());
        String name = target.getFileName().toString().toLowerCase(Locale.ROOT);
        boolean jpg = name.endsWith(".jpg") || name.endsWith(".jpeg");

        BufferedImage image = new BufferedImage(width, height,
                jpg ? BufferedImage.TYPE_INT_RGB : BufferedImage.TYPE_INT_ARGB);
        var reader = snapshot.getPixelReader();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                image.setRGB(x, y, reader.getArgb(x, y));
            }
        }
        if (jpg) {
            compositeOverWhite(image);
        }
        boolean ok = javax.imageio.ImageIO.write(image, jpg ? "jpg" : "png", target.toFile());
        if (!ok) {
            throw new IOException("no image writer for " + target);
        }
    }

    private static void compositeOverWhite(BufferedImage image) {
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int argb = image.getRGB(x, y);
                int alpha = (argb >>> 24) & 0xFF;
                if (alpha == 0xFF) {
                    continue;
                }
                int r = (argb >>> 16) & 0xFF;
                int g = (argb >>> 8) & 0xFF;
                int b = argb & 0xFF;
                r = (r * alpha + 255 * (255 - alpha)) / 255;
                g = (g * alpha + 255 * (255 - alpha)) / 255;
                b = (b * alpha + 255 * (255 - alpha)) / 255;
                image.setRGB(x, y, 0xFF000000 | (r << 16) | (g << 8) | b);
            }
        }
    }
}
