package archivefever.server.image;

import archivefever.server.book.Book;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/** Plain drawn covers and spines for books the catalogue has no picture of. */
final class GeneratedArt {

	private static final Color[] CLOTH = {
			new Color(0x6B3A2E), new Color(0x2F4A3F), new Color(0x2E3F5C), new Color(0x5A4A2A),
			new Color(0x4B2F4F), new Color(0x7A5230), new Color(0x3C3C3C), new Color(0x1F4E57) };

	private static final Color INK = new Color(0xF2E9D8);

	private GeneratedArt() {
	}

	/** Spine {@code height} pixels tall; thicker for longer books. */
	static BufferedImage spine(Book book, int height) {
		int pages = book.pages() == null ? 300 : book.pages();
		// About 14 pages per shelf pixel, kept between a slim paperback and a heavy volume.
		int shelfWidth = Math.max(16, Math.min(44, Math.round(pages / 14f)));
		int width = Math.round(shelfWidth * height / (float) BookImageService.SHELF_HEIGHT);
		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = graphics(image);
		g.setColor(colour(book));
		g.fillRect(0, 0, width, height);
		g.setColor(new Color(255, 255, 255, 40));
		g.fillRect(0, 0, Math.max(1, width / 10), height);
		// Title runs down the spine, as on most printed books.
		g.setFont(new Font(Font.SERIF, Font.BOLD, Math.round(width * 0.5f)));
		FontMetrics metrics = g.getFontMetrics();
		int margin = Math.round(height * 0.06f);
		String title = fit(book.title(), metrics, height - 2 * margin);
		g.setColor(INK);
		g.rotate(Math.PI / 2);
		g.drawString(title, margin, -(width - metrics.getAscent() + metrics.getDescent()) / 2 - metrics.getDescent());
		g.dispose();
		return image;
	}

	/** Cover {@code height} pixels tall at a common 2:3 paperback shape. */
	static BufferedImage cover(Book book, int height) {
		int width = Math.round(height * 2 / 3f);
		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = graphics(image);
		g.setColor(colour(book));
		g.fillRect(0, 0, width, height);
		int margin = Math.round(width * 0.1f);
		g.setColor(INK);
		g.drawRect(margin / 2, margin / 2, width - margin, height - margin);
		g.setFont(new Font(Font.SERIF, Font.BOLD, Math.round(width * 0.11f)));
		FontMetrics metrics = g.getFontMetrics();
		int y = Math.round(height * 0.25f);
		for (String line : wrap(book.title(), metrics, width - 2 * margin, 4)) {
			g.drawString(line, margin, y);
			y += metrics.getHeight();
		}
		if (book.author() != null) {
			g.setFont(new Font(Font.SERIF, Font.PLAIN, Math.round(width * 0.07f)));
			metrics = g.getFontMetrics();
			g.drawString(fit(book.author(), metrics, width - 2 * margin), margin, height - margin - metrics.getDescent());
		}
		g.dispose();
		return image;
	}

	private static Graphics2D graphics(BufferedImage image) {
		Graphics2D g = image.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		return g;
	}

	/** The same book always gets the same colour. */
	private static Color colour(Book book) {
		return CLOTH[Math.floorMod(book.isbn13().hashCode(), CLOTH.length)];
	}

	private static String fit(String text, FontMetrics metrics, int width) {
		if (metrics.stringWidth(text) <= width) {
			return text;
		}
		String ellipsis = "…";
		int end = text.length();
		while (end > 0 && metrics.stringWidth(text.substring(0, end) + ellipsis) > width) {
			end--;
		}
		return text.substring(0, end) + ellipsis;
	}

	private static List<String> wrap(String text, FontMetrics metrics, int width, int maxLines) {
		List<String> lines = new ArrayList<>();
		String rest = text.strip();
		while (!rest.isEmpty() && lines.size() < maxLines) {
			if (lines.size() == maxLines - 1) {
				lines.add(fit(rest, metrics, width));
				break;
			}
			int end = rest.length();
			while (end > 1 && metrics.stringWidth(rest.substring(0, end)) > width) {
				end--;
			}
			// Prefer breaking at a space when one is near the edge.
			int space = rest.lastIndexOf(' ', end);
			if (end < rest.length() && space > end / 2) {
				end = space;
			}
			lines.add(rest.substring(0, end).strip());
			rest = rest.substring(end).strip();
		}
		return lines;
	}

}
