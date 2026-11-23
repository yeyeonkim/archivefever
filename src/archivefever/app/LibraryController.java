package archivefever.app;

import archivefever.api.LibraryApi;
import archivefever.host.HostBridge;
import archivefever.model.BookSpine;
import archivefever.ui.components.LinkLabel;
import archivefever.ui.components.ScanConfirmationPopup;
import archivefever.ui.components.ScanResultView;
import archivefever.ui.screens.CameraScanPanel;
import archivefever.ui.screens.MainPanel;
import archivefever.ui.theme.AppColors;
import archivefever.ui.theme.AppFonts;
import javax.swing.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

/**
 * Runs book registration against the server: the scan button creates a scan, the host
 * (phone or desktop) uploads a barcode photo to it, and this class waits for the outcome,
 * shows the book for confirmation and puts it on the shelf. Used on the event thread only.
 */
public final class LibraryController {
    private static final String YES24="https://www.yes24.com/";
    private static final int POLL_MILLIS=800, FAILURES_BEFORE_GIVING_UP=5;
    private final LibraryApi api;
    private final HostBridge host;
    private final Timer poll=new Timer(POLL_MILLIS,e->pollOnce());
    private MainPanel shelf;
    private ScanConfirmationPopup popup;
    private ScanResultView result;
    private Runnable onAdded;
    private String scanId;
    private boolean polling;
    private int failures;
    /** Bumped whenever the current scan is replaced or dropped, so its late answers are ignored. */
    private int scanGeneration;
    /** Bumped for every shelf update, so only the newest one is shown. */
    private int shelfGeneration;

    public LibraryController(LibraryApi api,HostBridge host) {this.api=api;this.host=host;}

    /** @param onAdded run after a confirmed book has been placed on the shelf */
    public void bind(MainPanel shelf,ScanConfirmationPopup popup,Runnable onAdded) {
        this.shelf=shelf;this.popup=popup;this.onAdded=onAdded;
        result=new ScanResultView(popup.resultArea());
        // YES24 requires its name and a link on every screen that shows its book data.
        LinkLabel credit=new LinkLabel("book data: YES24","도서 정보 출처 YES24",AppFonts.typewriter(12),
                AppColors.LIGHT_BROWN,true,()->host.openUrl(YES24));
        credit.setBounds(262,4,140,18);
        shelf.add(credit,0);
    }

    /** Replaces the shelf with the user's books from the server. */
    public void reloadShelf() {
        int load=++shelfGeneration;
        background(this::loadShelf,books->{if(load==shelfGeneration)shelf.setBooks(books);},error->{});
    }

    /**
     * Called when a camera screen (add book, or sentence capture) comes into view or back
     * into view, or when the UI leaves it. Both screens draw the same camera.
     */
    public void cameraScreen(boolean showing) {
        java.awt.Rectangle display=CameraScanPanel.DISPLAY;
        if(showing)host.showCamera(display.x,display.y,display.width,display.height,CameraScanPanel.DISPLAY_CORNER);
        else host.hideCamera();
    }

    /**
     * Called when the sentence-capture screen's scan button is pressed: the host takes the
     * page photo now and keeps the picture on its preview. Nothing is uploaded yet; text
     * recognition and saving sentences are not built.
     */
    public void pageCaptured() {host.capturePhoto();}

    /** Called when the confirmation popup has just opened for a new scan. */
    public void scanStarted() {
        // Take the picture at the moment of the press; it is uploaded once the scan exists.
        host.capturePhoto();
        dropScan();
        int scan=scanGeneration;
        result.showMessage("opening camera...");
        background(api::createScan,created->{
            if(scan!=scanGeneration) {
                // The popup was closed before the server answered; do not leave the scan waiting.
                background(()->api.cancelScan(created.id()),cancelled->{},error->{});
                return;
            }
            scanId=created.id();failures=0;
            host.startScan(created.id());
            result.showMessage("waiting for photo...");
            poll.start();
        },error->{if(scan==scanGeneration)result.showMessage("request failed");});
    }

    /** Called when the user accepts the book shown in the popup. */
    public void confirm() {
        if(scanId==null)return;
        String id=scanId;
        int scan=scanGeneration;
        popup.setConfirmationAvailable(false);
        background(()->{api.confirmScan(id);return loadShelf();},books->{
            // The book is in the library now, so the shelf shows it even if the popup has gone.
            shelfGeneration++;
            shelf.setBooks(books);
            if(scan!=scanGeneration)return;
            scanId=null;
            onAdded.run();
        },error->{
            if(scan!=scanGeneration)return;
            result.showMessage("request failed");
            popup.setConfirmationAvailable(true);
        });
    }

    /** Called whenever the popup closes; abandons a scan that is still waiting. */
    public void cancel() {
        String id=scanId;
        dropScan();
        if(id!=null)background(()->api.cancelScan(id),cancelled->{},error->{});
    }

    private void dropScan() {
        scanGeneration++;poll.stop();polling=false;scanId=null;
    }

    private void pollOnce() {
        if(polling || scanId==null)return;
        polling=true;
        String id=scanId;
        int scan=scanGeneration;
        background(()->api.scan(id),latest->{
            if(scan!=scanGeneration)return;
            polling=false;failures=0;
            show(latest);
        },error->{
            if(scan!=scanGeneration)return;
            polling=false;
            if(++failures>=FAILURES_BEFORE_GIVING_UP) {poll.stop();result.showMessage("request failed");}
        });
    }

    private void show(LibraryApi.Scan latest) {
        if(latest.status().equals("PENDING"))return;
        poll.stop();
        if(latest.status().equals("RESOLVED") && latest.book()!=null) {
            LibraryApi.BookInfo book=latest.book();
            int scan=scanGeneration;
            Runnable openSource=()->host.openUrl(book.link()==null?YES24:book.link());
            // The book can be confirmed even if its cover fails to load.
            background(()->api.image(book.coverUrl()),
                    cover->{if(scan==scanGeneration)offer(book,cover,openSource);},
                    error->{if(scan==scanGeneration)offer(book,null,openSource);});
            return;
        }
        scanId=null;
        result.showMessage(switch(latest.status()) {
            case "NO_BARCODE"->"no barcode found";
            case "NOT_FOUND"->"book not found";
            case "CANCELLED"->"scan cancelled";
            case "EXPIRED"->"scan expired";
            default->"lookup failed";
        });
    }

    private void offer(LibraryApi.BookInfo book,BufferedImage cover,Runnable openSource) {
        result.showBook(book.title(),book.author(),cover,openSource);
        popup.setConfirmationAvailable(true);
    }

    private List<BookSpine> loadShelf() throws Exception {
        List<BookSpine> spines=new ArrayList<>();
        for(LibraryApi.ShelfBook book:api.library())
            spines.add(new BookSpine(book.isbn13(),book.title(),api.image(book.spineUrl()),
                    Math.max(1,Math.min(355,book.spineWidth()))));
        return spines;
    }

    /** Runs work off the event thread and reports back on it. */
    private static <T> void background(Callable<T> work,Consumer<T> done,Consumer<Exception> failed) {
        new SwingWorker<T,Void>() {
            @Override protected T doInBackground() throws Exception {return work.call();}
            @Override protected void done() {
                try {done.accept(get());}
                catch(ExecutionException e) {failed.accept(e.getCause() instanceof Exception cause?cause:e);}
                catch(InterruptedException e) {Thread.currentThread().interrupt();failed.accept(e);}
            }
        }.execute();
    }
}
