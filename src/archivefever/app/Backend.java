package archivefever.app;

import archivefever.api.ApiClient;
import archivefever.api.LibraryApi;
import archivefever.host.DesktopHost;
import archivefever.host.HostBridge;
import archivefever.host.WebHost;
import archivefever.social.ServerSocial;
import archivefever.ui.navigation.AppShell;
import java.util.function.Function;

/**
 * Connects the UI to the server when one is configured: the shelf, collected passages,
 * friends, book swaps and notifications. Otherwise the UI stays on demo data.
 */
public final class Backend {
    /** System property with the server address, e.g. -Darchivefever.api=http://localhost:8080 */
    public static final String API_PROPERTY="archivefever.api";
    /** Set by the web page to the name of whoever it has signed in, before it starts this UI. */
    public static final String USER_PROPERTY="archivefever.user";
    /** Set at sign-in to the handle friends find the user by, without the "@". */
    public static final String HANDLE_PROPERTY="archivefever.handle";

    private Backend() {}

    /** For the desktop window: photos are picked from disk, and the login button signs in by name. */
    public static void attachDesktop(AppShell app) {attach(app,DesktopHost::new);}

    /**
     * For the browser JVM inside the phone app: photos come from the phone's camera, and
     * the page has already signed someone in, so the UI opens on their shelf.
     */
    public static void attachWeb(AppShell app) {
        LibraryController library=attach(app,api->new WebHost());
        String user=System.getProperty(USER_PROPERTY);
        if(library==null || user==null)return;
        library.reloadShelf();
        app.signedIn(user);
    }

    private static LibraryController attach(AppShell app,Function<LibraryApi,HostBridge> host) {
        String address=System.getProperty(API_PROPERTY,"").strip();
        if(address.isEmpty())return null;
        ApiClient client=new ApiClient(address);
        LibraryApi api=new LibraryApi(client);
        LibraryController library=new LibraryController(api,host.apply(api));
        app.connect(library);
        app.connectSocial(new ServerSocial(client,api));
        return library;
    }
}
