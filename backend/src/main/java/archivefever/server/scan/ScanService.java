package archivefever.server.scan;

import archivefever.server.book.Book;
import archivefever.server.book.BookLookup;
import archivefever.server.book.BookService;
import archivefever.server.library.LibraryService;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Registering a book by photographing its barcode.
 *
 * The Swing client creates a scan and waits on it; the phone uploads the photo to that
 * scan; the client then shows the result and confirms it into the user's library.
 */
@Service
public class ScanService {

	/** @param book present once the scan is RESOLVED or CONFIRMED */
	public record ScanView(String id, ScanStatus status, String reason, BookView book) {
	}

	/** @param link the YES24 product page; shown with the book as its source */
	public record BookView(String isbn13, String title, String author, String publisher, String link,
			String coverUrl, String spineUrl) {
	}

	@ResponseStatus(HttpStatus.NOT_FOUND)
	public static class ScanNotFoundException extends RuntimeException {

		ScanNotFoundException(String id) {
			super("No scan " + id);
		}

	}

	@ResponseStatus(HttpStatus.CONFLICT)
	public static class ScanStateException extends RuntimeException {

		ScanStateException(Scan scan, String attempted) {
			super("Cannot " + attempted + " a scan that is " + scan.status());
		}

	}

	private static final Logger log = LoggerFactory.getLogger(ScanService.class);

	private final ScanRepository scans;

	private final BarcodeReader barcodes;

	private final BookService books;

	private final LibraryService library;

	private final Duration timeToLive;

	private final Path uploads;

	ScanService(ScanRepository scans, BarcodeReader barcodes, BookService books, LibraryService library,
			@Value("${archivefever.scan.ttl}") Duration timeToLive,
			@Value("${archivefever.data-dir}") String dataDirectory) {
		this.scans = scans;
		this.barcodes = barcodes;
		this.books = books;
		this.library = library;
		this.timeToLive = timeToLive;
		this.uploads = Path.of(dataDirectory, "uploads");
	}

	public ScanView create(String userId) {
		Scan scan = scans.save(new Scan(UUID.randomUUID().toString(), userId, Instant.now()));
		return view(scan);
	}

	public ScanView get(String id, String userId) {
		return view(loadOwn(id, userId));
	}

	/** Reads the barcode from the photo and looks the book up. Only a PENDING scan accepts a photo. */
	public ScanView attachPhoto(String id, byte[] photo) {
		Scan scan = load(id);
		if (scan.status() != ScanStatus.PENDING) {
			throw new ScanStateException(scan, "add a photo to");
		}
		keepPhoto(id, photo);
		resolve(scan, photo);
		return view(scans.save(scan));
	}

	public ScanView cancel(String id) {
		Scan scan = load(id);
		if (scan.status() == ScanStatus.PENDING) {
			scan.setStatus(ScanStatus.CANCELLED);
			scans.save(scan);
		}
		return view(scan);
	}

	/** Adds the scanned book to the user's library. Confirming again returns the same book. */
	public LibraryService.ShelfBook confirm(String id, String userId) {
		Scan scan = loadOwn(id, userId);
		if (scan.status() != ScanStatus.RESOLVED && scan.status() != ScanStatus.CONFIRMED) {
			throw new ScanStateException(scan, "confirm");
		}
		LibraryService.ShelfBook added = library.add(scan.userId(), scan.isbn13());
		scan.setStatus(ScanStatus.CONFIRMED);
		scans.save(scan);
		return added;
	}

	private void resolve(Scan scan, byte[] photo) {
		BufferedImage image = decode(photo);
		if (image == null) {
			scan.finish(ScanStatus.NO_BARCODE, "UNREADABLE_IMAGE", null);
			return;
		}
		Optional<String> isbn = barcodes.readIsbn13(image);
		if (isbn.isEmpty()) {
			scan.finish(ScanStatus.NO_BARCODE, null, null);
			return;
		}
		try {
			boolean known = books.find(isbn.get()).isPresent();
			scan.finish(known ? ScanStatus.RESOLVED : ScanStatus.NOT_FOUND, null, isbn.get());
		}
		catch (BookLookup.LookupException exception) {
			log.warn("Lookup failed for ISBN {}: {}", isbn.get(), exception.getMessage());
			scan.finish(ScanStatus.FAILED, exception.failure().name(), isbn.get());
		}
	}

	/** The scan, for the user who created it. Another user's scan is reported the same way as a missing one. */
	private Scan loadOwn(String id, String userId) {
		Scan scan = load(id);
		if (!scan.userId().equals(userId)) {
			throw new ScanNotFoundException(id);
		}
		return scan;
	}

	/**
	 * The scan, for whoever knows its id. That is how the phone app, which has no session of
	 * its own, delivers the photo or calls the scan off: the id is random, and only the
	 * signed-in client that created the scan was told it.
	 */
	private Scan load(String id) {
		Scan scan = scans.findById(id).orElseThrow(() -> new ScanNotFoundException(id));
		if (scan.status() == ScanStatus.PENDING && scan.createdAt().plus(timeToLive).isBefore(Instant.now())) {
			scan.setStatus(ScanStatus.EXPIRED);
			scans.save(scan);
		}
		return scan;
	}

	private ScanView view(Scan scan) {
		boolean hasBook = scan.status() == ScanStatus.RESOLVED || scan.status() == ScanStatus.CONFIRMED;
		BookView book = hasBook ? books.cached(scan.isbn13()).map(ScanService::view).orElse(null) : null;
		return new ScanView(scan.id(), scan.status(), scan.reason(), book);
	}

	private static BookView view(Book book) {
		String images = "/api/images/" + book.isbn13();
		return new BookView(book.isbn13(), book.title(), book.author(), book.publisher(), book.link(),
				images + "/cover.png", images + "/spine.png");
	}

	private static BufferedImage decode(byte[] photo) {
		try {
			return ImageIO.read(new ByteArrayInputStream(photo));
		}
		catch (IOException exception) {
			return null;
		}
	}

	/** Photos are kept so that a barcode the reader missed can be examined afterwards. */
	private void keepPhoto(String id, byte[] photo) {
		try {
			Files.createDirectories(uploads);
			Files.write(uploads.resolve(id + ".img"), photo);
		}
		catch (IOException exception) {
			log.warn("Could not keep the photo for scan {}: {}", id, exception.toString());
		}
	}

}
