package archivefever.server.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** Someone who has signed in. Their shelf, scans and collected passages carry this id. */
@Entity
@Table(name = "app_user")
class AppUser {

	/** "kakao" followed by the Kakao member number, or the name typed at a development sign-in. */
	@Id
	@Column(length = 64)
	private String id;

	@Column(nullable = false, length = 16)
	private String provider;

	@Column(nullable = false, length = 40)
	private String nickname;

	@Column(nullable = false)
	private Instant createdAt;

	@Column(nullable = false)
	private Instant lastSignInAt;

	protected AppUser() {
	}

	AppUser(String id, String provider, String nickname, Instant now) {
		this.id = id;
		this.provider = provider;
		this.nickname = nickname;
		this.createdAt = now;
		this.lastSignInAt = now;
	}

	String id() {
		return id;
	}

	String provider() {
		return provider;
	}

	String nickname() {
		return nickname;
	}

	/** A returning user: the name may have changed at the provider since last time. */
	void signedInAgain(String nickname, Instant now) {
		this.nickname = nickname;
		this.lastSignInAt = now;
	}

}
