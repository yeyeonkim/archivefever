package archivefever.app;

import archivefever.api.ApiClient;
import archivefever.api.LibraryApi;
import archivefever.host.DesktopHost;
import archivefever.host.HostBridge;
import archivefever.host.WebHost;
import archivefever.ui.navigation.AppShell;
import java.util.function.Function;

/** Connects the UI to the server when one is configured; otherwise the UI stays on demo data. */
public final class Backend {
    /** System property with the server address, e.g. -Darchivefever.api=http://localhost:8080 */
    public static final String API_PROPERTY="archivefever.api";

    private Backend() {}

    /** For the desktop window: photos are picked from disk. */
    public static void attachDesktop(AppShell app) {attach(app,DesktopHost::new);}

    /** For the browser JVM inside the phone app: photos come from the phone's camera. */
    public static void attachWeb(AppShell app) {attach(app,api->new WebHost());}

    private static void attach(AppShell app,Function<LibraryApi,HostBridge> host) {
        String address=System.getProperty(API_PROPERTY,"").strip();
        if(address.isEmpty())return;
        LibraryApi api=new LibraryApi(new ApiClient(address));
        LibraryController library=new LibraryController(api,host.apply(api));
        app.connect(library);
        library.reloadShelf();
    }
}
