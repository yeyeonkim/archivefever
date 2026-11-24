package archivefever.server.passage;

import archivefever.server.library.LibraryService;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.ResponseStatus;

/** The sentences a reader has collected from the books on their shelf. */
@Service
public class PassageService {

	/** A collected passage as the client shows it. {@code page} is null when it was left out. */
	public record PassageView(long id, Integer page, String text) {
	}

	@ResponseStatus(HttpStatus.NOT_FOUND)
	static class BookNotOnShelf extends RuntimeException {

		BookNotOnShelf(String isbn13) {
			super("Not on the shelf: " + isbn13);
		}

	}

	@ResponseStatus(HttpStatus.BAD_REQUEST)
	static class InvalidPassage extends RuntimeException {

		InvalidPassage(String message) {
			super(message);
		}

	}

	static final int MAX_LENGTH = 2000;

	static final int MAX_PAGE = 9999;

	/** Reading order: by page, passages without a page last, and in the order collected within a page. */
	private static final Comparator<Passage> READING_ORDER = Comparator
			.comparing(Passage::page, Comparator.nullsLast(Comparator.naturalOrder()))
			.thenComparing(Passage::createdAt)
			.thenComparing(Passage::id);

	private final PassageRepository passages;

	private final LibraryService library;

	PassageService(PassageRepository passages, LibraryService library) {
		this.passages = passages;
		this.library = library;
	}

	public List<PassageView> list(String userId, String isbn13) {
		requireOnShelf(userId, isbn13);
		return passages.findByUserIdAndIsbn13(userId, isbn13).stream()
				.sorted(READING_ORDER)
				.map(PassageService::view)
				.toList();
	}

	public PassageView add(String userId, String isbn13, Integer page, String text) {
		requireOnShelf(userId, isbn13);
		String tidy = tidy(text);
		if (tidy.isEmpty()) {
			throw new InvalidPassage("The passage has no text");
		}
		if (tidy.length() > MAX_LENGTH) {
			throw new InvalidPassage("The passage is longer than " + MAX_LENGTH + " characters");
		}
		if (page != null && (page < 1 || page > MAX_PAGE)) {
			throw new InvalidPassage("The page must be between 1 and " + MAX_PAGE);
		}
		return view(passages.save(new Passage(userId, isbn13, page, tidy, Instant.now())));
	}

	/** Recognised text arrives with ragged spacing; a passage is kept as one paragraph. */
	static String tidy(String text) {
		return text == null ? "" : text.replaceAll("\\s+", " ").strip();
	}

	private void requireOnShelf(String userId, String isbn13) {
		if (!library.has(userId, isbn13)) {
			throw new BookNotOnShelf(isbn13);
		}
	}

	private static PassageView view(Passage passage) {
		return new PassageView(passage.id(), passage.page(), passage.text());
	}

}
