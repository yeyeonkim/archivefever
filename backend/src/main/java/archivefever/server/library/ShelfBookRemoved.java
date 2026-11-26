package archivefever.server.library;

/** A user has taken a book off their shelf: what they kept about that book goes with it. */
public record ShelfBookRemoved(String userId, String isbn13) {
}
