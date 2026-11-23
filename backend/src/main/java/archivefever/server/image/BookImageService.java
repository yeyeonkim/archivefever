package archivefever.server.image;

import archivefever.server.book.Book;
import archivefever.server.book.BookService;
import archivefever.server.book.Isbn13;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import javax.imageio.ImageIO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Cover and spine pictures for the Swing client, as small PNGs on our own origin.
 *
 * The client runs in a browser JVM where decoding is slow and other origins are blocked,
 * so the server fetches the catalogue's picture once, shrinks it and keeps it on disk.
 */
@Service
public class BookImageService {

	public enum Kind {

		COVER(400), SPINE(2 * SHELF_HEIGHT);

		private final int height;

		Kind(int height) {
			this.height = height;
		}

	}

	/** Height at which the Swing shelf draws a spine. Pictures are kept at twice that. */
	public static final int SHELF_HEIGHT = 172;

	/** Narrower spines are too thin to tap on a phone. */
	private static final int MIN_SHELF_WIDTH = 14;

	private static final int MAX_SHELF_WIDTH = 60;

	private static final Logger log = LoggerFactory.getLogger(BookImageService.class);

	private final BookService books;

	private final Path directory;

	private final HttpClient http = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(5))
			.followRedirects(HttpClient.Redirect.NORMAL)
			.build();

	BookImageService(BookService books, @Value("${archivefever.data-dir}") String dataDirectory) {
		this.books = books;
		this.directory = Path.of(dataDirectory, "images");
	}

	/** The picture as PNG bytes, or empty for a book that was never looked up. */
	public Optional<byte[]> png(String isbn13, Kind kind) {
		// Validating also keeps the value safe to use in a file name.
		if (!Isbn13.isValid(isbn13)) {
			return Optional.empty();
		}
		Path file = directory.resolve(isbn13 + "-" + kind.name().toLowerCase(Locale.ROOT) + ".png");
		try {
			synchronized (this) {
				if (Files.exists(file)) {
					return Optional.of(Files.readAllBytes(file));
				}
				Optional<Book> book = books.cached(isbn13);
				if (book.isEmpty()) {
					return Optional.empty();
				}
				Picture picture = picture(book.get(), kind);
				byte[] png = encode(picture.image());
				if (picture.keep()) {
					store(png, file);
				}
				return Optional.of(png);
			}
		}
		catch (IOException exception) {
			throw new UncheckedIOException(exception);
		}
	}

	/** Width in pixels of the book's spine when drawn {@link #SHELF_HEIGHT} tall. */
	public int spineWidth(String isbn13) {
		Optional<byte[]> png = png(isbn13, Kind.SPINE);
		if (png.isEmpty()) {
			return MIN_SHELF_WIDTH;
		}
		try {
			BufferedImage spine = ImageIO.read(new ByteArrayInputStream(png.get()));
			int width = Math.round(spine.getWidth() * SHELF_HEIGHT / (float) spine.getHeight());
			return Math.max(MIN_SHELF_WIDTH, Math.min(MAX_SHELF_WIDTH, width));
		}
		catch (IOException exception) {
			throw new UncheckedIOException(exception);
		}
	}

	/**
	 * @param keep false when the catalogue could not be reached, so the stand-in is not
	 * stored and the real picture is fetched on a later request
	 */
	private record Picture(BufferedImage image, boolean keep) {
	}

	private Picture picture(Book book, Kind kind) {
		String url = kind == Kind.COVER ? book.coverImageUrl() : book.spineImageUrl();
		boolean reached = true;
		BufferedImage original = null;
		if (url != null) {
			try {
				original = download(url, kind);
			}
			catch (IOException exception) {
				log.warn("Could not fetch {} picture for {}: {}", kind, book.isbn13(), exception.toString());
				reached = false;
			}
			catch (InterruptedException exception) {
				Thread.currentThread().interrupt();
				reached = false;
			}
		}
		if (original != null) {
			return new Picture(scale(original, kind.height), true);
		}
		BufferedImage drawn = kind == Kind.COVER ? GeneratedArt.cover(book, kind.height)
				: GeneratedArt.spine(book, kind.height);
		return new Picture(drawn, reached);
	}

	/** @return the catalogue's picture, or null when it has none for this book */
	private BufferedImage download(String url, Kind kind) throws IOException, InterruptedException {
		URI uri = URI.create(url);
		// The URL comes from the catalogue's answer; refuse anything that is not its image host.
		if (!"https".equals(uri.getScheme()) || uri.getHost() == null || !uri.getHost().endsWith(".yes24.com")) {
			log.warn("Ignoring unexpected image URL {}", url);
			return null;
		}
		HttpResponse<byte[]> response = http.send(
				HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(8)).build(),
				HttpResponse.BodyHandlers.ofByteArray());
		if (response.statusCode() != 200) {
			throw new IOException("HTTP " + response.statusCode() + " for " + url);
		}
		BufferedImage image = ImageIO.read(new ByteArrayInputStream(response.body()));
		if (image == null) {
			throw new IOException("Not an image: " + url);
		}
		return isPlaceholder(image, response.body().length, kind) ? null : image;
	}

	/**
	 * YES24 answers 200 with a small 420x600 stand-in when a product has no picture, and the
	 * API does not say so. A real spine is also far taller than it is wide.
	 */
	static boolean isPlaceholder(BufferedImage image, int bytes, Kind kind) {
		boolean standIn = image.getWidth() == 420 && image.getHeight() == 600 && bytes < 6000;
		return standIn || (kind == Kind.SPINE && image.getHeight() < 4 * image.getWidth());
	}

	private static BufferedImage scale(BufferedImage image, int height) {
		if (image.getHeight() <= height) {
			return image;
		}
		int width = Math.max(1, Math.round(image.getWidth() * height / (float) image.getHeight()));
		BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = result.createGraphics();
		// Area averaging keeps small spine lettering legible when shrinking several times over.
		g.drawImage(image.getScaledInstance(width, height, Image.SCALE_AREA_AVERAGING), 0, 0, null);
		g.dispose();
		return result;
	}

	private static byte[] encode(BufferedImage image) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ImageIO.write(image, "png", out);
		return out.toByteArray();
	}

	private void store(byte[] png, Path file) throws IOException {
		Files.createDirectories(directory);
		Path temporary = Files.createTempFile(directory, "image-", ".tmp");
		Files.write(temporary, png);
		Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
	}

}
