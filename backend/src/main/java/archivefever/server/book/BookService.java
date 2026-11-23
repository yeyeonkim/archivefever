package archivefever.server.book;

import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Service;

/** Books by ISBN, answered from the local cache when possible. */
@Service
public class BookService {

	private final BookLookup lookup;

	private final CachedBookRepository cache;

	BookService(BookLookup lookup, CachedBookRepository cache) {
		this.lookup = lookup;
		this.cache = cache;
	}

	/**
	 * @return the book, or empty when the catalogue does not list it
	 * @throws BookLookup.LookupException when it is not cached and the catalogue could not be asked
	 */
	public Optional<Book> find(String isbn13) {
		Optional<Book> cached = cached(isbn13);
		if (cached.isPresent()) {
			return cached;
		}
		Optional<Book> found = lookup.findByIsbn13(isbn13);
		found.ifPresent(book -> cache.save(new CachedBook(book, Instant.now())));
		return found;
	}

	/** The book if it has been looked up before; never contacts the catalogue. */
	public Optional<Book> cached(String isbn13) {
		return cache.findById(isbn13).map(CachedBook::toBook);
	}

}
