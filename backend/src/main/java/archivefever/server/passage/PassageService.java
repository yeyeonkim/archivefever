package archivefever.server.passage;

import archivefever.server.library.LibraryService;
import archivefever.server.library.ShelfBookRemoved;
import archivefever.server.web.ApiError;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * The sentences a reader has collected from the books on their shelf, and what readers
 * have marked on them: underlines, highlights and memos.
 */
@Service
public class PassageService {

	/**
	 * One sentence of a passage as its reader has marked it.
	 *
	 * @param sentence which sentence of the passage, counting from 0
	 * @param note the memo; empty when there is none
	 */
	public record MarkView(int sentence, boolean underlined, boolean highlighted, String note) {
	}

	/**
	 * A collected passage as the client shows it to whose it is.
	 *
	 * @param page null when it was left out
	 * @param marks the reader's own marks on its sentences
	 */
	public record PassageView(long id, Integer page, String text, List<MarkView> marks) {
	}

	/** A passage with whose it is, for records that show more than one reader's. */
	public record OwnedPassage(long id, String userId, String isbn13, Integer page, String text) {
	}

	/** A mark with whose it is and which passage it is on. */
	public record OwnedMark(long passageId, String authorId, int sentence, boolean underlined, boolean highlighted,
			String note) {
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

	private final PassageNoteRepository marks;

	private final LibraryService library;

	PassageService(PassageRepository passages, PassageNoteRepository marks, LibraryService library) {
		this.passages = passages;
		this.marks = marks;
		this.library = library;
	}

	/** The reader's passages from a book on their shelf, in reading order, each with their marks on it. */
	public List<PassageView> list(String userId, String isbn13) {
		requireOnShelf(userId, isbn13);
		List<Passage> collected = passages.findByUserIdAndIsbn13(userId, isbn13).stream().sorted(READING_ORDER).toList();
		Map<Long, List<MarkView>> mine = collected.isEmpty() ? Map.of()
				: marks.findByPassageIdInAndAuthorIdIn(collected.stream().map(Passage::id).toList(), List.of(userId)).stream()
						.sorted(Comparator.comparing(PassageNote::sentence))
						.collect(Collectors.groupingBy(PassageNote::passageId,
								Collectors.mapping(PassageService::view, Collectors.toList())));
		return collected.stream().map(passage -> view(passage, mine.getOrDefault(passage.id(), List.of()))).toList();
	}

	public PassageView add(String userId, String isbn13, Integer page, String text) {
		requireOnShelf(userId, isbn13);
		String tidy = checked(page, text);
		return view(passages.save(new Passage(userId, isbn13, page, tidy, Instant.now())), List.of());
	}

	/**
	 * Rewrites a passage of the reader's own: its text, its page, or both. The marks on its
	 * sentences stay where they are, by the number of the sentence.
	 */
	@Transactional
	public PassageView update(String userId, String isbn13, long id, Integer page, String text) {
		Passage passage = own(userId, isbn13, id);
		passage.rewrite(page, checked(page, text));
		return view(passage, marks.findByPassageIdInAndAuthorIdIn(List.of(id), List.of(userId)).stream()
				.sorted(Comparator.comparing(PassageNote::sentence)).map(PassageService::view).toList());
	}

	/** Deletes a passage of the reader's own, with everything anyone has marked on it. */
	@Transactional
	public void delete(String userId, String isbn13, long id) {
		Passage passage = own(userId, isbn13, id);
		marks.deleteAll(marks.findByPassageIdIn(List.of(id)));
		passages.delete(passage);
	}

	/** Sets how the reader marks a sentence of a passage of their own. */
	@Transactional
	public void markOwn(String userId, String isbn13, long id, int sentence, boolean underlined, boolean highlighted,
			String note) {
		mark(userId, own(userId, isbn13, id).id(), sentence, underlined, highlighted, note);
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
	 * What these readers have marked on these passages.
	 *
	 * @param until leaves out marks made or changed after this moment (a mark is kept as it
	 * is now, not as it was then); null for all of them
	 */
	public List<OwnedMark> marks(Collection<Long> passageIds, Collection<String> authorIds, Instant until) {
		if (passageIds.isEmpty() || authorIds.isEmpty()) {
			return List.of();
		}
		return marks.findByPassageIdInAndAuthorIdIn(passageIds, authorIds).stream()
				.filter(mark -> until == null || !mark.updatedAt().isAfter(until))
				.sorted(Comparator.comparing(PassageNote::passageId).thenComparing(PassageNote::sentence))
				.map(mark -> new OwnedMark(mark.passageId(), mark.authorId(), mark.sentence(), mark.underlined(),
						mark.highlighted(), mark.text()))
				.toList();
	}

	/**
	 * Sets how the author marks a sentence of a passage from now on: underlined or not,
	 * highlighted or not, and their memo (blank for none). With nothing left, the mark is
	 * gone. Whether the author may mark this passage is for the caller to have settled.
	 */
	@Transactional
	public void mark(String authorId, long passageId, int sentence, boolean underlined, boolean highlighted, String note) {
		String tidy = note == null ? "" : note.strip();
		if (sentence < 0 || sentence > MAX_SENTENCE) {
			throw new InvalidNote("No such sentence: " + sentence);
		}
		if (tidy.length() > MAX_NOTE_LENGTH) {
			throw new InvalidNote("The memo is longer than " + MAX_NOTE_LENGTH + " characters");
		}
		Optional<PassageNote> existing = marks.findByPassageIdAndAuthorIdAndSentence(passageId, authorId, sentence);
		if (!underlined && !highlighted && tidy.isEmpty()) {
			existing.ifPresent(marks::delete);
			return;
		}
		PassageNote mark = existing.orElseGet(() -> new PassageNote(passageId, authorId, sentence));
		mark.set(underlined, highlighted, tidy, Instant.now());
		marks.save(mark);
	}

	/** A book has left a reader's shelf: their passages from it go too, with the marks on them. */
	@EventListener
	@Transactional
	void on(ShelfBookRemoved removed) {
		List<Passage> collected = passages.findByUserIdAndIsbn13(removed.userId(), removed.isbn13());
		if (collected.isEmpty()) {
			return;
		}
		marks.deleteAll(marks.findByPassageIdIn(collected.stream().map(Passage::id).toList()));
		passages.deleteAll(collected);
	}

	/** Recognised text arrives with ragged spacing; a passage is kept as one paragraph. */
	static String tidy(String text) {
		return text == null ? "" : text.replaceAll("\\s+", " ").strip();
	}

	/** The text made tidy, once it and the page are found acceptable. */
	private static String checked(Integer page, String text) {
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
		return tidy;
	}

	private void requireOnShelf(String userId, String isbn13) {
		if (!library.has(userId, isbn13)) {
			throw new BookNotOnShelf(isbn13);
		}
	}

	/** A passage of this reader's from this book; to anyone else it does not exist. */
	private Passage own(String userId, String isbn13, long id) {
		return passages.findById(id)
				.filter(passage -> passage.userId().equals(userId) && passage.isbn13().equals(isbn13))
				.orElseThrow(() -> ApiError.notFound("PASSAGE_NOT_FOUND"));
	}

	private static PassageView view(Passage passage, List<MarkView> marks) {
		return new PassageView(passage.id(), passage.page(), passage.text(), marks);
	}

	private static MarkView view(PassageNote mark) {
		return new MarkView(mark.sentence(), mark.underlined(), mark.highlighted(), mark.text());
	}

	private static OwnedPassage owned(Passage passage) {
		return new OwnedPassage(passage.id(), passage.userId(), passage.isbn13(), passage.page(), passage.text());
	}

}
