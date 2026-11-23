package archivefever.server.book;

/**
 * A book as described by the catalogue (YES24).
 *
 * @param link the catalogue's product page, which every screen showing the book must link to
 * @param coverImageUrl the catalogue's cover image, or null
 * @param spineImageUrl the catalogue's spine image, or null
 * @param pages page count, or null when unknown
 */
public record Book(String isbn13, Long itemId, String title, String author, String publisher, String link,
		String coverImageUrl, String spineImageUrl, Integer pages) {
}
