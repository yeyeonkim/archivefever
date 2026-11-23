package archivefever.server.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * A signed-in browser or app. Kept in the database so that restarting the server does not
 * sign everyone out.
 */
@Entity
@Table(name = "login_session")
class LoginSession {

	/** SHA-256 of the token in the cookie: the database never holds a usable token. */
	@Id
	@Column(name = "token_hash", length = 64)
	private String tokenHash;

	@Column(name = "user_id", nullable = false, length = 64)
	private String userId;

	@Column(nullable = false)
	private Instant createdAt;

	@Column(nullable = false)
	private Instant expiresAt;

	protected LoginSession() {
	}

	LoginSession(String tokenHash, String userId, Instant createdAt, Instant expiresAt) {
		this.tokenHash = tokenHash;
		this.userId = userId;
		this.createdAt = createdAt;
		this.expiresAt = expiresAt;
	}

	String userId() {
		return userId;
	}

	Instant expiresAt() {
		return expiresAt;
	}

}
