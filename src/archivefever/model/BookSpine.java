package archivefever.model;

import java.awt.image.BufferedImage;
import java.util.Objects;

/** Presentation data, independent of the future YES24 response format. */
public record BookSpine(String id, String title, BufferedImage image, int width) {
    public BookSpine {
        Objects.requireNonNull(id);
        Objects.requireNonNull(title);
        Objects.requireNonNull(image);
        if (width < 1 || width > 355) throw new IllegalArgumentException("Invalid spine width");
    }
}
