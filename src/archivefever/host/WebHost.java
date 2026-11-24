package archivefever.host;

import archivefever.api.Json;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import javax.swing.SwingUtilities;

/**
 * Host bridge for the browser JVM. Each request is one JSON message handed to the page,
 * which forwards it to the phone app around the web view. Used on the event thread only.
 */
public final class WebHost implements HostBridge {
    /** Implemented in JavaScript by the host page (host.js). */
    private static native void post(String json);

    /**
     * Waits until the page has the answer to a request that expects one, and returns it as
     * request number * 16 + outcome. Implemented in host.js; only the thread calling it waits.
     */
    private static native int awaitReply();

    /** Where the 412x917 design currently sits in the page. */
    private static double scale=1;
    private static int originX,originY;
    /** The camera display in design coordinates (x, y, width, height, corner) while it is shown. */
    private static int[] camera;
    /** Requests still waiting for the host's answer, by request number. */
    private static final Map<Integer,Consumer<PassageResult>> waiting=new HashMap<>();
    private static int lastRequest;
    private static Thread replies;

    /** Tells the page the UI is on screen, so it can take its loading notice down. */
    public static void announceReady() {post("{\"type\":\"ready\"}");}

    /**
     * Records the scale and top-left corner at which the design is drawn in the page. The
     * phone app places its camera preview in page coordinates, so they follow the layout.
     */
    public static void designPlacedAt(double newScale,int x,int y) {
        if(newScale==scale && x==originX && y==originY)return;
        scale=newScale;originX=x;originY=y;
        if(camera!=null)postCamera(true);
    }

    @Override public void showCamera(int x,int y,int width,int height,int cornerRadius) {
        camera=new int[]{x,y,width,height,cornerRadius};
        postCamera(false);
    }

    @Override public void hideCamera() {
        camera=null;
        post("{\"type\":\"camera\",\"visible\":false}");
    }

    @Override public void capturePhoto() {post("{\"type\":\"capture\"}");}

    @Override public void startScan(String scanId) {
        post("{\"type\":\"scan\",\"scanId\":"+Json.quote(scanId)+"}");
    }

    @Override public void capturePage() {post("{\"type\":\"capturePage\"}");}

    @Override public void savePassage(String isbn13,Consumer<PassageResult> done) {
        int request=++lastRequest;
        waiting.put(request,done);
        listenForReplies();
        post("{\"type\":\"savePassage\",\"request\":"+request+",\"isbn13\":"+Json.quote(isbn13)+"}");
    }

    /**
     * In the browser the page signs people in before it starts this UI, so the UI's own
     * login screen is only reached after signing out. Both ways lead back to the page's.
     */
    @Override public void signIn(Consumer<String> done) {signOut();}

    @Override public void signOut() {post("{\"type\":\"signOut\"}");}

    @Override public void openUrl(String url) {
        post("{\"type\":\"openUrl\",\"url\":"+Json.quote(url)+"}");
    }

    /** One thread of its own does the waiting, so an answer that never comes holds nothing up. */
    private static void listenForReplies() {
        if(replies!=null)return;
        replies=new Thread(()->{
            while(true) {
                int reply=awaitReply();
                SwingUtilities.invokeLater(()->answer(reply>>>4,reply&15));
            }
        },"host-replies");
        replies.setDaemon(true);
        replies.start();
    }

    private static void answer(int request,int outcome) {
        Consumer<PassageResult> done=waiting.remove(request);
        if(done==null)return;
        PassageResult[] results=PassageResult.values();
        done.accept(outcome<results.length?results[outcome]:PassageResult.FAILED);
    }

    /** @param moved the display is the one already showing, drawn at a new place: nothing restarts */
    private static void postCamera(boolean moved) {
        post(String.format(Locale.ROOT,
                "{\"type\":\"camera\",\"visible\":true,\"x\":%.2f,\"y\":%.2f,\"width\":%.2f,\"height\":%.2f,\"radius\":%.2f,\"moved\":%b}",
                originX+camera[0]*scale,originY+camera[1]*scale,camera[2]*scale,camera[3]*scale,camera[4]*scale,moved));
    }
}
