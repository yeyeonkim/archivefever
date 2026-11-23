package archivefever.host;

import archivefever.api.Json;
import java.util.Locale;

/**
 * Host bridge for the browser JVM. Each request is one JSON message handed to the page,
 * which forwards it to the phone app around the web view. Used on the event thread only.
 */
public final class WebHost implements HostBridge {
    /** Implemented in JavaScript by the host page (host.js). */
    private static native void post(String json);

    /** Where the 412x917 design currently sits in the page. */
    private static double scale=1;
    private static int originX,originY;
    /** The camera display in design coordinates (x, y, width, height, corner) while it is shown. */
    private static int[] camera;

    /** Tells the page the UI is on screen, so it can take its loading notice down. */
    public static void announceReady() {post("{\"type\":\"ready\"}");}

    /**
     * Records the scale and top-left corner at which the design is drawn in the page. The
     * phone app places its camera preview in page coordinates, so they follow the layout.
     */
    public static void designPlacedAt(double newScale,int x,int y) {
        if(newScale==scale && x==originX && y==originY)return;
        scale=newScale;originX=x;originY=y;
        if(camera!=null)postCamera();
    }

    @Override public void showCamera(int x,int y,int width,int height,int cornerRadius) {
        camera=new int[]{x,y,width,height,cornerRadius};
        postCamera();
    }

    @Override public void hideCamera() {
        camera=null;
        post("{\"type\":\"camera\",\"visible\":false}");
    }

    @Override public void capturePhoto() {post("{\"type\":\"capture\"}");}

    @Override public void startScan(String scanId) {
        post("{\"type\":\"scan\",\"scanId\":"+Json.quote(scanId)+"}");
    }

    @Override public void openUrl(String url) {
        post("{\"type\":\"openUrl\",\"url\":"+Json.quote(url)+"}");
    }

    private static void postCamera() {
        post(String.format(Locale.ROOT,
                "{\"type\":\"camera\",\"visible\":true,\"x\":%.2f,\"y\":%.2f,\"width\":%.2f,\"height\":%.2f,\"radius\":%.2f}",
                originX+camera[0]*scale,originY+camera[1]*scale,camera[2]*scale,camera[3]*scale,camera[4]*scale));
    }
}
