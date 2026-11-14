package archivefever.mock;

import archivefever.model.BookSpine;
import archivefever.ui.theme.AppTheme;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.ArrayList;

/** Figma artwork only, not live YES24 results. */
public final class DemoBooks {
    private DemoBooks() {}

    public static List<BookSpine> load() {
        BufferedImage atlas = AppTheme.image("images/main/demo-spines.png");
        int[][] regions = {{137,64}, {209,72}, {289,114}, {411,116}, {537,191}, {24,104}};
        String[] titles = {"이방인", "시지프 신화", "페스트", "정의란 무엇인가", "총, 균, 쇠", "소년이 온다"};
        List<BookSpine> books = new ArrayList<>();
        for (int i=0; i<regions.length; i++) {
            int x=regions[i][0], width=regions[i][1];
            books.add(new BookSpine("demo-"+i, titles[i],
                    atlas.getSubimage(x,24,width,1152),
                    Math.max(1, Math.round(width * 172f / 1152))));
        }
        return List.copyOf(books);
    }
}
