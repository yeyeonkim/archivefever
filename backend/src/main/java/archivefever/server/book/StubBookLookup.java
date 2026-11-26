package archivefever.server.book;

import java.util.List;
import java.util.Optional;

/**
 * Stands in for YES24 when no API key is configured. Every ISBN resolves to an obviously
 * fake book, except {@link #UNKNOWN_ISBN}, which lets the "not found" path be exercised.
 * A search finds the same three fake books whatever the words, except words containing
 * {@link #UNKNOWN_WORDS}, which find nothing.
 */
public class StubBookLookup implements BookLookup {

	public static final String UNKNOWN_ISBN = "9780000000002";

	public static final String UNKNOWN_WORDS = "없는 책";

	/** What a search finds, in this order. */
	public static final List<String> SEARCHED_ISBNS = List.of("9791100000205", "9791100000212", "9791100000229");

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

	@Override
	public List<Book> search(String words) {
		if (words.contains(UNKNOWN_WORDS)) {
			return List.of();
		}
		return SEARCHED_ISBNS.stream().map(isbn13 -> findByIsbn13(isbn13).orElseThrow()).toList();
	}

}
