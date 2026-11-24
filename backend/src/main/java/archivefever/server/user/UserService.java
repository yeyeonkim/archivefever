package archivefever.server.user;

import java.time.Instant;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.ResponseStatus;

/** The people who use the server: created the first time they sign in. */
@Service
public class UserService {

	/** A user as the client shows them. */
	public record UserView(String id, String nickname, String provider) {
	}

	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public static class InvalidName extends RuntimeException {

		InvalidName() {
			super("A name is 2 to 20 lower-case letters, digits or underscores");
		}

	}

	public static final String KAKAO = "kakao";

	public static final String DEVELOPMENT = "dev";

	/** Development names become user ids, and so parts of nothing but plain text. */
	private static final Pattern NAME = Pattern.compile("[a-z0-9_]{2,20}");

	private static final int NICKNAME_LENGTH = 40;

	private final AppUserRepository users;

	UserService(AppUserRepository users) {
		this.users = users;
	}

	public Optional<UserView> find(String id) {
		return users.findById(id).map(UserService::view);
	}

	/** Signs in whoever Kakao says this is; the member number identifies them across sign-ins. */
	public UserView signInWithKakao(long memberNumber, String nickname) {
		String name = nickname == null || nickname.isBlank() ? "카카오 사용자" : nickname.strip();
		return signIn(KAKAO + memberNumber, KAKAO, name.length() > NICKNAME_LENGTH ? name.substring(0, NICKNAME_LENGTH) : name);
	}

	/**
	 * Signs in by name alone, for development without a Kakao key. The name is the user id,
	 * so "demo" is the user everything belonged to before there were accounts.
	 */
	public UserView signInForDevelopment(String name) {
		String tidy = name == null ? "" : name.strip().toLowerCase();
		// A name starting with "kakao" could be taken for a Kakao member's id.
		if (!NAME.matcher(tidy).matches() || tidy.startsWith(KAKAO)) {
			throw new InvalidName();
		}
		return signIn(tidy, DEVELOPMENT, tidy);
	}

	private UserView signIn(String id, String provider, String nickname) {
		Instant now = Instant.now();
		AppUser user = users.findById(id).orElse(null);
		if (user == null) {
			user = new AppUser(id, provider, nickname, now);
		}
		else {
			user.signedInAgain(nickname, now);
		}
		return view(users.save(user));
	}

	private static UserView view(AppUser user) {
		return new UserView(user.id(), user.nickname(), user.provider());
	}

}
