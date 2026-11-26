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
 * What someone has marked on one sentence of a collected passage: an underline, a
 * highlight, a memo, or any of them together. The passage is their own, or one a book
 * swap lets them read. A sentence nobody has marked has no row.
 *
 * The table is called after memos because memos were what it held first.
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

	/** Which sentence of the passage the marks are on, counting from 0. */
	@Column(nullable = false)
	private int sentence;

	@Column(nullable = false)
	private boolean underlined;

	@Column(nullable = false)
	private boolean highlighted;

	/** The memo; empty when the sentence is only underlined or highlighted. */
	@Column(name = "content", nullable = false, length = PassageService.MAX_NOTE_LENGTH)
	private String text;

	@Column(nullable = false)
	private Instant updatedAt;

	protected PassageNote() {
	}

	PassageNote(Long passageId, String authorId, int sentence) {
		this.passageId = passageId;
		this.authorId = authorId;
		this.sentence = sentence;
		this.text = "";
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

	boolean underlined() {
		return underlined;
	}

	boolean highlighted() {
		return highlighted;
	}

	String text() {
		return text;
	}

	Instant updatedAt() {
		return updatedAt;
	}

	void set(boolean underlined, boolean highlighted, String text, Instant now) {
		this.underlined = underlined;
		this.highlighted = highlighted;
		this.text = text;
		this.updatedAt = now;
	}

}
