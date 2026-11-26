package archivefever.server.web;

import archivefever.server.book.BookLookup;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Answers a refused request with its code, in the shape the session filter also uses. */
@RestControllerAdvice
class ApiErrorAdvice {

	private static final Logger log = LoggerFactory.getLogger(ApiErrorAdvice.class);

	@ExceptionHandler(ApiError.class)
	ResponseEntity<Map<String, String>> refused(ApiError error) {
		return ResponseEntity.status(error.status()).body(Map.of("error", error.code()));
	}

	/**
	 * The catalogue could not be asked: it is down or slow (LOOKUP_UNAVAILABLE), or it turned
	 * our key down (CONFIG). Either way the fault is not in the request.
	 */
	@ExceptionHandler(BookLookup.LookupException.class)
	ResponseEntity<Map<String, String>> catalogue(BookLookup.LookupException exception) {
		log.warn("The catalogue could not be asked: {}", exception.getMessage());
		return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("error", exception.failure().name()));
	}

	/**
	 * The database turned a row down. That is what happens when two requests for the same
	 * thing arrive together, such as one friend request sent twice: the services check
	 * first, and a unique key decides between requests that both passed the check.
	 */
	@ExceptionHandler(DataIntegrityViolationException.class)
	ResponseEntity<Map<String, String>> conflict(DataIntegrityViolationException exception) {
		// Logged because anything else the database refuses would be a mistake of ours.
		log.warn("The database refused a row: {}", exception.getMostSpecificCause().getMessage());
		return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "CONFLICT"));
	}

}
