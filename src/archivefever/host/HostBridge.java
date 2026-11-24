package archivefever.host;

import java.util.function.Consumer;

/**
 * What the Swing UI asks of whatever it is running inside: the phone app around the web
 * view, or the desktop. The UI itself has no camera and, in a web view, no browser.
 */
public interface HostBridge {
    /** How a request to save a collected passage ended. */
    enum PassageResult {
        SAVED,
        /** The reader has not picked any text to save. */
        NOTHING_PICKED,
        /** The reader backed out when asked to confirm the text. */
        CANCELLED,
        FAILED,
        /** This host cannot collect passages. */
        UNAVAILABLE
    }

    /**
     * The camera screen is in view: a host with a camera shows its live preview over the
     * given rectangle (design coordinates of the 412x917 canvas). Hosts without one ignore it.
     */
    default void showCamera(int x,int y,int width,int height,int cornerRadius) {}

    /** The camera screen is no longer in view. */
    default void hideCamera() {}

    /**
     * Takes the photo now, at the moment the scan button is pressed, if a live preview is
     * showing. The photo is kept for the {@link #startScan} that follows.
     */
    default void capturePhoto() {}

    /**
     * Asks the host to upload a photo of a book's barcode to the given scan: the one just
     * taken by {@link #capturePhoto}, or else one it asks the person for. Returns at once;
     * the outcome is read from the scan on the server.
     */
    void startScan(String scanId);

    /**
     * Photographs a book page now, if a live preview is showing. The host reads the text
     * on it and lets the reader pick lines on the picture, for the {@link #savePassage}
     * that follows.
     */
    default void capturePage() {}

    /**
     * Saves the text the reader picked as a passage of the given shelf book, once the
     * reader has confirmed the text and its page number. Returns at once.
     * @param done told how it ended, on the event thread
     */
    default void savePassage(String isbn13,Consumer<PassageResult> done) {done.accept(PassageResult.UNAVAILABLE);}

    /**
     * Signs the person in, in whatever way this host can, and reports the name they go by.
     * Returns at once. A host with no accounts of its own lets everyone straight in.
     * @param done told the name on the event thread; not called if the person gives up
     */
    default void signIn(Consumer<String> done) {done.accept("");}

    /** Ends the session with the server. */
    default void signOut() {}

    /** Opens a web page outside the app. */
    void openUrl(String url);
}
