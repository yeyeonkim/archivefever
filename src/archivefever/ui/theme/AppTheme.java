package archivefever.ui.theme;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import javax.imageio.ImageIO;

public final class AppTheme {
    public static final int WIDTH = 412;
    public static final int HEIGHT = 917;

    private AppTheme() {}

    public static InputStream resource(String path) {
        InputStream stream = AppTheme.class.getResourceAsStream("/resources/" + path);
        if (stream == null) throw new IllegalStateException("Missing resource: " + path);
        return stream;
    }

    public static BufferedImage image(String path) {
        try (InputStream in = resource(path)) {
            BufferedImage image = ImageIO.read(in);
            if (image == null) throw new IOException("Unsupported image format");
            return image;
        } catch (IOException e) {
            throw new IllegalStateException("Cannot load image: " + path, e);
        }
    }

}
