package archivefever.app;

import archivefever.api.LibraryApi;
import archivefever.host.HostBridge;
import archivefever.model.BookSpine;
import archivefever.ui.components.LinkLabel;
import archivefever.ui.components.ScanConfirmationPopup;
import archivefever.ui.components.ScanResultView;
import archivefever.ui.screens.CameraScanPanel;
import archivefever.ui.screens.MainPanel;
import archivefever.ui.screens.SentenceScanPanel;
import archivefever.ui.theme.AppColors;
import archivefever.ui.theme.AppFonts;
import javax.swing.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

/**
 * Runs book registration against the server: the scan button creates a scan, the host
 * (phone or desktop) uploads a barcode photo to it, and this class waits for the outcome,
 * shows the book for confirmation and puts it on the shelf. It also has the host save the
 * sentences collected from a page photo, and loads them for the reading screen.
 * Used on the event thread only.
 */
public final class LibraryController {
    /**
     * What the reading screen shows for a shelf book.
     * @param text the passages collected from the book, in page order
     * @param notice shown in place of the text when there is none
     */
    public record Reading(String author,String text,String notice) {}

    private static final String YES24="https://www.yes24.com/";
    private static final String PICK_HINT="저장할 줄을 고른 뒤 눌러 주세요";
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
    private SentenceScanPanel sentences;
    private Consumer<BookSpine> onPassageSaved;
    /** Bumped when a camera screen is entered or left, so a late answer to a save is ignored. */
    private int passageGeneration;
    /** Authors of the shelf books by ISBN; filled while the shelf loads off the event thread. */
    private final Map<String,String> authors=new ConcurrentHashMap<>();

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

    /** @param onSaved run with the book once a passage collected from it has been saved */
    public void bindSentences(SentenceScanPanel panel,Consumer<BookSpine> onSaved) {
        sentences=panel;onPassageSaved=onSaved;
    }

    /**
     * Signs in through the host and loads that person's shelf.
     * @param done told the name they go by, once signed in
     */
    public void signIn(Consumer<String> done) {
        host.signIn(name->{reloadShelf();done.accept(name);});
    }

    /** Ends the session and takes the books off the screen: they are the last person's. */
    public void signOut() {
        dropScan();
        shelfGeneration++;passageGeneration++;
        shelf.setBooks(List.of());
        authors.clear();
        host.signOut();
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
        passageGeneration++;
        java.awt.Rectangle display=CameraScanPanel.DISPLAY;
        if(showing)host.showCamera(display.x,display.y,display.width,display.height,CameraScanPanel.DISPLAY_CORNER);
        else host.hideCamera();
    }

    /**
     * Called when the sentence-capture screen's scan button is pressed: the host takes the
     * page photo now, reads its text and lets the reader pick lines on the picture.
     */
    public void pageCaptured() {
        host.capturePage();
        sentences.setSaveAvailable(true,PICK_HINT);
    }

    /** Called when the sentence-capture screen's save button is pressed. */
    public void savePassage() {
        BookSpine book=sentences.selectedBook();
        if(book==null)return;
        int attempt=++passageGeneration;
        sentences.setSaveAvailable(false,"저장하는 중이에요");
        host.savePassage(book.id(),outcome->{
            if(attempt!=passageGeneration)return;
            switch(outcome) {
                case SAVED->onPassageSaved.accept(book);
                case NOTHING_PICKED->sentences.setSaveAvailable(true,"저장할 줄을 먼저 골라 주세요");
                case CANCELLED->sentences.setSaveAvailable(true,PICK_HINT);
                case FAILED->sentences.setSaveAvailable(true,"저장하지 못했어요. 다시 눌러 주세요");
                case UNAVAILABLE->sentences.setSaveAvailable(false,"이 기기에서는 문장을 저장할 수 없어요");
            }
        });
    }

    /** Loads what the reading screen shows for a shelf book; {@code ready} always gets an answer. */
    public void loadReading(BookSpine book,Consumer<Reading> ready) {
        String author=authors.getOrDefault(book.id(),"");
        background(()->readingText(api.passages(book.id())),
                text->ready.accept(new Reading(author,text,"아직 수집한 문장이 없어요.\n책장에서 책을 누르고 scan으로 문장을 모아 보세요.")),
                error->ready.accept(new Reading(author,"","수집한 문장을 불러오지 못했어요.")));
    }

    /**
     * The passages as one text, a paragraph each, with the page noted wherever it changes.
     * @param passages in reading order, those without a page last
     */
    public static String readingText(List<LibraryApi.Passage> passages) {
        StringBuilder text=new StringBuilder();
        Integer page=null;
        for(LibraryApi.Passage passage:passages) {
            if(text.length()>0)text.append("\n\n");
            if(passage.page()!=null && !passage.page().equals(page))text.append("p.").append(passage.page()).append('\n');
            else if(passage.page()==null && page!=null)text.append("p.—\n");
            page=passage.page();
            text.append(passage.text());
        }
        return text.toString();
    }

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
        for(LibraryApi.ShelfBook book:api.library()) {
            authors.put(book.isbn13(),book.author()==null?"":book.author());
            spines.add(new BookSpine(book.isbn13(),book.title(),api.image(book.spineUrl()),
                    Math.max(1,Math.min(355,book.spineWidth()))));
        }
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
