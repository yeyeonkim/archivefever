package archivefever.server.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Sessions: a random token handed to the client in a cookie, good until it expires or is ended. */
@Service
public class SessionService {

	private final LoginSessionRepository sessions;

	private final Duration timeToLive;

	private final SecureRandom random = new SecureRandom();

	SessionService(LoginSessionRepository sessions, @Value("${archivefever.auth.session-ttl}") Duration timeToLive) {
		this.sessions = sessions;
		this.timeToLive = timeToLive;
	}

	public Duration timeToLive() {
		return timeToLive;
	}

	/** Starts a session for the user and returns its token, which is not stored anywhere else. */
	public String start(String userId) {
		Instant now = Instant.now();
		// Signing in is rare enough to be the moment for clearing out what has expired.
		sessions.deleteByExpiresAtBefore(now);
		byte[] bytes = new byte[32];
		random.nextBytes(bytes);
		String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
		sessions.save(new LoginSession(hash(token), userId, now, now.plus(timeToLive)));
		return token;
	}

	/** The user the token belongs to, if it names a session that has not expired. */
	public Optional<String> userOf(String token) {
		if (token == null || token.isBlank()) {
			return Optional.empty();
		}
		return sessions.findById(hash(token))
				.filter(session -> session.expiresAt().isAfter(Instant.now()))
				.map(LoginSession::userId);
	}

	public void end(String token) {
		if (token != null && !token.isBlank()) {
			sessions.deleteById(hash(token));
		}
	}

	private static String hash(String token) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
					.digest(token.getBytes(StandardCharsets.UTF_8)));
		}
		catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException(exception);
		}
	}

}
