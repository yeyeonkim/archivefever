package archivefever.server.notification;

import archivefever.server.book.Book;
import archivefever.server.book.BookService;
import archivefever.server.user.UserService;
import archivefever.server.user.UserService.UserView;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * What each user is told about friend requests and book swaps. The friend and swap
 * services report what happened; this keeps the list each user reads.
 */
@Service
public class NotificationService {

	public enum Kind {

		FRIEND_REQUEST, FRIEND_ACCEPTED, FRIEND_DECLINED, SWAP_REQUEST, SWAP_ACCEPTED, SWAP_DECLINED

	}

	public enum Answer {

		ACCEPTED, DECLINED

	}

	/** @param handle without the "@" */
	public record Person(String handle, String name) {
	}

	public record NotedBook(String isbn13, String title) {
	}

	/**
	 * A notification as the client shows it.
	 *
	 * @param from who sent the request or answered it
	 * @param book the book of a swap; null for friend requests
	 * @param swapId the swap to accept or decline; null for friend requests
	 * @param state for a request the user was sent: PENDING until they answer it, then
	 * ACCEPTED or DECLINED; null for news of an answer
	 */
	public record View(long id, Kind kind, Person from, NotedBook book, Long swapId, String state) {
	}

	/** Old news falls off the end; the requests still open are few. */
	private static final int LIST_LENGTH = 30;

	private final NotificationRepository notifications;

	private final UserService users;

	private final BookService books;

	NotificationService(NotificationRepository notifications, UserService users, BookService books) {
		this.notifications = notifications;
		this.users = users;
		this.books = books;
	}

	/** Newest first. */
	@Transactional(readOnly = true)
	public List<View> list(String userId) {
		List<Notification> latest = notifications.findByUserIdOrderByIdDesc(userId, PageRequest.of(0, LIST_LENGTH));
		Map<String, UserView> actors = users.byId(latest.stream().map(Notification::actorId).distinct().toList());
		return latest.stream()
				// Someone who no longer exists has nothing left to tell.
				.filter(notification -> actors.containsKey(notification.actorId()))
				.map(notification -> view(notification, actors.get(notification.actorId())))
				.toList();
	}

	public void friendRequested(String to, String from) {
		save(to, Kind.FRIEND_REQUEST, from, null, null);
	}

	/** The request's own notification now shows the answer, and whoever asked is told. */
	@Transactional
	public void friendAnswered(String requester, String answerer, boolean accepted) {
		Answer answer = accepted ? Answer.ACCEPTED : Answer.DECLINED;
		notifications.findByUserIdAndKindAndActorIdAndAnswerIsNull(answerer, Kind.FRIEND_REQUEST, requester)
				.forEach(request -> request.answered(answer));
		save(requester, accepted ? Kind.FRIEND_ACCEPTED : Kind.FRIEND_DECLINED, answerer, null, null);
	}

	public void swapRequested(String to, String from, long swapId, String isbn13) {
		save(to, Kind.SWAP_REQUEST, from, swapId, isbn13);
	}

	@Transactional
	public void swapAnswered(String requester, String answerer, long swapId, String isbn13, boolean accepted) {
		Answer answer = accepted ? Answer.ACCEPTED : Answer.DECLINED;
		notifications.findByKindAndSwapIdAndAnswerIsNull(Kind.SWAP_REQUEST, swapId)
				.forEach(request -> request.answered(answer));
		save(requester, accepted ? Kind.SWAP_ACCEPTED : Kind.SWAP_DECLINED, answerer, swapId, isbn13);
	}

	/** The swap is off before it was answered: there is nothing left to accept. */
	@Transactional
	public void swapWithdrawn(long swapId) {
		notifications.deleteAll(notifications.findByKindAndSwapIdAndAnswerIsNull(Kind.SWAP_REQUEST, swapId));
	}

	private void save(String to, Kind kind, String actor, Long swapId, String isbn13) {
		notifications.save(new Notification(to, kind, actor, swapId, isbn13, Instant.now()));
	}

	private View view(Notification notification, UserView actor) {
		boolean request = notification.kind() == Kind.FRIEND_REQUEST || notification.kind() == Kind.SWAP_REQUEST;
		String state = !request ? null : Objects.toString(notification.answer(), "PENDING");
		NotedBook book = notification.isbn13() == null ? null
				: new NotedBook(notification.isbn13(),
						books.cached(notification.isbn13()).map(Book::title).orElse(""));
		return new View(notification.id(), notification.kind(), new Person(actor.handle(), actor.nickname()), book,
				notification.swapId(), state);
	}

}
