package archivefever.server.notification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Something another user did that this user is told about: a request, or the answer to one. */
@Entity
@Table(name = "notification", indexes = @Index(name = "notification_user", columnList = "user_id"))
class Notification {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	/** Who is told. */
	@Column(name = "user_id", nullable = false, length = 64)
	private String userId;

	// Kept as text, not as the database's own enumeration, so that a kind added later needs no change to the table.
	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 24)
	private NotificationService.Kind kind;

	/** Who did it: sent the request, or answered it. */
	@Column(name = "actor_id", nullable = false, length = 64)
	private String actorId;

	/** The book swap this is about; null for friend requests. */
	@Column(name = "swap_id")
	private Long swapId;

	/** The book of that swap. */
	@Column(length = 13)
	private String isbn13;

	/** For a request: how the user answered it, or null while they have not. */
	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(length = 12)
	private NotificationService.Answer answer;

	@Column(nullable = false)
	private Instant createdAt;

	protected Notification() {
	}

	Notification(String userId, NotificationService.Kind kind, String actorId, Long swapId, String isbn13, Instant createdAt) {
		this.userId = userId;
		this.kind = kind;
		this.actorId = actorId;
		this.swapId = swapId;
		this.isbn13 = isbn13;
		this.createdAt = createdAt;
	}

	Long id() {
		return id;
	}

	NotificationService.Kind kind() {
		return kind;
	}

	String actorId() {
		return actorId;
	}

	Long swapId() {
		return swapId;
	}

	String isbn13() {
		return isbn13;
	}

	NotificationService.Answer answer() {
		return answer;
	}

	void answered(NotificationService.Answer answer) {
		this.answer = answer;
	}

}
