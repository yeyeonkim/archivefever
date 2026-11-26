package archivefever.server.swap;

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

/**
 * Two friends reading the same book together: once the one who was asked accepts, each
 * sees what the other collected from it and wrote on it. No book changes hands.
 */
@Entity
@Table(name = "book_swap", indexes = { @Index(name = "book_swap_open", columnList = "open_key", unique = true),
		@Index(name = "book_swap_requester", columnList = "requester_id"),
		@Index(name = "book_swap_addressee", columnList = "addressee_id") })
class BookSwap {

	enum Status {

		/** Asked; the other user has not answered. */
		PENDING,
		/** Accepted: the two share their records of the book. */
		ACTIVE,
		DECLINED,
		/** Was still waiting for an answer when the two stopped being friends; nothing was ever shared. */
		WITHDRAWN,
		/**
		 * Was being read when the two stopped being friends. What they had shared until then
		 * stays readable to both; nothing newer is shared and nothing more can be written.
		 */
		ENDED

	}

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "requester_id", nullable = false, length = 64)
	private String requesterId;

	@Column(name = "addressee_id", nullable = false, length = 64)
	private String addresseeId;

	@Column(nullable = false, length = 13)
	private String isbn13;

	// Kept as text, not as the database's own enumeration, so that a status added later needs no change to the table.
	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 12)
	private Status status;

	/**
	 * The pair and the book, set while the swap is pending or active and null afterwards.
	 * Being unique, it lets two users have one open swap per book, whichever of them asks.
	 */
	@Column(name = "open_key", length = 160)
	private String openKey;

	@Column(nullable = false)
	private Instant requestedAt;

	private Instant answeredAt;

	/** When an accepted swap ended; its records are kept as they stood at this moment. */
	private Instant endedAt;

	protected BookSwap() {
	}

	BookSwap(String requesterId, String addresseeId, String isbn13, Instant now) {
		this.requesterId = requesterId;
		this.addresseeId = addresseeId;
		this.isbn13 = isbn13;
		this.status = Status.PENDING;
		this.openKey = openKey(requesterId, addresseeId, isbn13);
		this.requestedAt = now;
	}

	/** The same for a pair and a book whichever of the two is named first. */
	static String openKey(String userId, String otherId, String isbn13) {
		boolean ordered = userId.compareTo(otherId) <= 0;
		return (ordered ? userId : otherId) + "|" + (ordered ? otherId : userId) + "|" + isbn13;
	}

	Long id() {
		return id;
	}

	String requesterId() {
		return requesterId;
	}

	String addresseeId() {
		return addresseeId;
	}

	String isbn13() {
		return isbn13;
	}

	Status status() {
		return status;
	}

	Instant requestedAt() {
		return requestedAt;
	}

	Instant endedAt() {
		return endedAt;
	}

	/** The pair and the book, whatever has become of the swap. */
	String pairKey() {
		return openKey(requesterId, addresseeId, isbn13);
	}

	boolean involves(String userId) {
		return requesterId.equals(userId) || addresseeId.equals(userId);
	}

	/** The other of the two. */
	String otherThan(String userId) {
		return requesterId.equals(userId) ? addresseeId : requesterId;
	}

	boolean open() {
		return status == Status.PENDING || status == Status.ACTIVE;
	}

	void accept(Instant now) {
		this.status = Status.ACTIVE;
		this.answeredAt = now;
	}

	void decline(Instant now) {
		this.status = Status.DECLINED;
		this.openKey = null;
		this.answeredAt = now;
	}

	/** The two are no longer friends: a request still waiting is off, a swap being read is over. */
	void end(Instant now) {
		if (status == Status.ACTIVE) {
			this.status = Status.ENDED;
			this.endedAt = now;
		}
		else {
			this.status = Status.WITHDRAWN;
		}
		this.openKey = null;
	}

}
