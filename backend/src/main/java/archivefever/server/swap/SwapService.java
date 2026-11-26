package archivefever.server.swap;

import archivefever.server.book.Book;
import archivefever.server.book.BookService;
import archivefever.server.friend.FriendService;
import archivefever.server.friend.FriendshipEnded;
import archivefever.server.library.LibraryService;
import archivefever.server.library.ShelfBookLeaving;
import archivefever.server.notification.NotificationService;
import archivefever.server.passage.PassageService;
import archivefever.server.passage.PassageService.OwnedPassage;
import archivefever.server.user.UserService;
import archivefever.server.user.UserService.UserView;
import archivefever.server.web.ApiError;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Book swaps: a user asks a friend to read a book they both have together, and once the
 * friend accepts, each can read what the other collected from it and write memos on it.
 * Until then, and after a refusal, nothing of either reader's record is shown to the other.
 * When the two stop being friends the swap ends: what they had shared by then stays
 * readable to both, as it stood, and nothing newer is shared.
 */
@Service
public class SwapService {

	/** @param handle without the "@" */
	public record Person(String handle, String name) {
	}

	public record SwapBook(String isbn13, String title, String author, String coverUrl) {
	}

	/**
	 * A swap as one of its two users sees it.
	 *
	 * @param requestedByMe whether the signed-in user is the one who asked
	 * @param partner the other user
	 */
	public record SwapView(long id, BookSwap.Status status, boolean requestedByMe, Person partner, SwapBook book) {
	}

	/** @param by the handle of whoever collected it */
	public record RecordPassage(long id, Integer page, String text, String by, boolean mine) {
	}

	/**
	 * A reader's marks on one sentence. The user's own come with their underline and
	 * highlight; the friend's are here for their memo, and only when there is one.
	 *
	 * @param sentence which sentence of the passage it is on, counting from 0
	 * @param text the memo; empty when the sentence is only underlined or highlighted
	 * @param by the handle of whoever made it
	 */
	public record RecordNote(long passageId, int sentence, String text, boolean underlined, boolean highlighted,
			String by, boolean mine) {
	}

	/** What the two readers of a swap collected from the book, in reading order, and their memos. */
	public record Records(long id, Person me, Person partner, SwapBook book, List<RecordPassage> passages,
			List<RecordNote> notes) {
	}

	private final BookSwapRepository swaps;

	private final FriendService friends;

	private final LibraryService library;

	private final PassageService passages;

	private final NotificationService notifications;

	private final UserService users;

	private final BookService books;

	SwapService(BookSwapRepository swaps, FriendService friends, LibraryService library, PassageService passages,
			NotificationService notifications, UserService users, BookService books) {
		this.swaps = swaps;
		this.friends = friends;
		this.library = library;
		this.passages = passages;
		this.notifications = notifications;
		this.users = users;
		this.books = books;
	}

	/** The friend's books that the user has too: only those can be read together. */
	@Transactional(readOnly = true)
	public List<LibraryService.ShelfBook> candidates(String me, String friendHandle) {
		UserView friend = friends.requireFriend(me, friendHandle);
		Set<String> mine = library.list(me).stream().map(LibraryService.ShelfBook::isbn13).collect(Collectors.toSet());
		return library.list(friend.id()).stream().filter(book -> mine.contains(book.isbn13())).toList();
	}

	/**
	 * The swaps the user is reading, those they asked for that wait for an answer, and
	 * those that ended with a friendship, whose records are kept. Requests they were sent
	 * reach them as notifications instead.
	 */
	@Transactional(readOnly = true)
	public List<SwapView> list(String me) {
		List<BookSwap> mine = swaps.findByRequesterIdOrAddresseeId(me, me);
		// Of the times a pair read a book and parted, the last record holds the most. And
		// once they are reading it again, the new swap shows everything the old record did.
		Set<String> reading = mine.stream().filter(swap -> swap.status() == BookSwap.Status.ACTIVE)
				.map(BookSwap::pairKey).collect(Collectors.toSet());
		Map<String, BookSwap> kept = mine.stream()
				.filter(swap -> swap.status() == BookSwap.Status.ENDED && !reading.contains(swap.pairKey()))
				.collect(Collectors.toMap(BookSwap::pairKey, swap -> swap,
						(one, other) -> one.endedAt().isAfter(other.endedAt()) ? one : other));
		List<BookSwap> shown = mine.stream()
				.filter(swap -> swap.status() == BookSwap.Status.ACTIVE
						|| (swap.status() == BookSwap.Status.PENDING && swap.requesterId().equals(me))
						|| kept.get(swap.pairKey()) == swap)
				.sorted(Comparator.comparing(BookSwap::requestedAt).thenComparing(BookSwap::id))
				.toList();
		Map<String, UserView> partners = users.byId(shown.stream().map(swap -> swap.otherThan(me)).distinct().toList());
		return shown.stream()
				.filter(swap -> partners.containsKey(swap.otherThan(me)))
				.map(swap -> view(swap, me, partners.get(swap.otherThan(me))))
				.toList();
	}

	@Transactional
	public SwapView request(String me, String friendHandle, String isbn13) {
		UserView friend = friends.requireFriend(me, friendHandle);
		if (isbn13 == null || !library.has(me, isbn13) || !library.has(friend.id(), isbn13)) {
			throw ApiError.conflict("NOT_COMMON");
		}
		if (swaps.existsByOpenKey(BookSwap.openKey(me, friend.id(), isbn13))) {
			throw ApiError.conflict("ALREADY_SWAPPING");
		}
		BookSwap swap = swaps.save(new BookSwap(me, friend.id(), isbn13, Instant.now()));
		notifications.swapRequested(friend.id(), me, swap.id(), isbn13);
		return view(swap, me, friend);
	}

	/**
	 * Starts the swap. Both must still have the book: if one has taken it off their shelf
	 * since the request, the request stays as it is, to be accepted once the book is back.
	 */
	@Transactional
	public SwapView accept(String me, long id) {
		BookSwap swap = toAnswer(me, id);
		if (!library.has(swap.requesterId(), swap.isbn13()) || !library.has(swap.addresseeId(), swap.isbn13())) {
			throw ApiError.conflict("BOOK_MISSING");
		}
		swap.accept(Instant.now());
		notifications.swapAnswered(swap.requesterId(), me, swap.id(), swap.isbn13(), true);
		return view(swap, me, user(swap.requesterId()));
	}

	@Transactional
	public SwapView decline(String me, long id) {
		BookSwap swap = toAnswer(me, id);
		swap.decline(Instant.now());
		notifications.swapAnswered(swap.requesterId(), me, swap.id(), swap.isbn13(), false);
		return view(swap, me, user(swap.requesterId()));
	}

	/**
	 * Both readers' passages from the book and their memos on them: as they are now while
	 * the swap is being read, and as they stood when it ended once it has.
	 */
	@Transactional(readOnly = true)
	public Records records(String me, long id) {
		BookSwap swap = own(me, id);
		if (swap.status() != BookSwap.Status.ACTIVE && swap.status() != BookSwap.Status.ENDED) {
			throw ApiError.conflict("NOT_ACTIVE");
		}
		// Null while the swap is being read: nothing is left out.
		Instant until = swap.endedAt();
		UserView self = user(me);
		UserView partner = user(swap.otherThan(me));
		List<OwnedPassage> collected = passages.collectedBy(List.of(me, partner.id()), swap.isbn13(), until);
		Map<String, String> handles = Map.of(me, self.handle(), partner.id(), partner.handle());
		List<RecordPassage> shown = collected.stream()
				.map(passage -> new RecordPassage(passage.id(), passage.page(), passage.text(),
						handles.get(passage.userId()), passage.userId().equals(me)))
				.toList();
		List<RecordNote> notes = passages.marks(collected.stream().map(OwnedPassage::id).toList(), handles.keySet(), until).stream()
				// What a friend underlined for themselves is not shown; what they wrote is.
				.filter(mark -> mark.authorId().equals(me) || !mark.note().isEmpty())
				.map(mark -> new RecordNote(mark.passageId(), mark.sentence(), mark.note(), mark.underlined(),
						mark.highlighted(), handles.get(mark.authorId()), mark.authorId().equals(me)))
				.toList();
		return new Records(swap.id(), person(self), person(partner), book(swap.isbn13()), shown, notes);
	}

	/**
	 * Sets how the user marks a sentence of a passage of this swap, one of theirs or one of
	 * their partner's: underlined or not, highlighted or not, and their memo (blank for
	 * none). Only ever the user's own marks, and only while the swap is being read: a record
	 * kept from an ended swap is not written on.
	 */
	@Transactional
	public void mark(String me, long id, long passageId, int sentence, boolean underlined, boolean highlighted,
			String text) {
		BookSwap swap = active(me, id);
		OwnedPassage passage = passages.find(passageId)
				.filter(found -> swap.involves(found.userId()) && found.isbn13().equals(swap.isbn13()))
				.orElseThrow(() -> ApiError.notFound("PASSAGE_NOT_FOUND"));
		passages.mark(me, passage.id(), sentence, underlined, highlighted, text);
	}

	/**
	 * A book that is being read with a friend stays on the shelf: the swap is of a book
	 * both have. A request that is only waiting does not hold the book; accepting it looks
	 * again at who has it.
	 */
	@EventListener
	void on(ShelfBookLeaving leaving) {
		boolean reading = swaps.findByRequesterIdOrAddresseeId(leaving.userId(), leaving.userId()).stream()
				.anyMatch(swap -> swap.status() == BookSwap.Status.ACTIVE && swap.isbn13().equals(leaving.isbn13()));
		if (reading) {
			throw ApiError.conflict("SWAPPING");
		}
	}

	/**
	 * Two users stopped being friends. A swap they were reading ends, keeping what they had
	 * shared until now; a request that was still waiting is taken back.
	 */
	@EventListener
	@Transactional
	void on(FriendshipEnded ended) {
		Instant now = Instant.now();
		for (BookSwap swap : swaps.findBetween(ended.userId(), ended.otherId())) {
			if (!swap.open()) {
				continue;
			}
			if (swap.status() == BookSwap.Status.PENDING) {
				notifications.swapWithdrawn(swap.id());
			}
			swap.end(now);
		}
	}

	/** A swap of the user's; to anyone else it does not exist. */
	private BookSwap own(String me, long id) {
		return swaps.findById(id).filter(swap -> swap.involves(me))
				.orElseThrow(() -> ApiError.notFound("SWAP_NOT_FOUND"));
	}

	/** A request that is this user's to answer: sent to them, and not answered yet. */
	private BookSwap toAnswer(String me, long id) {
		BookSwap swap = own(me, id);
		if (!swap.addresseeId().equals(me)) {
			throw ApiError.forbidden("NOT_YOURS_TO_ANSWER");
		}
		if (swap.status() != BookSwap.Status.PENDING) {
			throw ApiError.conflict("ALREADY_ANSWERED");
		}
		return swap;
	}

	/** A swap of the user's that is being read: the only kind that can be written on. */
	private BookSwap active(String me, long id) {
		BookSwap swap = own(me, id);
		if (swap.status() != BookSwap.Status.ACTIVE) {
			throw ApiError.conflict("NOT_ACTIVE");
		}
		return swap;
	}

	private UserView user(String id) {
		return users.find(id).orElseThrow(() -> ApiError.notFound("USER_NOT_FOUND"));
	}

	private SwapView view(BookSwap swap, String me, UserView partner) {
		return new SwapView(swap.id(), swap.status(), swap.requesterId().equals(me), person(partner), book(swap.isbn13()));
	}

	private SwapBook book(String isbn13) {
		Book book = books.cached(isbn13).orElse(null);
		return new SwapBook(isbn13, book == null ? "" : book.title(), book == null ? "" : book.author(),
				"/api/images/" + isbn13 + "/cover.png");
	}

	private static Person person(UserView user) {
		return new Person(user.handle(), user.nickname());
	}

}
