package archivefever.server.library;

import archivefever.server.book.Book;
import archivefever.server.book.BookService;
import archivefever.server.book.Isbn13;
import archivefever.server.image.BookImageService;
import archivefever.server.web.ApiError;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

	/**
	 * A book a search found.
	 *
	 * @param link the YES24 product page; shown with the book as its source
	 * @param onShelf whether the user has it already
	 */
	public record FoundBook(String isbn13, String title, String author, String publisher, String link, boolean onShelf) {
	}

	/** A book looked up for the user to look at before they put it on their shelf. */
	public record BookCard(String isbn13, String title, String author, String publisher, String link, String coverUrl,
			String spineUrl) {
	}

	private final LibraryBookRepository library;

	private final BookService books;

	private final BookImageService images;

	private final ApplicationEventPublisher events;

	LibraryService(LibraryBookRepository library, BookService books, BookImageService images,
			ApplicationEventPublisher events) {
		this.library = library;
		this.books = books;
		this.images = images;
		this.events = events;
	}

	/** Puts the book on the user's shelf; adding it twice changes nothing. */
	public ShelfBook add(String userId, String isbn13) {
		if (!library.existsByUserIdAndIsbn13(userId, isbn13)) {
			library.save(new LibraryBook(userId, isbn13, Instant.now()));
		}
		return shelfBook(isbn13).orElseThrow(() -> new IllegalStateException("No catalogue data for " + isbn13));
	}

	/**
	 * Puts a book the user picked by its ISBN on their shelf, looking it up in the
	 * catalogue first if nobody has had it before.
	 */
	public ShelfBook addByIsbn(String userId, String isbn13) {
		card(isbn13);
		return add(userId, isbn13);
	}

	/**
	 * Takes a book off the user's shelf, and with it what they kept about it. A book the
	 * user is reading with a friend stays.
	 */
	@Transactional
	public void remove(String userId, String isbn13) {
		LibraryBook entry = library.findByUserIdAndIsbn13(userId, isbn13)
				.orElseThrow(() -> ApiError.notFound("NOT_ON_SHELF"));
		events.publishEvent(new ShelfBookLeaving(userId, isbn13));
		library.delete(entry);
		events.publishEvent(new ShelfBookRemoved(userId, isbn13));
	}

	public boolean has(String userId, String isbn13) {
		return library.existsByUserIdAndIsbn13(userId, isbn13);
	}

	/** The user's books, oldest first, which is the order they sit on the shelf. */
	public List<ShelfBook> list(String userId) {
		return library.findByUserIdOrderByAddedAtAscIdAsc(userId).stream()
				.map(entry -> shelfBook(entry.isbn13()))
				.flatMap(Optional::stream)
				.toList();
	}

	/** Books the catalogue finds for some words of a title or an author, best match first. */
	public List<FoundBook> search(String userId, String words) {
		List<Book> found = books.search(words);
		if (found.isEmpty()) {
			return List.of();
		}
		Set<String> mine = library.findByUserIdOrderByAddedAtAscIdAsc(userId).stream().map(LibraryBook::isbn13)
				.collect(Collectors.toSet());
		return found.stream()
				.map(book -> new FoundBook(book.isbn13(), book.title(), book.author(), book.publisher(), book.link(),
						mine.contains(book.isbn13())))
				.toList();
	}

	/**
	 * A book by its ISBN, from the catalogue unless someone has looked it up before.
	 *
	 * @throws ApiError when it is not an ISBN, or the catalogue has no such book
	 */
	public BookCard card(String isbn13) {
		if (!Isbn13.isValid(isbn13)) {
			throw ApiError.badRequest("INVALID_ISBN");
		}
		Book book = books.find(isbn13).orElseThrow(() -> ApiError.notFound("BOOK_NOT_FOUND"));
		return new BookCard(book.isbn13(), book.title(), book.author(), book.publisher(), book.link(),
				"/api/images/" + book.isbn13() + "/cover.png", "/api/images/" + book.isbn13() + "/spine.png");
	}

	private Optional<ShelfBook> shelfBook(String isbn13) {
		return books.cached(isbn13).map(this::toShelfBook);
	}

	private ShelfBook toShelfBook(Book book) {
		return new ShelfBook(book.isbn13(), book.title(), book.author(), book.link(),
				"/api/images/" + book.isbn13() + "/spine.png", images.spineWidth(book.isbn13()));
	}

}
