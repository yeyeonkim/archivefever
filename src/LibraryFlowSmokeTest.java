import archivefever.api.ApiClient;
import archivefever.api.Json;
import archivefever.api.LibraryApi;
import archivefever.app.LibraryController;
import archivefever.host.HostBridge;
import archivefever.model.ReadingDocument;
import archivefever.ui.components.Bookshelf;
import archivefever.ui.components.ScanConfirmationPopup;
import archivefever.ui.navigation.AppShell;
import archivefever.ui.navigation.Destination;
import archivefever.ui.screens.ReadingPanel;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Exercises server-backed book registration through the real Swing components, against a
 * small in-process HTTP server that answers like the Archive Fever backend.
 */
public class LibraryFlowSmokeTest {
    private static final String ISBN="9788936434120", TITLE="소년이 온다 \"테스트\"", LINK="https://www.yes24.com/Product/Goods/1";
    private static final String BOOK="{\"isbn13\":\""+ISBN+"\",\"title\":"+Json.quote(TITLE)+",\"author\":\"한강\","
            +"\"publisher\":\"창비\",\"link\":\""+LINK+"\",\"coverUrl\":\"/api/images/"+ISBN+"/cover.png\","
            +"\"spineUrl\":\"/api/images/"+ISBN+"/spine.png\"}";
    private static final String SHELF_BOOK="{\"isbn13\":\""+ISBN+"\",\"title\":"+Json.quote(TITLE)+",\"author\":\"한강\","
            +"\"link\":\""+LINK+"\",\"spineUrl\":\"/api/images/"+ISBN+"/spine.png\",\"spineWidth\":20}";
    /** A second book, which only a search by title finds. */
    private static final String FOUND_ISBN="9788936433598", FOUND_TITLE="채식주의자", FOUND_LINK="https://www.yes24.com/Product/Goods/2";
    private static final String FOUND_BOOK="{\"isbn13\":\""+FOUND_ISBN+"\",\"title\":\""+FOUND_TITLE+"\",\"author\":\"한강\","
            +"\"publisher\":\"창비\",\"link\":\""+FOUND_LINK+"\",\"coverUrl\":\"/api/images/"+FOUND_ISBN+"/cover.png\","
            +"\"spineUrl\":\"/api/images/"+FOUND_ISBN+"/spine.png\"}";
    private static final String FOUND_SHELF_BOOK="{\"isbn13\":\""+FOUND_ISBN+"\",\"title\":\""+FOUND_TITLE+"\",\"author\":\"한강\","
            +"\"link\":\""+FOUND_LINK+"\",\"spineUrl\":\"/api/images/"+FOUND_ISBN+"/spine.png\",\"spineWidth\":24}";

    /** Scan id to status, as the fake server currently reports it. */
    private static final Map<String,String> scans=new ConcurrentHashMap<>();
    private static final List<String> cancelled=new CopyOnWriteArrayList<>();
    private static final AtomicInteger created=new AtomicInteger();
    private static volatile boolean inLibrary;
    /** Whether the book found by searching is on the shelf, and whether the server says it is being read with a friend. */
    private static volatile boolean foundInLibrary,foundBeingSwapped;
    /** The words the fake server was asked to search for, in order. */
    private static final List<String> searches=new CopyOnWriteArrayList<>();

    private static final List<String> scanRequests=new CopyOnWriteArrayList<>();
    private static final List<String> openedUrls=new CopyOnWriteArrayList<>();
    /** Camera and scan requests made of the host, in order. */
    private static final List<String> hostEvents=new CopyOnWriteArrayList<>();
    /** A collected passage as the fake server keeps it. */
    private record Stored(long id,Integer page,String text) {}
    /** Collected passages of the book, in the order the fake server lists them. */
    private static final List<Stored> passages=new CopyOnWriteArrayList<>();
    /** The reader's marks, as the JSON the fake server answers with, by "passage id/sentence". */
    private static final Map<String,String> marks=new ConcurrentHashMap<>();
    /** While set, the fake server fails every request to mark a sentence. */
    private static volatile boolean marksFail;
    /** What the fake host does when asked to let the reader correct a passage; null leaves it unanswered. */
    private static volatile java.util.function.Function<Stored,HostBridge.PassageResult> nextEdit;
    /** How the fake host ends the next save; null leaves the request unanswered. */
    private static volatile HostBridge.PassageResult nextSave;
    private static volatile Consumer<HostBridge.PassageResult> unansweredSave;
    private static AppShell app;
    private static JFrame frame;

    private static byte[] png(int width,int height) throws Exception {
        BufferedImage image=new BufferedImage(width,height,BufferedImage.TYPE_INT_RGB);
        Graphics2D g=image.createGraphics();g.setColor(new Color(0x6B3A2E));g.fillRect(0,0,width,height);g.dispose();
        ByteArrayOutputStream out=new ByteArrayOutputStream();ImageIO.write(image,"png",out);return out.toByteArray();
    }

    private static String scanJson(String id) {
        String status=scans.get(id);
        boolean hasBook=status.equals("RESOLVED") || status.equals("CONFIRMED");
        return "{\"id\":\""+id+"\",\"status\":\""+status+"\",\"reason\":null,\"book\":"+(hasBook?BOOK:"null")+"}";
    }

    private static void answer(HttpExchange exchange) {
        try(exchange) {
            String method=exchange.getRequestMethod(), path=exchange.getRequestURI().getPath();
            byte[] request=exchange.getRequestBody().readAllBytes();
            byte[] body;String type="application/json; charset=utf-8";int status=200;
            if(method.equals("POST") && path.equals("/api/scans")) {
                String id="scan-"+created.incrementAndGet();scans.put(id,"PENDING");
                body=scanJson(id).getBytes(StandardCharsets.UTF_8);status=201;
            } else if(path.startsWith("/api/scans/")) {
                String[] parts=path.substring("/api/scans/".length()).split("/");
                String id=parts[0];
                if(parts.length==2 && parts[1].equals("cancel")) {
                    cancelled.add(id);scans.computeIfPresent(id,(key,value)->value.equals("PENDING")?"CANCELLED":value);
                    body=scanJson(id).getBytes(StandardCharsets.UTF_8);
                } else if(parts.length==2 && parts[1].equals("confirm")) {
                    scans.put(id,"CONFIRMED");inLibrary=true;
                    body=SHELF_BOOK.getBytes(StandardCharsets.UTF_8);
                } else body=scanJson(id).getBytes(StandardCharsets.UTF_8);
            } else if(path.equals("/api/library/books/"+ISBN+"/passages")) {
                if(method.equals("POST")) {
                    Object sent=Json.parse(new String(request,StandardCharsets.UTF_8));
                    Stored saved=new Stored(passages.size()+1,Json.field(sent,"page") instanceof Number page?page.intValue():null,
                            Json.string(sent,"text"));
                    passages.add(saved);body=json(saved).getBytes(StandardCharsets.UTF_8);status=201;
                } else body=("["+String.join(",",passages.stream().map(LibraryFlowSmokeTest::json).toList())+"]")
                        .getBytes(StandardCharsets.UTF_8);
            } else if(path.startsWith("/api/library/books/"+ISBN+"/passages/")) {
                String[] parts=path.substring(("/api/library/books/"+ISBN+"/passages/").length()).split("/");
                long id=Long.parseLong(parts[0]);
                Stored stored=passages.stream().filter(passage->passage.id()==id).findFirst().orElse(null);
                body=new byte[0];status=204;
                if(stored==null) {body="{\"error\":\"PASSAGE_NOT_FOUND\"}".getBytes(StandardCharsets.UTF_8);status=404;}
                else if(parts.length==2 && parts[1].equals("marks")) {
                    Object sent=Json.parse(new String(request,StandardCharsets.UTF_8));
                    String key=id+"/"+Json.integer(sent,"sentence");
                    boolean underlined=Boolean.TRUE.equals(Json.field(sent,"underlined")),highlighted=Boolean.TRUE.equals(Json.field(sent,"highlighted"));
                    String note=Json.string(sent,"note")==null?"":Json.string(sent,"note");
                    if(marksFail) {body="{}".getBytes(StandardCharsets.UTF_8);status=500;}
                    else if(!underlined && !highlighted && note.isEmpty())marks.remove(key);
                    else marks.put(key,"{\"sentence\":"+Json.integer(sent,"sentence")+",\"underlined\":"+underlined
                            +",\"highlighted\":"+highlighted+",\"note\":"+Json.quote(note)+"}");
                } else if(method.equals("PUT")) {
                    Object sent=Json.parse(new String(request,StandardCharsets.UTF_8));
                    Stored corrected=new Stored(id,Json.field(sent,"page") instanceof Number page?page.intValue():null,Json.string(sent,"text"));
                    passages.set(passages.indexOf(stored),corrected);
                    body=json(corrected).getBytes(StandardCharsets.UTF_8);status=200;
                } else if(method.equals("DELETE")) {
                    passages.remove(stored);marks.keySet().removeIf(key->key.startsWith(id+"/"));
                }
            } else if(path.equals("/api/library/books")) {
                if(method.equals("POST")) {
                    foundInLibrary=FOUND_ISBN.equals(Json.string(Json.parse(new String(request,StandardCharsets.UTF_8)),"isbn13"));
                    body=FOUND_SHELF_BOOK.getBytes(StandardCharsets.UTF_8);status=201;
                } else body=("["+String.join(",",java.util.stream.Stream.of(inLibrary?SHELF_BOOK:null,foundInLibrary?FOUND_SHELF_BOOK:null)
                        .filter(java.util.Objects::nonNull).toList())+"]").getBytes(StandardCharsets.UTF_8);
            } else if(method.equals("DELETE") && path.equals("/api/library/books/"+FOUND_ISBN)) {
                if(foundBeingSwapped) {body="{\"error\":\"SWAPPING\"}".getBytes(StandardCharsets.UTF_8);status=409;}
                else {foundInLibrary=false;body=new byte[0];status=204;}
            } else if(path.equals("/api/books")) {
                String words=java.net.URLDecoder.decode(exchange.getRequestURI().getRawQuery().substring("query=".length()),StandardCharsets.UTF_8);
                searches.add(words);
                // Whatever the words, the catalogue has two books: the one on the shelf already, and another.
                body=(words.contains("없는")?"[]":"["+BOOK.replace("}",",\"onShelf\":"+inLibrary+"}")+","
                        +FOUND_BOOK.replace("}",",\"onShelf\":"+foundInLibrary+"}")+"]").getBytes(StandardCharsets.UTF_8);
            } else if(path.equals("/api/books/"+FOUND_ISBN)) {
                body=FOUND_BOOK.getBytes(StandardCharsets.UTF_8);
            } else if(path.endsWith("/cover.png")) {body=png(200,300);type="image/png";}
            else if(path.endsWith("/spine.png")) {body=png(40,344);type="image/png";}
            else {body="{}".getBytes(StandardCharsets.UTF_8);status=404;}
            exchange.getResponseHeaders().add("Content-Type",type);
            // An answer without content is announced as such: -1, not a body of no bytes.
            exchange.sendResponseHeaders(status,body.length==0?-1:body.length);
            if(body.length>0)exchange.getResponseBody().write(body);
        } catch(Exception e) {throw new RuntimeException(e);}
    }

    /** A passage as the fake server answers with it, the reader's marks on it included. */
    private static String json(Stored passage) {
        List<String> own=marks.entrySet().stream().filter(mark->mark.getKey().startsWith(passage.id()+"/"))
                .sorted(Map.Entry.comparingByKey()).map(Map.Entry::getValue).toList();
        return "{\"id\":"+passage.id()+",\"page\":"+passage.page()+",\"text\":"+Json.quote(passage.text())
                +",\"marks\":["+String.join(",",own)+"]}";
    }

    private static Component named(Container parent,String name) {
        for(Component child:parent.getComponents()) {
            if(!child.isVisible())continue;
            if(child.getAccessibleContext()!=null && name.equals(child.getAccessibleContext().getAccessibleName()))return child;
            if(child instanceof Container container) {Component found=named(container,name);if(found!=null)return found;}
        }
        return null;
    }
    private static <T> T find(Container parent,Class<T> type) {
        for(Component child:parent.getComponents()) {
            if(type.isInstance(child))return type.cast(child);
            if(child instanceof Container container) {T found=find(container,type);if(found!=null)return found;}
        }
        return null;
    }
    private static <T> T edt(Callable<T> call) throws Exception {
        AtomicReference<T> value=new AtomicReference<>();
        AtomicReference<Exception> error=new AtomicReference<>();
        SwingUtilities.invokeAndWait(()->{try{value.set(call.call());}catch(Exception e){error.set(e);}});
        if(error.get()!=null)throw error.get();
        return value.get();
    }
    private static void waitFor(String what,Callable<Boolean> condition) throws Exception {
        long end=System.currentTimeMillis()+8000;
        while(System.currentTimeMillis()<end) {
            if(edt(condition))return;
            Thread.sleep(25);
        }
        throw new AssertionError("Timed out: "+what);
    }
    private static void click(String name) throws Exception {
        waitFor(name,()->named(app,name)!=null);
        edt(()->{((JButton)named(app,name)).doClick(0);return true;});
    }
    private static void require(boolean value,String message) {if(!value)throw new AssertionError(message);}
    private static void layoutAll(Container root) {
        root.doLayout();
        for(Component child:root.getComponents())if(child instanceof Container container)layoutAll(container);
    }
    private static void mouse(Component target,int id,int x,int y) {
        target.dispatchEvent(new java.awt.event.MouseEvent(target,id,System.currentTimeMillis(),0,x,y,1,false,
                java.awt.event.MouseEvent.BUTTON1));
    }
    private static void clickAt(Component target,int x,int y) {
        mouse(target,java.awt.event.MouseEvent.MOUSE_PRESSED,x,y);
        mouse(target,java.awt.event.MouseEvent.MOUSE_RELEASED,x,y);
        mouse(target,java.awt.event.MouseEvent.MOUSE_CLICKED,x,y);
    }
    /** A press, a move and a release; like a real one, it also ends in a click event on some platforms. */
    private static void drag(Component target,int x,int y,int toX,int toY) {
        mouse(target,java.awt.event.MouseEvent.MOUSE_PRESSED,x,y);
        mouse(target,java.awt.event.MouseEvent.MOUSE_DRAGGED,toX,toY);
        mouse(target,java.awt.event.MouseEvent.MOUSE_RELEASED,toX,toY);
        mouse(target,java.awt.event.MouseEvent.MOUSE_CLICKED,toX,toY);
    }
    private static int countUnderlined(archivefever.ui.components.ReadingText text) {
        int count=0;
        for(int i=0;i<text.document().sentences().size();i++)if(text.document().mark(i).underlined())count++;
        return count;
    }
    private static String lastHostEvent() {return hostEvents.isEmpty()?"":hostEvents.get(hostEvents.size()-1);}
    /** The name shown in the profile popup, which need not be open. */
    private static String profileName() {
        for(Component child:find(app,archivefever.ui.components.ProfilePopup.class).getComponents())
            if(child instanceof JLabel label && "로그인한 사용자".equals(label.getAccessibleContext().getAccessibleName()))
                return label.getText();
        return null;
    }
    private static String statusText() {
        return named(app,"도서 조회 상태") instanceof JLabel label?label.getText():null;
    }
    private static boolean canConfirm() {
        return named(app,"스캔한 도서 추가") instanceof JButton yes && yes.isEnabled();
    }
    private static JButton saveButton() {return named(app,"문장 저장") instanceof JButton button?button:null;}
    /** Presses save on the sentence screen and waits until the host's answer has been handled. */
    private static void save(HostBridge.PassageResult outcome,String expectedHint) throws Exception {
        nextSave=outcome;
        int saves=(int)hostEvents.stream().filter(event->event.equals("save "+ISBN)).count();
        click("문장 저장");
        waitFor("host asked to save",()->hostEvents.stream().filter(event->event.equals("save "+ISBN)).count()==saves+1);
        waitFor("hint: "+expectedHint,()->saveButton()!=null && saveButton().isEnabled()
                && expectedHint.equals(saveButton().getToolTipText()));
        require(edt(()->frame.getContentPane()==app),"Still on the sentence screen after "+outcome);
    }
    private static void openSentenceCapture() throws Exception {
        edt(()->{
            Component spine=find(app,Bookshelf.class).getComponent(0);
            spine.dispatchEvent(new java.awt.event.MouseEvent(spine,java.awt.event.MouseEvent.MOUSE_CLICKED,
                    System.currentTimeMillis(),0,3,3,1,false));
            return true;
        });
        click("책 스캔 선택");
        require(lastHostEvent().equals("show 52,141 272x369"),"Sentence capture asks for the live preview: "+lastHostEvent());
    }
    private static ReadingPanel reading() {return frame.getContentPane() instanceof ReadingPanel panel?panel:null;}
    /** Opens the reading page of a shelf book, by its place on the shelf. */
    private static void openReading(int place) throws Exception {
        pressSpine(place);
        click("책 읽기 선택");
        waitFor("reading page",()->reading()!=null);
    }
    private static void pressSpine(int place) throws Exception {
        edt(()->{
            Component spine=find(app,Bookshelf.class).getComponent(place);
            spine.dispatchEvent(new java.awt.event.MouseEvent(spine,java.awt.event.MouseEvent.MOUSE_CLICKED,
                    System.currentTimeMillis(),0,3,3,1,false));
            return true;
        });
    }
    /** Presses the text of the reading page on one of its lines, counting from 0, wherever the window's size has put it. */
    private static void pressLine(int line) throws Exception {
        edt(()->{
            frame.validate();
            archivefever.ui.components.ReadingText text=reading().readingText();
            double scale=text.getWidth()/335.0;
            clickAt(text,(int)Math.round(40*scale),(int)Math.round((line*14+7)*scale));
            return true;
        });
    }
    private static String pageText() {return reading()==null?null:reading().readingText().document().text();}
    private static boolean shelfNotice(String message) {
        return named(app,"책장 안내") instanceof JLabel notice && message.equals(notice.getText());
    }
    private static void leaveReading() throws Exception {
        edt(()->{((JButton)named(reading(),"return to archive")).doClick(0);return true;});
        require(edt(()->frame.getContentPane()==app && app.currentDestination()==Destination.ARCHIVE),"Back on the shelf");
    }

    private static void startScan(String expectedId) throws Exception {
        edt(()->{app.navigate(Destination.ADD_BOOK);return true;});
        require(lastHostEvent().equals("show 52,141 272x369"),"Camera screen asks for the live preview: "+lastHostEvent());
        click("도서 스캔");
        waitFor("host asked to scan "+expectedId,()->scanRequests.contains(expectedId));
        int scan=hostEvents.lastIndexOf("scan "+expectedId);
        require(hostEvents.get(scan-1).equals("capture"),"The photo is taken at the press, before the scan exists: "+hostEvents);
        waitFor("waiting message",()->"waiting for photo...".equals(statusText()));
        require(edt(()->!canConfirm()),"Nothing to confirm before the photo arrives");
    }

    private static void run() throws Exception {
        Object parsed=Json.parse("{\"a\":[1,2.5,true,null,\"한\\n\\u0041\\\"\"],\"b\":{}}");
        require(Json.list(Json.field(parsed,"a")).equals(java.util.Arrays.asList(1.0,2.5,true,null,"한\nA\"")),"JSON values");
        require(Json.parse(Json.quote("a\"b\\c\n")).equals("a\"b\\c\n"),"JSON quoting round trip");
        archivefever.model.PassagePage laidOut=LibraryController.page(List.of(new LibraryApi.Passage(11,7,"첫째 문장이다. 둘째 문장이다.",List.of()),
                new LibraryApi.Passage(12,7,"나.",List.of()),new LibraryApi.Passage(13,9,"다.",List.of()),
                new LibraryApi.Passage(14,null,"라.",List.of())));
        require(laidOut.text().equals("p.7\n첫째 문장이다. 둘째 문장이다.\n\n나.\n\np.9\n다.\n\np.—\n라."),
                "Passages become one text with the page noted where it changes");
        // Sentences: p.7 | 첫째 문장이다. | 둘째 문장이다. | 나. | p.9 | 다. | p | . | — | 라.
        // (The note for a passage without a page is read as three: the letter, the stop and the dash.)
        require(ReadingDocument.sentencesOf(laidOut.text()).size()==10,"Sentences: "+ReadingDocument.sentencesOf(laidOut.text()));
        require(laidOut.passageOf(0)==null && laidOut.passageOf(4)==null && laidOut.passageOf(6)==null && laidOut.passageOf(7)==null
                && laidOut.passageOf(8)==null,"A page note is no passage");
        require(laidOut.passageOf(1).id()==11 && laidOut.placeIn(1)==0 && laidOut.passageOf(2).id()==11 && laidOut.placeIn(2)==1
                && laidOut.passageOf(3).id()==12 && laidOut.placeIn(3)==0 && laidOut.passageOf(9).id()==14 && laidOut.placeIn(9)==0,
                "Each sentence knows its passage and its place in it");
        require(laidOut.sentenceAt(11,1)==2 && laidOut.sentenceAt(13,0)==5 && laidOut.sentenceAt(11,2)==null
                && laidOut.sentenceAt(99,0)==null,"And each passage's sentences are found on the page");
        require(LibraryController.page(List.of(new LibraryApi.Passage(1,null,"가.",List.of()),new LibraryApi.Passage(2,null,"나.",List.of())))
                .text().equals("가.\n\n나."),"Passages without pages carry no page notes");

        // Marks follow their sentences when the book's text grows around them.
        String notes="carry-"+System.nanoTime();
        ReadingDocument before=new ReadingDocument(notes,"p.9\n첫 문장. 둘째 문장.");
        before.underline(2);before.saveMemo(2,"기억할 것");before.highlight(0);
        ReadingDocument grown=new ReadingDocument(notes,"p.3\n새 문장.\n\np.9\n첫 문장. 둘째 문장.");
        int second=-1,fresh=-1,pageNine=-1;
        for(int i=0;i<grown.sentences().size();i++) {
            String sentence=grown.text().substring(grown.sentences().get(i).start(),grown.sentences().get(i).end()).strip();
            if(sentence.equals("둘째 문장."))second=i;
            if(sentence.equals("새 문장."))fresh=i;
            if(sentence.equals("p.9"))pageNine=i;
        }
        require(second>=0 && fresh>=0 && pageNine>=0,"Sentences found: "+grown.sentences());
        require(grown.mark(second).underlined() && grown.mark(second).note().equals("기억할 것") && grown.mark(second).memo(),
                "Underline and memo follow their sentence");
        require(grown.mark(pageNine).highlighted() && !grown.mark(fresh).underlined() && !grown.mark(fresh).highlighted()
                && !grown.mark(second-1).underlined(),"Other sentences keep only their own marks");
        grown.highlight(fresh);
        ReadingDocument reopened=new ReadingDocument(notes,grown.text());
        require(reopened.mark(second).underlined() && reopened.mark(fresh).highlighted() && reopened.mark(pageNine).highlighted(),
                "Carried marks are saved with the next change");

        // A book with more collected text than fits the page scrolls; marks land on what is in view.
        edt(()->{
            StringBuilder many=new StringBuilder();
            for(int i=1;i<=60;i++)many.append(i>1?"\n":"").append("문장 ").append(i).append("번.");
            ReadingPanel page=new ReadingPanel("scroll-"+System.nanoTime(),"긴 책","",many.toString(),List.of(),"",()->{});
            page.setSize(412,917);layoutAll(page);
            archivefever.ui.components.ReadingText text=page.readingText();
            require(text.maxScroll()>0 && text.scrollOffset()==0,"Long text can be scrolled: "+text.maxScroll());
            text.setTool(archivefever.ui.components.ReadingText.Tool.PENCIL);
            clickAt(text,40,7);
            require(text.document().mark(0).underlined(),"A click at the top marks the first sentence");
            text.scrollTo(Float.MAX_VALUE);
            require(text.scrollOffset()==text.maxScroll(),"Scrolling stops at the end");
            clickAt(text,40,7);
            int marked=0,last=-1;
            for(int i=0;i<text.document().sentences().size();i++)if(text.document().mark(i).underlined()){marked++;last=i;}
            require(marked==2 && last>20,"After scrolling, the same spot is a later sentence: "+last);
            drag(text,40,200,40,400);
            require(text.scrollOffset()<text.maxScroll(),"Dragging down scrolls back up");
            require(marked==countUnderlined(text),"A drag marks nothing");
            ReadingPanel brief=new ReadingPanel("brief-"+System.nanoTime(),"짧은 책","","한 문장.",List.of(),"",()->{});
            brief.setSize(412,917);layoutAll(brief);
            brief.readingText().scrollTo(100);
            require(brief.readingText().maxScroll()==0 && brief.readingText().scrollOffset()==0,"Short text does not scroll");
            return true;
        });

        // Nothing of an earlier run is left on this device for the book the flow below reads.
        ReadingDocument.forgetLocalMarks(ISBN);

        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/",LibraryFlowSmokeTest::answer);
        server.start();
        String address="http://127.0.0.1:"+server.getAddress().getPort();
        HostBridge host=new HostBridge() {
            @Override public void showCamera(int x,int y,int width,int height,int cornerRadius) {
                hostEvents.add("show "+x+","+y+" "+width+"x"+height);
            }
            @Override public void hideCamera() {hostEvents.add("hide");}
            @Override public void capturePhoto() {hostEvents.add("capture");}
            @Override public void startScan(String scanId) {scanRequests.add(scanId);hostEvents.add("scan "+scanId);}
            @Override public void capturePage() {hostEvents.add("page");}
            @Override public void savePassage(String isbn13,Consumer<PassageResult> done) {
                hostEvents.add("save "+isbn13);
                PassageResult outcome=nextSave;
                if(outcome==null) {unansweredSave=done;return;}
                // Like the phone: it stores the passage itself, then answers a moment later.
                if(outcome==PassageResult.SAVED)passages.add(new Stored(1,12,"모은 문장."));
                Timer answer=new Timer(30,e->done.accept(outcome));answer.setRepeats(false);answer.start();
            }
            @Override public void editPassage(String isbn13,long id,Integer page,String text,Consumer<PassageResult> done) {
                hostEvents.add("edit "+isbn13+" "+id+" "+page+" "+text);
                java.util.function.Function<Stored,PassageResult> edit=nextEdit;
                if(edit==null)return;
                // Like the page in the phone: it changes the passage on the server itself, then answers a moment later.
                PassageResult outcome=edit.apply(new Stored(id,page,text));
                Timer answer=new Timer(30,e->done.accept(outcome));answer.setRepeats(false);answer.start();
            }
            @Override public void signIn(Consumer<String> done) {hostEvents.add("signIn");done.accept("mina");}
            @Override public void signOut() {hostEvents.add("signOut");}
            @Override public void openUrl(String url) {openedUrls.add(url);}
        };
        edt(()->{
            app=new AppShell(()->{},()->{},()->{});
            frame=new JFrame();frame.setContentPane(app);frame.pack();
            app.setSize(412,917);app.doLayout();
            require(find(app,Bookshelf.class).getComponentCount()>0,"Demo books before connecting");
            LibraryController library=new LibraryController(new LibraryApi(new ApiClient(address)),host);
            app.connect(library);
            require(find(app,Bookshelf.class).getComponentCount()>0 && hostEvents.isEmpty(),"Nothing is asked of the server before signing in");
            ((JButton)named(app,"카카오 로그인")).doClick(0);
            return true;
        });
        require(hostEvents.get(0).equals("signIn"),"The login button signs in through the host: "+hostEvents);
        waitFor("server shelf replaces demo books",()->find(app,Bookshelf.class).getComponentCount()==0);
        require(edt(()->app.currentDestination()==Destination.ARCHIVE && "mina".equals(profileName())),
                "Signed in as the person the host named");
        require(edt(()->named(app,"도서 정보 출처 YES24")!=null),"Shelf credits YES24");
        require(lastHostEvent().equals("hide"),"No camera preview away from the camera screen: "+lastHostEvent());

        // Photo arrives and the book is found: confirm it onto the shelf.
        startScan("scan-1");
        scans.put("scan-1","RESOLVED");
        waitFor("book offered",()->canConfirm());
        require(edt(()->TITLE.equals(((JTextArea)named(app,"조회된 도서 제목")).getText())),"Title shown");
        require(edt(()->named(app,"표지")!=null),"Cover shown");
        click("YES24 상품 페이지 열기");
        require(openedUrls.equals(List.of(LINK)),"Source link opens the YES24 product page");
        click("스캔한 도서 추가");
        waitFor("popup closes on the shelf",()->!find(app,ScanConfirmationPopup.class).isVisible()
                && app.currentDestination()==Destination.ARCHIVE);
        waitFor("book on the shelf",()->find(app,Bookshelf.class).getComponentCount()==1);
        require(edt(()->TITLE.equals(find(app,Bookshelf.class).getComponent(0).getAccessibleContext().getAccessibleName())),
                "Spine carries the title");
        require(!cancelled.contains("scan-1"),"A confirmed scan is not cancelled");
        require(lastHostEvent().equals("hide"),"The preview goes away with the camera screen: "+lastHostEvent());

        // A shelf book with nothing collected yet opens on an empty reading page.
        edt(()->{
            Component spine=find(app,Bookshelf.class).getComponent(0);
            spine.dispatchEvent(new java.awt.event.MouseEvent(spine,java.awt.event.MouseEvent.MOUSE_CLICKED,
                    System.currentTimeMillis(),0,3,3,1,false));
            return true;
        });
        click("책 읽기 선택");
        waitFor("reading page",()->reading()!=null);
        require(edt(()->reading().readingText().document().text().isEmpty()),"Nothing collected yet");
        leaveReading();

        // Sentence capture for a shelf book: same camera, and the page is photographed at the press.
        openSentenceCapture();
        int requestsBefore=scanRequests.size();
        click("문장 촬영");
        require(lastHostEvent().equals("page"),"Page photo is taken at the press: "+lastHostEvent());
        require(edt(()->saveButton()!=null && saveButton().isEnabled()),"Sentence screen moves on to its save state");
        Thread.sleep(300);
        require(scanRequests.size()==requestsBefore && created.get()==1,"A page photo starts no book scan");

        // Back from the page under review: the same book, the camera again for another photo.
        edt(()->{
            JComponent panel=find(app,archivefever.ui.screens.SentenceScanPanel.class);
            panel.getActionMap().get("return").actionPerformed(new java.awt.event.ActionEvent(panel,0,"return"));
            return true;
        });
        require(edt(()->saveButton()==null && named(app,"문장 촬영")!=null),"Back from a captured page offers scan again");
        require(lastHostEvent().equals("show 52,141 272x369"),"Retaking brings the live preview back: "+lastHostEvent());
        click("문장 촬영");
        require(lastHostEvent().equals("page") && edt(()->saveButton()!=null && saveButton().isEnabled()),"Second page photo");

        // Saving is the host's work; whatever stops it, the reader can press save again.
        save(HostBridge.PassageResult.NOTHING_PICKED,"저장할 줄을 먼저 골라 주세요");
        save(HostBridge.PassageResult.CANCELLED,"저장할 줄을 고른 뒤 눌러 주세요");
        save(HostBridge.PassageResult.FAILED,"저장하지 못했어요. 다시 눌러 주세요");
        require(passages.isEmpty(),"Nothing saved so far");

        // Saved: the book's reading page opens with the passage on it.
        nextSave=HostBridge.PassageResult.SAVED;
        click("문장 저장");
        waitFor("reading page after saving",()->reading()!=null);
        require(edt(()->reading().readingText().document().text().equals("p.12\n모은 문장.")),"Collected passage is the page's text");
        require(lastHostEvent().equals("hide"),"The preview goes away after saving: "+lastHostEvent());
        leaveReading();
        require(edt(()->saveButton()==null && named(app,"문장 촬영")==null),"The sentence screen is closed");

        // The server stores a passage sent as JSON (the desktop host's way of saving).
        LibraryApi api=new LibraryApi(new ApiClient(address));
        LibraryApi.Passage typed=api.addPassage(ISBN,null,"직접 \"입력\"한\n문장.");
        require(typed.page()==null && typed.text().equals("직접 \"입력\"한\n문장.") && api.passages(ISBN).size()==2,
                "Passage round trip: "+typed);

        // Marks made on the reading page are kept by the server, by passage and by sentence in it.
        // The page's sentences: p.12 | 모은 문장. | p | . | — | 직접 "입력"한 | 문장.
        // Its lines: p.12 / 모은 문장. / (empty) / p.— / 직접 "입력"한 / 문장.
        openReading(0);
        edt(()->{
            archivefever.ui.components.ReadingText text=reading().readingText();
            text.document().underline(1);text.document().highlight(6);
            text.document().enableMemo(6);
            return true;
        });
        require(marks.equals(Map.of("1/0","{\"sentence\":0,\"underlined\":true,\"highlighted\":false,\"note\":\"\"}",
                "2/1","{\"sentence\":1,\"underlined\":false,\"highlighted\":true,\"note\":\"\"}")),
                "Underline and highlight are saved as made; opening the memo box saves nothing: "+marks);
        edt(()->{reading().readingText().document().saveMemo(6,"서버에 남는 메모");return true;});
        require(marks.get("2/1").equals("{\"sentence\":1,\"underlined\":false,\"highlighted\":true,\"note\":\"서버에 남는 메모\"}"),
                "A memo joins the marks of its sentence: "+marks);
        // What the server will not take does not show as made.
        marksFail=true;
        edt(()->{reading().readingText().setTool(archivefever.ui.components.ReadingText.Tool.PENCIL);return true;});
        pressLine(4);
        require(edt(()->!reading().readingText().document().mark(5).underlined()),"A mark the server refused is not shown");
        marksFail=false;
        pressLine(4);
        require(edt(()->reading().readingText().document().mark(5).underlined()) && marks.containsKey("2/0"),"Pressed again, it is made and kept");
        pressLine(4);
        require(edt(()->!reading().readingText().document().mark(5).underlined()) && !marks.containsKey("2/0"),
                "Pressed once more, the underline is off and its mark gone from the server");
        leaveReading();
        // Read again, the page has its marks from the server, not from this device.
        openReading(0);
        require(edt(()->{
            ReadingDocument document=reading().readingText().document();
            return document.mark(1).underlined() && !document.mark(1).highlighted() && document.mark(6).highlighted()
                    && document.mark(6).memo() && document.mark(6).note().equals("서버에 남는 메모") && !document.mark(5).underlined();
        }),"Marks come back with the passages");

        // With a tool in hand a press marks; the tool pressed again is put down, and then a press is for the passage itself.
        int editsBefore=(int)hostEvents.stream().filter(event->event.startsWith("edit ")).count();
        edt(()->{reading().stationeryButtons().get(0).doClick(0);return true;});
        require(edt(()->reading().readingText().tool()==archivefever.ui.components.ReadingText.Tool.PENCIL),"The pencil is picked up");
        edt(()->{reading().stationeryButtons().get(0).doClick(0);return true;});
        require(edt(()->reading().readingText().tool()==archivefever.ui.components.ReadingText.Tool.NONE
                && !reading().stationeryButtons().get(0).isActive()),"Pressed again, the pencil is put down");
        nextEdit=null;
        pressLine(0);
        require(hostEvents.stream().filter(event->event.startsWith("edit ")).count()==editsBefore,"A page note is not a passage to correct");
        // The host lets the reader correct the passage; the page is then read again, and the passage keeps its marks.
        nextEdit=passage->{
            passages.set(passages.indexOf(passage),new Stored(passage.id(),15,"고친 문장."));
            return HostBridge.PassageResult.SAVED;
        };
        pressLine(1);
        waitFor("host asked to correct the passage",()->lastHostEvent().equals("edit "+ISBN+" 1 12 모은 문장."));
        waitFor("page read again after the correction",()->"p.15\n고친 문장.\n\np.—\n직접 \"입력\"한\n문장.".equals(pageText()));
        require(edt(()->reading().readingText().document().mark(1).underlined()),"A corrected passage keeps its marks");
        // Backing out of the correction, or failing at it, leaves the page as it is.
        nextEdit=passage->HostBridge.PassageResult.CANCELLED;
        pressLine(1);
        waitFor("host asked again",()->lastHostEvent().equals("edit "+ISBN+" 1 15 고친 문장."));
        Thread.sleep(200);
        require(edt(()->pageText().startsWith("p.15\n고친 문장.")),"A cancelled correction changes nothing");
        nextEdit=passage->HostBridge.PassageResult.FAILED;
        ReadingPanel beforeFailure=edt(LibraryFlowSmokeTest::reading);
        pressLine(1);
        Thread.sleep(200);
        require(edt(()->reading()==beforeFailure && pageText().startsWith("p.15\n고친 문장.")),"A failed correction leaves the page showing");
        // Deleted, the passage is gone from the page, and its marks from the server.
        nextEdit=passage->{
            passages.remove(passage);marks.keySet().removeIf(key->key.startsWith(passage.id()+"/"));
            return HostBridge.PassageResult.DELETED;
        };
        pressLine(1);
        waitFor("page read again after the deletion",()->"직접 \"입력\"한\n문장.".equals(pageText()));
        require(edt(()->reading().readingText().document().mark(1).highlighted()
                && reading().readingText().document().mark(1).note().equals("서버에 남는 메모")),"The other passage keeps its marks: now sentence 1");
        // The desktop host's way of correcting and deleting: through the API.
        LibraryApi.Passage corrected=api.updatePassage(ISBN,2,31,"API로 고친 문장.");
        require(corrected.id()==2 && corrected.page()==31 && corrected.text().equals("API로 고친 문장.") && corrected.marks().size()==1,
                "Passage correction round trip: "+corrected);
        api.deletePassage(ISBN,2);
        require(api.passages(ISBN).isEmpty() && marks.isEmpty(),"Passage deletion round trip");
        leaveReading();

        // Marks made when they were still kept on the device are handed over the first time the book is read.
        LibraryApi.Passage old=api.addPassage(ISBN,40,"기기에 표시해 둔 문장. 표시 없는 문장.");
        ReadingDocument onDevice=new ReadingDocument(ISBN,"p.40\n기기에 표시해 둔 문장. 표시 없는 문장.");
        onDevice.highlight(1);onDevice.saveMemo(1,"기기에 있던 메모");onDevice.underline(0);
        require(ReadingDocument.hasLocalMarks(ISBN) && marks.isEmpty(),"The marks are on the device only");
        openReading(0);
        require(marks.equals(Map.of(old.id()+"/0","{\"sentence\":0,\"underlined\":false,\"highlighted\":true,\"note\":\"기기에 있던 메모\"}")),
                "The device's marks are now the server's; the one on the page note had no passage: "+marks);
        require(edt(()->reading().readingText().document().mark(1).highlighted()
                && reading().readingText().document().mark(1).note().equals("기기에 있던 메모")
                && !reading().readingText().document().mark(2).highlighted()) && !ReadingDocument.hasLocalMarks(ISBN),
                "They show on the page, and the device's file has done its work");
        leaveReading();
        api.deletePassage(ISBN,old.id());
        require(marks.isEmpty(),"Cleared for what follows");

        // A book whose barcode is not to hand is found by its title: searched, picked, looked at, confirmed.
        edt(()->{app.navigate(Destination.ADD_BOOK);return true;});
        click("도서 검색");
        require(lastHostEvent().equals("hide"),"The search has no camera preview: "+lastHostEvent());
        require(edt(()->named(app,"도서 스캔")==null && named(app,"도서 검색어")!=null),"The search takes the add-book screen's place");
        edt(()->{JTextField field=(JTextField)named(app,"도서 검색어");field.setText("없는 책");field.postActionEvent();return true;});
        waitFor("nothing found",()->searches.equals(List.of("없는 책")) && !find(app,archivefever.ui.screens.BookSearchPanel.class).isBusyForTest());
        require(edt(()->named(app,FOUND_TITLE+" 고르기")==null),"Nothing to pick when nothing was found");
        edt(()->{JTextField field=(JTextField)named(app,"도서 검색어");field.setText(" 채식 ");field.postActionEvent();return true;});
        waitFor("search results",()->named(app,FOUND_TITLE+" 고르기")!=null);
        require(searches.equals(List.of("없는 책","채식")),"The words are sent as typed, without the spaces around them: "+searches);
        require(edt(()->named(app,TITLE+" 고르기")==null),"A book already on the shelf is listed, and not offered again");
        click("검색 결과 출처 YES24");
        require(openedUrls.get(openedUrls.size()-1).equals("https://www.yes24.com/"),"The search credits YES24: "+openedUrls);
        click(FOUND_TITLE+" 고르기");
        waitFor("found book offered",()->canConfirm());
        require(edt(()->FOUND_TITLE.equals(((JTextArea)named(app,"조회된 도서 제목")).getText()) && named(app,"표지")!=null),"The picked book is shown with its cover");
        click("YES24 상품 페이지 열기");
        require(openedUrls.get(openedUrls.size()-1).equals(FOUND_LINK),"Its source link opens its own YES24 page");
        // Not this one after all: back on the results, and still no camera.
        click("스캔 다시 하기");
        require(edt(()->!find(app,ScanConfirmationPopup.class).isVisible() && named(app,FOUND_TITLE+" 고르기")!=null)
                && lastHostEvent().equals("hide") && !foundInLibrary,"No leaves the results as they were: "+lastHostEvent());
        click(FOUND_TITLE+" 고르기");
        waitFor("found book offered again",()->canConfirm());
        click("스캔한 도서 추가");
        waitFor("found book on the shelf",()->find(app,Bookshelf.class).getComponentCount()==2
                && app.currentDestination()==Destination.ARCHIVE && !find(app,ScanConfirmationPopup.class).isVisible());
        require(foundInLibrary && created.get()==1,"The book was added by its ISBN, with no scan: "+created.get());
        // Leaving the search by its back link brings the camera screen and its preview back.
        edt(()->{app.navigate(Destination.ADD_BOOK);return true;});
        click("도서 검색");
        click("뒤로가기");
        require(edt(()->named(app,"도서 스캔")!=null && named(app,"도서 검색어")==null) && lastHostEvent().startsWith("show "),
                "Back from the search: "+lastHostEvent());
        edt(()->{app.navigate(Destination.ARCHIVE);return true;});

        // A book leaves the shelf when the reader says so twice, and not while it is being read with a friend.
        pressSpine(1);
        click("책 빼기 선택");
        click("no");
        Thread.sleep(150);
        require(foundInLibrary && edt(()->find(app,Bookshelf.class).getComponentCount()==2),"Asked first: no keeps the book");
        foundBeingSwapped=true;
        pressSpine(1);
        click("책 빼기 선택");
        click("yes");
        waitFor("told why the book stays",()->shelfNotice("교환독서 중인 책은 뺄 수 없어요"));
        require(foundInLibrary && edt(()->find(app,Bookshelf.class).getComponentCount()==2),"A book being read with a friend stays");
        foundBeingSwapped=false;
        pressSpine(1);
        click("책 빼기 선택");
        click("yes");
        waitFor("book off the shelf",()->find(app,Bookshelf.class).getComponentCount()==1);
        require(!foundInLibrary && edt(()->TITLE.equals(find(app,Bookshelf.class).getComponent(0).getAccessibleContext().getAccessibleName())),
                "The other book is the one that stays");

        // An answer that arrives after the reader has left the screen opens nothing.
        openSentenceCapture();
        click("문장 촬영");
        nextSave=null;unansweredSave=null;
        click("문장 저장");
        waitFor("save left unanswered",()->unansweredSave!=null);
        require(edt(()->saveButton()!=null && !saveButton().isEnabled()),"Save cannot be pressed twice while saving");
        edt(()->{app.navigate(Destination.ARCHIVE);return true;});
        require(lastHostEvent().equals("hide"),"Leaving sentence capture turns the preview off: "+lastHostEvent());
        edt(()->{unansweredSave.accept(HostBridge.PassageResult.SAVED);return true;});
        Thread.sleep(400);
        require(edt(()->reading()==null && app.currentDestination()==Destination.ARCHIVE),"A late save answer is ignored");

        // Photo has no barcode: say so and offer nothing.
        startScan("scan-2");
        scans.put("scan-2","NO_BARCODE");
        waitFor("no barcode message",()->"no barcode found".equals(statusText()));
        require(edt(()->!canConfirm()),"Nothing to confirm without a book");
        click("스캔 다시 하기");
        require(edt(()->!find(app,ScanConfirmationPopup.class).isVisible()),"No closes the popup");
        require(lastHostEvent().startsWith("show "),"Back on the camera screen the preview returns: "+lastHostEvent());

        // Popup closed before any photo: the waiting scan is cancelled on the server.
        startScan("scan-3");
        click("스캔 다시 하기");
        waitFor("pending scan cancelled",()->cancelled.contains("scan-3"));
        scans.put("scan-3","RESOLVED");
        Thread.sleep(1200);
        require(edt(()->!find(app,ScanConfirmationPopup.class).isVisible() && find(app,Bookshelf.class).getComponentCount()==1),
                "A late answer for an abandoned scan changes nothing");

        // Signing out ends the session and takes this person's books off the screen;
        // signing in again brings them back.
        edt(()->{app.navigate(Destination.MENU);return true;});
        click("profile");
        click("로그아웃");
        require(hostEvents.subList(hostEvents.size()-2,hostEvents.size()).equals(List.of("hide","signOut")),
                "Signing out is passed on to the host: "+hostEvents);
        require(edt(()->app.currentDestination()==null && find(app,Bookshelf.class).getComponentCount()==0
                && named(app,"카카오 로그인")!=null),"Back on the login screen with an empty shelf");
        click("카카오 로그인");
        waitFor("shelf of the person signing in",()->find(app,Bookshelf.class).getComponentCount()==1);
        require(lastHostEvent().equals("hide") && hostEvents.contains("signIn"),"Signed in again: "+hostEvents);

        // Server unreachable: the popup reports it instead of waiting forever.
        server.stop(0);
        edt(()->{app.navigate(Destination.ADD_BOOK);return true;});
        click("도서 스캔");
        waitFor("request failed message",()->"request failed".equals(statusText()));

        System.out.println("PASS: JSON, sign in, server shelf, YES24 credit, camera preview requests, capture at press, scan -> book -> confirm -> shelf, page capture -> retake -> save -> reading page, marks carried over, long text scrolls, "
                +"marks kept on the server, tool put down, passage corrected and deleted, search -> pick -> confirm -> shelf, book removed, "
                +"no barcode, cancel, late answer, sign out and in again, server down");
    }

    public static void main(String[] args) {
        try {run();System.exit(0);}
        catch(Throwable error) {error.printStackTrace();System.exit(1);}
    }
}
