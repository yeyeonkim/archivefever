package archivefever.server.book;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

/** Books by ISBN, answered from the local cache when possible. */
@Service
public class BookService {

	/** Nobody types more of a title than this; it only keeps nonsense from being sent on. */
	private static final int MAX_SEARCH_LENGTH = 100;

	private final BookLookup lookup;

	private final CachedBookRepository cache;

	private final ApplicationEventPublisher events;

	BookService(BookLookup lookup, CachedBookRepository cache, ApplicationEventPublisher events) {
		this.lookup = lookup;
		this.cache = cache;
		this.events = events;
	}

	/**
	 * @return the book, or empty when the catalogue does not list it
	 * @throws BookLookup.LookupException when it is not cached and the catalogue could not be asked
	 */
	public Optional<Book> find(String isbn13) {
		Optional<CachedBook> cached = cache.findById(isbn13);
		// A row from another source is stale: stub books cached while no API key was set
		// must not be served once the real catalogue is available.
		if (cached.isPresent() && lookup.source().equals(cached.get().source())) {
			return cached.map(CachedBook::toBook);
		}
		Optional<Book> found = lookup.findByIsbn13(isbn13);
		if (found.isPresent()) {
			cache.save(new CachedBook(found.get(), lookup.source(), Instant.now()));
			if (cached.isPresent()) {
				events.publishEvent(new BookReplaced(isbn13));
			}
		}
		return found;
	}

	/**
	 * Books the catalogue finds for some words of a title or an author. Nothing of the
	 * answer is kept: a book is cached once someone picks it and it is looked up by ISBN.
	 *
	 * @throws BookLookup.LookupException when the catalogue could not be asked
	 */
	public List<Book> search(String words) {
		String tidy = words == null ? "" : words.strip();
		if (tidy.isEmpty()) {
			return List.of();
		}
		return lookup.search(tidy.length() > MAX_SEARCH_LENGTH ? tidy.substring(0, MAX_SEARCH_LENGTH) : tidy);
	}

	/**
	 * The book as last looked up; never contacts the catalogue. May be older data from
	 * another source until the book is looked up again.
	 */
	public Optional<Book> cached(String isbn13) {
		return cache.findById(isbn13).map(CachedBook::toBook);
	}

}
