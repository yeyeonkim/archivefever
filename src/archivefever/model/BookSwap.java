package archivefever.model;

import java.awt.image.BufferedImage;

/** A book the user reads, has asked to read, or once read together with a friend. */
public record BookSwap(String id, String friendId, String friendHandle, String bookTitle, String author,
                       BufferedImage cover, Stage stage) {
    public enum Stage {
        /** Asked for; the friend has not accepted yet, and nothing is shared. */
        WAITING,
        /** Accepted: both read the shared record and write memos on it. */
        READING,
        /** The two are no longer friends: the record is kept as it stood then, to read only. */
        ENDED
    }

    public boolean waiting() {return stage == Stage.WAITING;}

    public boolean ended() {return stage == Stage.ENDED;}

    public BookSwap withStage(Stage next) {
        return new BookSwap(id, friendId, friendHandle, bookTitle, author, cover, next);
    }
}
