package archivefever.host;

import archivefever.api.Json;

/**
 * Host bridge for the browser JVM. Each request is one JSON message handed to the page,
 * which forwards it to the phone app around the web view.
 */
public final class WebHost implements HostBridge {
    /** Implemented in JavaScript by the host page (host.js). */
    private static native void post(String json);

    /** Tells the page the UI is on screen, so it can take its loading notice down. */
    public static void announceReady() {post("{\"type\":\"ready\"}");}

    @Override public void startScan(String scanId) {
        post("{\"type\":\"scan\",\"scanId\":"+Json.quote(scanId)+"}");
    }

    @Override public void openUrl(String url) {
        post("{\"type\":\"openUrl\",\"url\":"+Json.quote(url)+"}");
    }
}
