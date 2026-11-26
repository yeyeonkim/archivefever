package archivefever.app;

import archivefever.api.ApiClient;
import archivefever.api.LibraryApi;
import archivefever.host.HostBridge;
import archivefever.model.BookSpine;
import archivefever.model.PassagePage;
import archivefever.model.ReadingDocument;
import archivefever.ui.components.LinkLabel;
import archivefever.ui.components.ScanConfirmationPopup;
import archivefever.ui.components.ScanResultView;
import archivefever.ui.screens.BookSearchPanel;
import archivefever.ui.screens.CameraScanPanel;
import archivefever.ui.screens.MainPanel;
import archivefever.ui.screens.SentenceScanPanel;
import archivefever.ui.theme.AppColors;
import archivefever.ui.theme.AppFonts;
import javax.swing.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

/**
 * Runs book registration against the server: the scan button creates a scan, the host
 * (phone or desktop) uploads a barcode photo to it, and this class waits for the outcome,
 * shows the book for confirmation and puts it on the shelf. A book found by searching for
 * its title goes the same way from the confirmation on. It also takes books off the shelf,
 * has the host save the sentences collected from a page photo and correct them later, and
 * loads them for the reading screen with the reader's marks, which it keeps on the server.
 * Used on the event thread only.
 */
public final class LibraryController {
    /**
     * What the reading screen shows for a shelf book.
     * @param document the passages collected from the book, in page order, with the reader's
     *                 marks; every mark made on it is kept on the server
     * @param notice shown in place of the text when there is none
     * @param page which passage each sentence of the document is of
     */
    public record Reading(String author,ReadingDocument document,String notice,PassagePage page) {}

    private static final String YES24="https://www.yes24.com/";
    private static final String PICK_HINT="저장할 줄을 고른 뒤 눌러 주세요";
    private static final String NOTHING_COLLECTED="아직 수집한 문장이 없어요.\n책장에서 책을 누르고 scan으로 문장을 모아 보세요.";
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
    /** The book found by searching that the popup is offering, once it has been looked up; null otherwise. */
    private String offeredIsbn;

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
        background(()->reading(book.id(),author,api.passages(book.id())),ready,
                error->ready.accept(new Reading(author,new ReadingDocument(book.id(),"",Map.of(),null),
                        "수집한 문장을 불러오지 못했어요.",new PassagePage(List.of()))));
    }

    /**
     * The passages as one text, a paragraph each, with the page noted wherever it changes,
     * and the reader's marks on their sentences. A mark made on the document is saved to
     * the server before it counts, by passage and by sentence within the passage.
     * @param passages in reading order, those without a page last
     */
    private Reading reading(String isbn13,String author,List<LibraryApi.Passage> passages) {
        PassagePage page=page(passages);
        Map<Integer,ReadingDocument.Mark> marks=new HashMap<>();
        for(LibraryApi.Passage passage:passages)for(LibraryApi.PassageMark mark:passage.marks()) {
            // A mark on a sentence the passage no longer has (it was corrected since) has nowhere to show.
            Integer sentence=page.sentenceAt(passage.id(),mark.sentence());
            if(sentence!=null)marks.put(sentence,new ReadingDocument.Mark(mark.underlined(),mark.highlighted(),
                    !mark.note().isEmpty(),mark.note()));
        }
        if(marks.isEmpty() && !page.isEmpty())handOverLocalMarks(isbn13,page,marks);
        ReadingDocument document=new ReadingDocument(isbn13,page.text(),marks,(sentence,mark)->{
            PassagePage.Passage passage=page.passageOf(sentence);
            if(passage==null)throw new IOException("A page note is not a passage");
            api.markPassage(isbn13,passage.id(),page.placeIn(sentence),mark.underlined(),mark.highlighted(),mark.note());
        });
        return new Reading(author,document,NOTHING_COLLECTED,page);
    }

    /**
     * Marks used to be kept in a file on the device. A book that has none on the server yet
     * but has such a file gets the file's marks: they are saved to the server, sentence by
     * sentence, and the file has then done its work. If saving fails part of the way the
     * file stays, and the rest follows the next time the book is read here.
     * @param marks filled with what was handed over, by sentence of the page
     */
    private void handOverLocalMarks(String isbn13,PassagePage page,Map<Integer,ReadingDocument.Mark> marks) {
        if(!ReadingDocument.hasLocalMarks(isbn13))return;
        // Read as a document of today's text, the file's marks follow their sentences to where those are now.
        ReadingDocument local=new ReadingDocument(isbn13,page.text());
        try {
            for(int sentence=0;sentence<local.sentences().size();sentence++) {
                ReadingDocument.Mark mark=local.mark(sentence);
                PassagePage.Passage passage=page.passageOf(sentence);
                String note=mark.note().strip();
                // A mark on a page note has no passage to belong to, and is left behind.
                if(passage==null || !(mark.underlined() || mark.highlighted() || !note.isEmpty()))continue;
                api.markPassage(isbn13,passage.id(),page.placeIn(sentence),mark.underlined(),mark.highlighted(),note);
                marks.put(sentence,new ReadingDocument.Mark(mark.underlined(),mark.highlighted(),!note.isEmpty(),note));
            }
            ReadingDocument.forgetLocalMarks(isbn13);
        } catch(IOException | RuntimeException failed) {
            // Whatever got through is on the server and shows; the file is tried again next time.
        }
    }

    /** The passages for a reading page: the page is noted above a passage wherever it changes. */
    public static PassagePage page(List<LibraryApi.Passage> passages) {
        List<PassagePage.Passage> laidOut=new ArrayList<>();
        Integer page=null;
        for(LibraryApi.Passage passage:passages) {
            String heading=passage.page()!=null && !passage.page().equals(page)?"p."+passage.page()
                    :passage.page()==null && page!=null?"p.—":null;
            page=passage.page();
            laidOut.add(new PassagePage.Passage(passage.id(),passage.page(),heading,passage.text()));
        }
        return new PassagePage(laidOut);
    }

    /**
     * The reader pressed a sentence of a shelf book's reading page with no tool in hand: the
     * host lets them correct the passage it is of, or delete it.
     * @param changed run once the passage has been saved or deleted, for the page to be read again
     * @param failed told what to tell the reader when it was not
     */
    public void editPassage(BookSpine book,PassagePage page,int sentence,Runnable changed,Consumer<String> failed) {
        PassagePage.Passage passage=page.passageOf(sentence);
        if(passage==null)return;
        host.editPassage(book.id(),passage.id(),passage.page(),passage.text(),outcome->{
            switch(outcome) {
                case SAVED,DELETED->changed.run();
                case NOTHING_PICKED->failed.accept("빈 문장으로는 고칠 수 없어요.");
                case FAILED->failed.accept("문장을 고치지 못했어요. 다시 해 주세요.");
                case UNAVAILABLE->failed.accept("이 기기에서는 문장을 고칠 수 없어요.");
                case CANCELLED->{}
            }
        });
    }

    /**
     * Books the catalogue finds for some words of a title or an author, for the search
     * screen; a failure's message is one the screen can show as it is. Blocking: call it
     * off the event thread.
     */
    public List<BookSearchPanel.Found> findBooks(String words) throws IOException {
        try {
            List<BookSearchPanel.Found> found=new ArrayList<>();
            for(LibraryApi.FoundBook book:api.searchBooks(words))
                found.add(new BookSearchPanel.Found(book.isbn13(),book.title()==null?"":book.title(),
                        book.author()==null?"":book.author(),book.publisher()==null?"":book.publisher(),book.onShelf()));
            return found;
        } catch(ApiClient.Refused refused) {
            // 502: the server could not ask the catalogue, as opposed to something being wrong with the question.
            throw new IOException(refused.status()==502?"catalogue unavailable":"request failed",refused);
        } catch(IOException | RuntimeException failed) {
            throw new IOException("request failed",failed);
        }
    }

    /** Opens the catalogue the book data comes from; it asks to be named and linked wherever its data shows. */
    public void openCatalogue() {host.openUrl(YES24);}

    /**
     * Called when the confirmation popup has just opened for a book the reader picked from
     * a search: looks the book up, shows it with its cover, and lets it be confirmed.
     */
    public void offerFound(String isbn13) {
        dropScan();
        int offer=scanGeneration;
        result.showMessage("looking up...");
        background(()->api.book(isbn13),book->{
            if(offer!=scanGeneration)return;
            Runnable openSource=()->host.openUrl(book.link()==null?YES24:book.link());
            // The book can be confirmed even if its cover fails to load.
            background(()->api.image(book.coverUrl()),
                    cover->{if(offer==scanGeneration){offeredIsbn=book.isbn13();offer(book,cover,openSource);}},
                    error->{if(offer==scanGeneration){offeredIsbn=book.isbn13();offer(book,null,openSource);}});
        },error->{
            if(offer!=scanGeneration)return;
            result.showMessage(error instanceof ApiClient.Refused refused && refused.status()==404?"book not found":"lookup failed");
        });
    }

    /**
     * Takes a book off the reader's shelf, with what they collected from it.
     * @param failed told what to tell the reader when the book stays
     */
    public void removeBook(BookSpine book,Consumer<String> failed) {
        background(()->{api.removeBook(book.id());return loadShelf();},books->{
            shelfGeneration++;
            shelf.setBooks(books);
        },error->failed.accept(error instanceof ApiClient.Refused refused && refused.code().equals("SWAPPING")
                ?"교환독서 중인 책은 뺄 수 없어요":"책을 빼지 못했어요. 다시 해 주세요"));
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

    /** Called when the user accepts the book shown in the popup: a scanned one, or one found by searching. */
    public void confirm() {
        if(scanId==null && offeredIsbn==null)return;
        String id=scanId,isbn13=offeredIsbn;
        int scan=scanGeneration;
        popup.setConfirmationAvailable(false);
        background(()->{
            if(id!=null)api.confirmScan(id);else api.addBook(isbn13);
            return loadShelf();
        },books->{
            // The book is in the library now, so the shelf shows it even if the popup has gone.
            shelfGeneration++;
            shelf.setBooks(books);
            if(scan!=scanGeneration)return;
            scanId=null;offeredIsbn=null;
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
        scanGeneration++;poll.stop();polling=false;scanId=null;offeredIsbn=null;
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
