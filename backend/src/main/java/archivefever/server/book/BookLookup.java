package archivefever.server.book;

import java.util.Optional;

/** Finds a book in an external catalogue. */
public interface BookLookup {

	/**
	 * @return the book, or empty when the catalogue does not list this ISBN
	 * @throws LookupException when the catalogue could not be asked
	 */
	Optional<Book> findByIsbn13(String isbn13);

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
