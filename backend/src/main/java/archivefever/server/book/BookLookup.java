package archivefever.server.book;

import java.util.List;
import java.util.Optional;

/** Finds a book in an external catalogue. */
public interface BookLookup {

	/**
	 * @return the book, or empty when the catalogue does not list this ISBN
	 * @throws LookupException when the catalogue could not be asked
	 */
	Optional<Book> findByIsbn13(String isbn13);

	/**
	 * Paper books the catalogue finds for some words of a title or an author, best match
	 * first; never more than {@link #SEARCH_RESULTS}. Books without an ISBN-13 (sets of
	 * several books, mostly) are left out, as a shelf could not hold them.
	 *
	 * @return the books, empty when nothing matches
	 * @throws LookupException when the catalogue could not be asked
	 */
	List<Book> search(String words);

	int SEARCH_RESULTS = 20;

	/**
	 * Short name of where the answers come from. Stored with cached books so that data from
	 * one source (the stub, say) is not served once another source is configured.
	 */
	String source();

	/** Why a lookup could not be completed, as reported to the client. */
	enum Failure {
		/** The API key is missing or rejected; someone has to fix the server configuration. */
		CONFIG,
		/** The catalogue is down, slow or rate limiting; trying again later may work. */
		LOOKUP_UNAVAILABLE
	}

	class LookupException extends RuntimeException {

		private final Failure failure;

		public LookupException(Failure failure, String message, Throwable cause) {
			super(message, cause);
			this.failure = failure;
		}

		public Failure failure() {
			return failure;
		}

	}

}
