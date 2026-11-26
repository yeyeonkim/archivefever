package archivefever.server.passage;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

/**
 * A memo someone wrote on one sentence of a collected passage: their own passage, or one
 * a book swap lets them read.
 */
@Entity
@Table(name = "passage_note", uniqueConstraints = @UniqueConstraint(name = "passage_note_place", columnNames = {
		"passage_id", "author_id", "sentence" }))
class PassageNote {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "passage_id", nullable = false)
	private Long passageId;

	@Column(name = "author_id", nullable = false, length = 64)
	private String authorId;

	/** Which sentence of the passage the memo is on, counting from 0. */
	@Column(nullable = false)
	private int sentence;

	@Column(name = "content", nullable = false, length = PassageService.MAX_NOTE_LENGTH)
	private String text;

	@Column(nullable = false)
	private Instant updatedAt;

	protected PassageNote() {
	}

	PassageNote(Long passageId, String authorId, int sentence, String text, Instant now) {
		this.passageId = passageId;
		this.authorId = authorId;
		this.sentence = sentence;
		this.text = text;
		this.updatedAt = now;
	}

	Long passageId() {
		return passageId;
	}

	String authorId() {
		return authorId;
	}

	int sentence() {
		return sentence;
	}

	String text() {
		return text;
	}

	Instant updatedAt() {
		return updatedAt;
	}

	void rewrite(String text, Instant now) {
		this.text = text;
		this.updatedAt = now;
	}

}
