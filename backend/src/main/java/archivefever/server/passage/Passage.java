package archivefever.server.passage;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;

/** A sentence, or a few, that a reader collected from a page of one of their books. */
@Entity
@Table(name = "passage", indexes = @Index(columnList = "user_id, isbn13"))
class Passage {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "user_id", nullable = false, length = 64)
	private String userId;

	@Column(name = "isbn13", nullable = false, length = 13)
	private String isbn13;

	/** Null when the reader did not say which page the text is on. */
	@Column(name = "page_number")
	private Integer page;

	@Column(name = "content", nullable = false, length = PassageService.MAX_LENGTH)
	private String text;

	@Column(nullable = false)
	private Instant createdAt;

	protected Passage() {
	}

	Passage(String userId, String isbn13, Integer page, String text, Instant createdAt) {
		this.userId = userId;
		this.isbn13 = isbn13;
		this.page = page;
		this.text = text;
		this.createdAt = createdAt;
	}

	Long id() {
		return id;
	}

	Integer page() {
		return page;
	}

	String text() {
		return text;
	}

	Instant createdAt() {
		return createdAt;
	}

}
