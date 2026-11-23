package archivefever.server.scan;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "scan")
class Scan {

	@Id
	@Column(length = 36)
	private String id;

	@Column(nullable = false, length = 64)
	private String userId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 16)
	private ScanStatus status;

	/** Detail for NO_BARCODE and FAILED outcomes; null otherwise. */
	@Column(length = 32)
	private String reason;

	@Column(length = 13)
	private String isbn13;

	@Column(nullable = false)
	private Instant createdAt;

	protected Scan() {
	}

	Scan(String id, String userId, Instant createdAt) {
		this.id = id;
		this.userId = userId;
		this.status = ScanStatus.PENDING;
		this.createdAt = createdAt;
	}

	void finish(ScanStatus status, String reason, String isbn13) {
		this.status = status;
		this.reason = reason;
		this.isbn13 = isbn13;
	}

	void setStatus(ScanStatus status) {
		this.status = status;
	}

	String id() {
		return id;
	}

	String userId() {
		return userId;
	}

	ScanStatus status() {
		return status;
	}

	String reason() {
		return reason;
	}

	String isbn13() {
		return isbn13;
	}

	Instant createdAt() {
		return createdAt;
	}

}
