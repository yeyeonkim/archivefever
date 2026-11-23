package archivefever.host;

/**
 * What the Swing UI asks of whatever it is running inside: the phone app around the web
 * view, or the desktop. The UI itself has no camera and, in a web view, no browser.
 */
public interface HostBridge {
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

    /** Opens a web page outside the app. */
    void openUrl(String url);
}
