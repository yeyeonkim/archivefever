package archivefever.server.friend;

import archivefever.server.notification.NotificationService;
import archivefever.server.user.UserService;
import archivefever.server.user.UserService.UserView;
import archivefever.server.web.ApiError;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Friends: one user asks, the other accepts or declines, and either can end it. Only
 * friends see each other's shelves.
 */
@Service
public class FriendService {

	/** What another user is to the signed-in one. */
	public enum Relation {

		/** Nothing between them. */
		NONE,
		/** The signed-in user has asked and is waiting. */
		REQUESTED,
		/** The other has asked; the signed-in user can accept or decline. */
		INCOMING,
		/** Friends. */
		ADDED

	}

	/**
	 * Another user as the signed-in one sees them.
	 *
	 * @param handle without the "@"; also how the client names this user in its requests
	 */
	public record Person(String handle, String name, Relation status) {
	}

	private static final int SEARCH_RESULTS = 20;

	private final FriendshipRepository friendships;

	private final UserService users;

	private final NotificationService notifications;

	private final ApplicationEventPublisher events;

	FriendService(FriendshipRepository friendships, UserService users, NotificationService notifications,
			ApplicationEventPublisher events) {
		this.friendships = friendships;
		this.users = users;
		this.notifications = notifications;
		this.events = events;
	}

	/** The user's friends, in the order they became friends. */
	@Transactional(readOnly = true)
	public List<Person> friends(String me) {
		List<Friendship> accepted = friendships.findByLowIdOrHighId(me, me).stream()
				.filter(Friendship::accepted)
				.sorted(Comparator.comparing(Friendship::acceptedAt))
				.toList();
		Map<String, UserView> people = users.byId(accepted.stream().map(friendship -> friendship.otherThan(me)).toList());
		return accepted.stream()
				.map(friendship -> people.get(friendship.otherThan(me)))
				.filter(person -> person != null)
				.map(person -> new Person(person.handle(), person.nickname(), Relation.ADDED))
				.toList();
	}

	/** Other users whose name or handle contains the text, each with what they are to this user. */
	@Transactional(readOnly = true)
	public List<Person> search(String me, String text) {
		Map<String, Friendship> mine = friendships.findByLowIdOrHighId(me, me).stream()
				.collect(Collectors.toMap(friendship -> friendship.otherThan(me), Function.identity()));
		// One more than shown, in case the user is among their own results.
		return users.search(text, SEARCH_RESULTS + 1).stream()
				.filter(user -> !user.id().equals(me))
				.limit(SEARCH_RESULTS)
				.map(user -> new Person(user.handle(), user.nickname(), relation(me, mine.get(user.id()))))
				.toList();
	}

	@Transactional
	public Person request(String me, String handle) {
		UserView other = require(handle);
		if (other.id().equals(me)) {
			throw ApiError.badRequest("SELF");
		}
		Optional<Friendship> existing = between(me, other.id());
		if (existing.isPresent()) {
			throw ApiError.conflict(switch (relation(me, existing.get())) {
				case ADDED -> "ALREADY_FRIENDS";
				case INCOMING -> "REQUEST_WAITING";
				default -> "ALREADY_REQUESTED";
			});
		}
		friendships.save(new Friendship(me, other.id(), Instant.now()));
		notifications.friendRequested(other.id(), me);
		return new Person(other.handle(), other.nickname(), Relation.REQUESTED);
	}

	@Transactional
	public Person accept(String me, String handle) {
		UserView other = require(handle);
		Friendship request = incoming(me, other.id());
		request.accept(Instant.now());
		notifications.friendAnswered(other.id(), me, true);
		return new Person(other.handle(), other.nickname(), Relation.ADDED);
	}

	@Transactional
	public Person decline(String me, String handle) {
		UserView other = require(handle);
		friendships.delete(incoming(me, other.id()));
		notifications.friendAnswered(other.id(), me, false);
		return new Person(other.handle(), other.nickname(), Relation.NONE);
	}

	/** Ends a friendship; what the two shared as friends ends with it. */
	@Transactional
	public Person remove(String me, String handle) {
		UserView other = require(handle);
		Friendship friendship = between(me, other.id()).filter(Friendship::accepted)
				.orElseThrow(() -> ApiError.notFound("NOT_FRIENDS"));
		friendships.delete(friendship);
		events.publishEvent(new FriendshipEnded(me, other.id()));
		return new Person(other.handle(), other.nickname(), Relation.NONE);
	}

	public boolean areFriends(String userId, String otherId) {
		return between(userId, otherId).filter(Friendship::accepted).isPresent();
	}

	/**
	 * The user's friend with this handle.
	 * @throws ApiError when there is no such user, or they are not friends
	 */
	public UserView requireFriend(String me, String handle) {
		UserView other = require(handle);
		if (!areFriends(me, other.id())) {
			throw ApiError.forbidden("NOT_FRIENDS");
		}
		return other;
	}

	private UserView require(String handle) {
		return users.findByHandle(handle).orElseThrow(() -> ApiError.notFound("USER_NOT_FOUND"));
	}

	private Optional<Friendship> between(String userId, String otherId) {
		return friendships.findByLowIdAndHighId(Friendship.low(userId, otherId), Friendship.high(userId, otherId));
	}

	/** The other user's request that this user has yet to answer. */
	private Friendship incoming(String me, String otherId) {
		return between(me, otherId).filter(friendship -> relation(me, friendship) == Relation.INCOMING)
				.orElseThrow(() -> ApiError.notFound("NO_REQUEST"));
	}

	private static Relation relation(String me, Friendship friendship) {
		if (friendship == null) {
			return Relation.NONE;
		}
		if (friendship.accepted()) {
			return Relation.ADDED;
		}
		return friendship.requesterId().equals(me) ? Relation.REQUESTED : Relation.INCOMING;
	}

}
