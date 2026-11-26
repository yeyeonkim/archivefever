package archivefever.server.library;

/**
 * A user is about to take a book off their shelf. Whoever needs the book to stay (a book
 * swap being read) says so by throwing, and the book stays.
 */
public record ShelfBookLeaving(String userId, String isbn13) {
}
