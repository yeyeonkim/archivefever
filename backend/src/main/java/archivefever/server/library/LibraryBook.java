package archivefever.server.library;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

/** One book on one user's shelf. The book itself is identified by ISBN only. */
@Entity
@Table(name = "library_book", uniqueConstraints = @UniqueConstraint(columnNames = { "user_id", "isbn13" }))
class LibraryBook {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "user_id", nullable = false, length = 64)
	private String userId;

	@Column(name = "isbn13", nullable = false, length = 13)
	private String isbn13;

	@Column(nullable = false)
	private Instant addedAt;

	protected LibraryBook() {
	}

	LibraryBook(String userId, String isbn13, Instant addedAt) {
		this.userId = userId;
		this.isbn13 = isbn13;
		this.addedAt = addedAt;
	}

	String isbn13() {
		return isbn13;
	}

}
