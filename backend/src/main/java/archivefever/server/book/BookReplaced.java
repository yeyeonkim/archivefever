package archivefever.server.book;

/**
 * Published when a cached book's data is replaced by a fresh lookup, so that anything
 * derived from the old data (stored pictures) can be dropped.
 */
public record BookReplaced(String isbn13) {
}
