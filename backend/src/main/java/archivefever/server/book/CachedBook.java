package archivefever.server.book;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Catalogue data kept for books users have scanned. YES24's terms allow caching but not
 * building a separate catalogue, so rows exist only for books someone looked up, and the
 * user's own record (the library) refers to them by ISBN.
 */
@Entity
@Table(name = "book_cache")
class CachedBook {

	@Id
	@Column(length = 13)
	private String isbn13;

	private Long itemId;

	@Column(length = 500)
	private String title;

	@Column(length = 500)
	private String author;

	@Column(length = 200)
	private String publisher;

	@Column(length = 500)
	private String link;

	@Column(length = 500)
	private String coverImageUrl;

	@Column(length = 500)
	private String spineImageUrl;

	private Integer pages;

	private Instant fetchedAt;

	/** Which {@link BookLookup} produced this row. Null on rows written before sources were recorded. */
	@Column(length = 16)
	private String source;

	protected CachedBook() {
	}

	CachedBook(Book book, String source, Instant fetchedAt) {
		this.source = source;
		this.isbn13 = book.isbn13();
		this.itemId = book.itemId();
		this.title = book.title();
		this.author = book.author();
		this.publisher = book.publisher();
		this.link = book.link();
		this.coverImageUrl = book.coverImageUrl();
		this.spineImageUrl = book.spineImageUrl();
		this.pages = book.pages();
		this.fetchedAt = fetchedAt;
	}

	String source() {
		return source;
	}

	Book toBook() {
		return new Book(isbn13, itemId, title, author, publisher, link, coverImageUrl, spineImageUrl, pages);
	}

}
