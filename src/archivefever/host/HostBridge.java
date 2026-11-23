package archivefever.host;

/**
 * What the Swing UI asks of whatever it is running inside: the phone app around the web
 * view, or the desktop. The UI itself has no camera and, in a web view, no browser.
 */
public interface HostBridge {
    /**
     * Asks the host to take a photo of a book's barcode and upload it to the given scan.
     * Returns at once; the outcome is read from the scan on the server.
     */
    void startScan(String scanId);

    /** Opens a web page outside the app. */
    void openUrl(String url);
}
