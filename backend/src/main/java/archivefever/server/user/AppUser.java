package archivefever.server.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;

/** Someone who has signed in. Their shelf, scans and collected passages carry this id. */
@Entity
@Table(name = "app_user", indexes = @Index(name = "app_user_handle", columnList = "handle", unique = true))
class AppUser {

	/** "kakao" followed by the Kakao member number, or the name typed at a development sign-in. */
	@Id
	@Column(length = 64)
	private String id;

	@Column(nullable = false, length = 16)
	private String provider;

	@Column(nullable = false, length = 40)
	private String nickname;

	/**
	 * The name other users find and see this one by, written with an "@" in front on
	 * screen. Unlike the id it says nothing about the account behind it. Null only on rows
	 * from before handles existed, until the server next starts.
	 */
	@Column(length = UserService.HANDLE_LENGTH)
	private String handle;

	@Column(nullable = false)
	private Instant createdAt;

	@Column(nullable = false)
	private Instant lastSignInAt;

	protected AppUser() {
	}

	AppUser(String id, String provider, String nickname, String handle, Instant now) {
		this.id = id;
		this.provider = provider;
		this.nickname = nickname;
		this.handle = handle;
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

	String handle() {
		return handle;
	}

	void assignHandle(String handle) {
		this.handle = handle;
	}

	/** A returning user: the name may have changed at the provider since last time. */
	void signedInAgain(String nickname, Instant now) {
		this.nickname = nickname;
		this.lastSignInAt = now;
	}

}
