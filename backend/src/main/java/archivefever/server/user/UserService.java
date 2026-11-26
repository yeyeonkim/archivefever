package archivefever.server.user;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.ResponseStatus;

/** The people who use the server: created the first time they sign in. */
@Service
public class UserService {

	/**
	 * A user. To the user themselves all of it may be shown; of other users a client is
	 * told the nickname and the handle only, never the id, which for a Kakao user contains
	 * their member number.
	 *
	 * @param handle the name others find this user by, without the "@"
	 */
	public record UserView(String id, String nickname, String provider, String handle) {
	}

	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public static class InvalidName extends RuntimeException {

		InvalidName() {
			super("A name is 2 to 20 lower-case letters, digits or underscores");
		}

	}

	public static final String KAKAO = "kakao";

	public static final String DEVELOPMENT = "dev";

	static final int HANDLE_LENGTH = 20;

	/** Development names become user ids, and so parts of nothing but plain text. */
	private static final Pattern NAME = Pattern.compile("[a-z0-9_]{2,20}");

	private static final int NICKNAME_LENGTH = 40;

	/** Start of the handles the server makes up, as in "@reader4821". */
	private static final String HANDLE_STEM = "reader";

	private final AppUserRepository users;

	private final SecureRandom random = new SecureRandom();

	UserService(AppUserRepository users) {
		this.users = users;
	}

	public Optional<UserView> find(String id) {
		return users.findById(id).map(UserService::view);
	}

	/** @param handle as typed or as shown: with or without the "@", in any case */
	public Optional<UserView> findByHandle(String handle) {
		String tidy = tidyHandle(handle);
		return tidy.isEmpty() ? Optional.empty() : users.findByHandle(tidy).map(UserService::view);
	}

	/** The users with these ids, by id. */
	public Map<String, UserView> byId(Collection<String> ids) {
		return users.findAllById(ids).stream().map(UserService::view)
				.collect(Collectors.toMap(UserView::id, Function.identity()));
	}

	/**
	 * Users whose nickname or handle contains the text, by handle. Text starting with "@"
	 * is looked for in handles only.
	 */
	public List<UserView> search(String text, int limit) {
		String query = text == null ? "" : text.strip();
		boolean handlesOnly = query.startsWith("@");
		String part = handlesOnly ? tidyHandle(query) : query;
		if (part.isEmpty()) {
			return List.of();
		}
		PageRequest page = PageRequest.of(0, limit);
		List<AppUser> found = handlesOnly ? users.findByHandleContainingIgnoreCaseOrderByHandleAsc(part, page)
				: users.findByNicknameContainingIgnoreCaseOrHandleContainingIgnoreCaseOrderByHandleAsc(part, part, page);
		return found.stream().filter(user -> user.handle() != null).map(UserService::view).toList();
	}

	/** Signs in whoever Kakao says this is; the member number identifies them across sign-ins. */
	public UserView signInWithKakao(long memberNumber, String nickname) {
		String name = nickname == null || nickname.isBlank() ? "카카오 사용자" : nickname.strip();
		return signIn(KAKAO + memberNumber, KAKAO, name.length() > NICKNAME_LENGTH ? name.substring(0, NICKNAME_LENGTH) : name,
				null);
	}

	/**
	 * Signs in by name alone, for development without a Kakao key. The name is the user id,
	 * so "demo" is the user everything belonged to before there were accounts.
	 */
	public UserView signInForDevelopment(String name) {
		String tidy = name == null ? "" : name.strip().toLowerCase(Locale.ROOT);
		// A name starting with "kakao" could be taken for a Kakao member's id.
		if (!NAME.matcher(tidy).matches() || tidy.startsWith(KAKAO)) {
			throw new InvalidName();
		}
		return signIn(tidy, DEVELOPMENT, tidy, tidy);
	}

	/**
	 * Users from before handles existed get one when the server starts, so that they can be
	 * found by people looking for friends without having to sign in again first.
	 */
	@EventListener(ApplicationReadyEvent.class)
	void giveEveryoneAHandle() {
		for (AppUser user : users.findByHandleIsNull()) {
			user.assignHandle(newHandle(DEVELOPMENT.equals(user.provider()) ? user.id() : null));
			users.save(user);
		}
	}

	/** @param wantedHandle the handle to give a new user if nobody has it, or null to make one up */
	private UserView signIn(String id, String provider, String nickname, String wantedHandle) {
		Instant now = Instant.now();
		AppUser user = users.findById(id).orElse(null);
		if (user == null) {
			user = new AppUser(id, provider, nickname, newHandle(wantedHandle), now);
		}
		else {
			user.signedInAgain(nickname, now);
			if (user.handle() == null) {
				user.assignHandle(newHandle(wantedHandle));
			}
		}
		return view(users.save(user));
	}

	/**
	 * A handle nobody has yet. People cannot choose theirs until there is a sign-up step;
	 * a Kakao nickname will not do, as it is usually not in the letters a handle is made of.
	 */
	private String newHandle(String wanted) {
		if (wanted != null && NAME.matcher(wanted).matches() && !users.existsByHandle(wanted)) {
			return wanted;
		}
		// Four digits to begin with; more once those are getting used up.
		for (int digits = 4;; digits++) {
			int range = (int) Math.pow(10, digits);
			for (int attempt = 0; attempt < 20; attempt++) {
				String handle = HANDLE_STEM + String.format("%0" + digits + "d", random.nextInt(range));
				if (!users.existsByHandle(handle)) {
					return handle;
				}
			}
		}
	}

	private static String tidyHandle(String handle) {
		String tidy = handle == null ? "" : handle.strip().toLowerCase(Locale.ROOT);
		return tidy.startsWith("@") ? tidy.substring(1) : tidy;
	}

	private static UserView view(AppUser user) {
		return new UserView(user.id(), user.nickname(), user.provider(), user.handle());
	}

}
