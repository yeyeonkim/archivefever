package archivefever.server.book;

import java.util.Optional;

/**
 * Stands in for YES24 when no API key is configured. Every ISBN resolves to an obviously
 * fake book, except {@link #UNKNOWN_ISBN}, which lets the "not found" path be exercised.
 */
public class StubBookLookup implements BookLookup {

	public static final String UNKNOWN_ISBN = "9780000000002";

	@Override
	public String source() {
		return "stub";
	}

	@Override
	public Optional<Book> findByIsbn13(String isbn13) {
		if (UNKNOWN_ISBN.equals(isbn13)) {
			return Optional.empty();
		}
		return Optional.of(new Book(isbn13, null, "스텁 도서 " + isbn13, "YES24 키 미설정", "stub",
				"https://www.yes24.com/", null, null, 280));
	}

}
