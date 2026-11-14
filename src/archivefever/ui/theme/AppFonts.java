package archivefever.ui.theme;

import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.font.TextAttribute;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

/** Shared typography, loaded once from bundled font files. */
public final class AppFonts {
    private static final Font HEADING = load("fonts/CormorantGaramond-Bold.ttf");
    private static final Font HEADING_REGULAR = load("fonts/CormorantGaramond.ttf");
    private static final Font BODY = load("fonts/MaruBuri-Regular.ttf");
    private static final Font TYPEWRITER = load("fonts/TravelingTypewriter.ttf");

    private static final Font BODY_BOLD = load("fonts/MaruBuri-Bold.ttf");

    private AppFonts() {}

    public static Font heading(float size) {
        return HEADING.deriveFont(size)
                .deriveFont(Map.of(TextAttribute.TRACKING, -0.02f));
    }

    public static Font headingRegular(float size) {
        return HEADING_REGULAR.deriveFont(size).deriveFont(Map.of(TextAttribute.TRACKING,-.02f));
    }

    public static Font body(float size) {
        return BODY.deriveFont(size);
    }

    public static Font typewriter(float size) { return TYPEWRITER.deriveFont(size); }

    public static Font bodyBold(float size) { return BODY_BOLD.deriveFont(size); }

    private static Font load(String path) {
        try (InputStream in = AppTheme.resource(path)) {
            return Font.createFont(Font.TRUETYPE_FONT, in);
        } catch (IOException | FontFormatException e) {
            throw new IllegalStateException("Cannot load font: " + path, e);
        }
    }
}
