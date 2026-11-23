package archivefever.server.library;

import archivefever.server.book.Book;
import archivefever.server.book.BookService;
import archivefever.server.image.BookImageService;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class LibraryService {

	/**
	 * A shelf entry as the client draws it.
	 *
	 * @param link the YES24 product page; shown with the book as its source
	 * @param spineWidth width in pixels of the spine at the shelf's fixed spine height
	 */
	public record ShelfBook(String isbn13, String title, String author, String link, String spineUrl, int spineWidth) {
	}

	private final LibraryBookRepository library;

	private final BookService books;

	private final BookImageService images;

	LibraryService(LibraryBookRepository library, BookService books, BookImageService images) {
		this.library = library;
		this.books = books;
		this.images = images;
	}

	/** Puts the book on the user's shelf; adding it twice changes nothing. */
	public ShelfBook add(String userId, String isbn13) {
		if (!library.existsByUserIdAndIsbn13(userId, isbn13)) {
			library.save(new LibraryBook(userId, isbn13, Instant.now()));
		}
		return shelfBook(isbn13).orElseThrow(() -> new IllegalStateException("No catalogue data for " + isbn13));
	}

	/** The user's books, oldest first, which is the order they sit on the shelf. */
	public List<ShelfBook> list(String userId) {
		return library.findByUserIdOrderByAddedAtAscIdAsc(userId).stream()
				.map(entry -> shelfBook(entry.isbn13()))
				.flatMap(Optional::stream)
				.toList();
	}

	private Optional<ShelfBook> shelfBook(String isbn13) {
		return books.cached(isbn13).map(this::toShelfBook);
	}

	private ShelfBook toShelfBook(Book book) {
		return new ShelfBook(book.isbn13(), book.title(), book.author(), book.link(),
				"/api/images/" + book.isbn13() + "/spine.png", images.spineWidth(book.isbn13()));
	}

}
