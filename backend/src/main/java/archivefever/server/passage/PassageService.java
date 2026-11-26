package archivefever.server.passage;

import archivefever.server.library.LibraryService;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.ResponseStatus;

/** The sentences a reader has collected from the books on their shelf, and the memos written on them. */
@Service
public class PassageService {

	/** A collected passage as the client shows it. {@code page} is null when it was left out. */
	public record PassageView(long id, Integer page, String text) {
	}

	/** A passage with whose it is, for records that show more than one reader's. */
	public record OwnedPassage(long id, String userId, String isbn13, Integer page, String text) {
	}

	/** @param sentence which sentence of the passage the memo is on, counting from 0 */
	public record NoteView(long passageId, String authorId, int sentence, String text) {
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

	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public static class InvalidNote extends RuntimeException {

		InvalidNote(String message) {
			super(message);
		}

	}

	static final int MAX_LENGTH = 2000;

	static final int MAX_PAGE = 9999;

	public static final int MAX_NOTE_LENGTH = 1000;

	/** A passage is a few sentences; this only keeps nonsense out. */
	static final int MAX_SENTENCE = 999;

	/** Reading order: by page, passages without a page last, and in the order collected within a page. */
	private static final Comparator<Passage> READING_ORDER = Comparator
			.comparing(Passage::page, Comparator.nullsLast(Comparator.naturalOrder()))
			.thenComparing(Passage::createdAt)
			.thenComparing(Passage::id);

	private final PassageRepository passages;

	private final PassageNoteRepository notes;

	private final LibraryService library;

	PassageService(PassageRepository passages, PassageNoteRepository notes, LibraryService library) {
		this.passages = passages;
		this.notes = notes;
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

	/**
	 * What these readers collected from one book, in reading order. Whether the caller may
	 * see another reader's passages is for the caller to have settled.
	 *
	 * @param until leaves out what was collected after this moment; null for everything
	 */
	public List<OwnedPassage> collectedBy(Collection<String> userIds, String isbn13, Instant until) {
		return passages.findByUserIdInAndIsbn13(userIds, isbn13).stream()
				.filter(passage -> until == null || !passage.createdAt().isAfter(until))
				.sorted(READING_ORDER)
				.map(PassageService::owned)
				.toList();
	}

	public Optional<OwnedPassage> find(long passageId) {
		return passages.findById(passageId).map(PassageService::owned);
	}

	/**
	 * The memos these authors wrote on these passages.
	 *
	 * @param until leaves out memos written or rewritten after this moment (a memo is kept
	 * as it reads now, not as it read then); null for all of them
	 */
	public List<NoteView> notes(Collection<Long> passageIds, Collection<String> authorIds, Instant until) {
		if (passageIds.isEmpty() || authorIds.isEmpty()) {
			return List.of();
		}
		return notes.findByPassageIdInAndAuthorIdIn(passageIds, authorIds).stream()
				.filter(note -> until == null || !note.updatedAt().isAfter(until))
				.sorted(Comparator.comparing(PassageNote::passageId).thenComparing(PassageNote::sentence))
				.map(note -> new NoteView(note.passageId(), note.authorId(), note.sentence(), note.text()))
				.toList();
	}

	/**
	 * Writes the author's memo on a sentence of a passage, replacing the one they had there;
	 * blank text takes it away. Whether the author may write on this passage is for the
	 * caller to have settled.
	 */
	@Transactional
	public void writeNote(String authorId, long passageId, int sentence, String text) {
		String tidy = text == null ? "" : text.strip();
		if (sentence < 0 || sentence > MAX_SENTENCE) {
			throw new InvalidNote("No such sentence: " + sentence);
		}
		if (tidy.length() > MAX_NOTE_LENGTH) {
			throw new InvalidNote("The memo is longer than " + MAX_NOTE_LENGTH + " characters");
		}
		Optional<PassageNote> existing = notes.findByPassageIdAndAuthorIdAndSentence(passageId, authorId, sentence);
		if (tidy.isEmpty()) {
			existing.ifPresent(notes::delete);
		}
		else if (existing.isPresent()) {
			existing.get().rewrite(tidy, Instant.now());
		}
		else {
			notes.save(new PassageNote(passageId, authorId, sentence, tidy, Instant.now()));
		}
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

	private static OwnedPassage owned(Passage passage) {
		return new OwnedPassage(passage.id(), passage.userId(), passage.isbn13(), passage.page(), passage.text());
	}

}
