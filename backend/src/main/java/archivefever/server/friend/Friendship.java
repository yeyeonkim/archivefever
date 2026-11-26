package archivefever.server.friend;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

/**
 * Two users who are friends, or one's request to the other that is still waiting. A
 * declined request and an ended friendship leave no row: either may ask again.
 */
@Entity
@Table(name = "friendship", uniqueConstraints = @UniqueConstraint(name = "friendship_pair", columnNames = { "low_id",
		"high_id" }))
class Friendship {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	/** The two users in the order of their ids, so that a pair has one row whoever asked. */
	@Column(name = "low_id", nullable = false, length = 64)
	private String lowId;

	@Column(name = "high_id", nullable = false, length = 64)
	private String highId;

	@Column(name = "requester_id", nullable = false, length = 64)
	private String requesterId;

	/** False while the request waits for the other user's answer. */
	@Column(nullable = false)
	private boolean accepted;

	@Column(nullable = false)
	private Instant requestedAt;

	private Instant acceptedAt;

	protected Friendship() {
	}

	Friendship(String requesterId, String otherId, Instant now) {
		this.lowId = low(requesterId, otherId);
		this.highId = high(requesterId, otherId);
		this.requesterId = requesterId;
		this.requestedAt = now;
	}

	static String low(String a, String b) {
		return a.compareTo(b) <= 0 ? a : b;
	}

	static String high(String a, String b) {
		return a.compareTo(b) <= 0 ? b : a;
	}

	String requesterId() {
		return requesterId;
	}

	boolean accepted() {
		return accepted;
	}

	Instant acceptedAt() {
		return acceptedAt;
	}

	/** The other of the two. */
	String otherThan(String userId) {
		return lowId.equals(userId) ? highId : lowId;
	}

	void accept(Instant now) {
		this.accepted = true;
		this.acceptedAt = now;
	}

}
