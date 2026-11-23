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

    /** Scan id to status, as the fake server currently reports it. */
    private static final Map<String,String> scans=new ConcurrentHashMap<>();
    private static final List<String> cancelled=new CopyOnWriteArrayList<>();
    private static final AtomicInteger created=new AtomicInteger();
    private static volatile boolean inLibrary;

    private static final List<String> scanRequests=new CopyOnWriteArrayList<>();
    private static final List<String> openedUrls=new CopyOnWriteArrayList<>();
    /** Camera and scan requests made of the host, in order. */
    private static final List<String> hostEvents=new CopyOnWriteArrayList<>();
    /** Collected passages of the book, as JSON objects in the order the fake server lists them. */
    private static final List<String> passages=new CopyOnWriteArrayList<>();
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
                    Object page=Json.field(sent,"page");
                    String saved="{\"id\":"+(passages.size()+1)+",\"page\":"+(page==null?"null":((Number)page).intValue())
                            +",\"text\":"+Json.quote(Json.string(sent,"text"))+"}";
                    passages.add(saved);body=saved.getBytes(StandardCharsets.UTF_8);status=201;
                } else body=("["+String.join(",",passages)+"]").getBytes(StandardCharsets.UTF_8);
            } else if(path.equals("/api/library/books")) {
                body=(inLibrary?"["+SHELF_BOOK+"]":"[]").getBytes(StandardCharsets.UTF_8);
            } else if(path.endsWith("/cover.png")) {body=png(200,300);type="image/png";}
            else if(path.endsWith("/spine.png")) {body=png(40,344);type="image/png";}
            else {body="{}".getBytes(StandardCharsets.UTF_8);status=404;}
            exchange.getResponseHeaders().add("Content-Type",type);
            exchange.sendResponseHeaders(status,body.length);
            exchange.getResponseBody().write(body);
        } catch(Exception e) {throw new RuntimeException(e);}
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
        require(LibraryController.readingText(List.of(new LibraryApi.Passage(7,"가."),new LibraryApi.Passage(7,"나."),
                new LibraryApi.Passage(9,"다."),new LibraryApi.Passage(null,"라."))).equals("p.7\n가.\n\n나.\n\np.9\n다.\n\np.—\n라."),
                "Passages become one text with the page noted where it changes");
        require(LibraryController.readingText(List.of(new LibraryApi.Passage(null,"가."),new LibraryApi.Passage(null,"나.")))
                .equals("가.\n\n나."),"Passages without pages carry no page notes");

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
                if(outcome==PassageResult.SAVED)passages.add("{\"id\":1,\"page\":12,\"text\":\"모은 문장.\"}");
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

        System.out.println("PASS: JSON, sign in, server shelf, YES24 credit, camera preview requests, capture at press, scan -> book -> confirm -> shelf, page capture -> retake -> save -> reading page, marks carried over, long text scrolls, no barcode, cancel, late answer, sign out and in again, server down");
    }

    public static void main(String[] args) {
        try {run();System.exit(0);}
        catch(Throwable error) {error.printStackTrace();System.exit(1);}
    }
}
