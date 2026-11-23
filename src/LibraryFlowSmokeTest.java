import archivefever.api.ApiClient;
import archivefever.api.Json;
import archivefever.api.LibraryApi;
import archivefever.app.LibraryController;
import archivefever.host.HostBridge;
import archivefever.ui.components.Bookshelf;
import archivefever.ui.components.ScanConfirmationPopup;
import archivefever.ui.navigation.AppShell;
import archivefever.ui.navigation.Destination;
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
    private static AppShell app;

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
            exchange.getRequestBody().readAllBytes();
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
    private static String lastHostEvent() {return hostEvents.isEmpty()?"":hostEvents.get(hostEvents.size()-1);}
    private static String statusText() {
        return named(app,"도서 조회 상태") instanceof JLabel label?label.getText():null;
    }
    private static boolean canConfirm() {
        return named(app,"스캔한 도서 추가") instanceof JButton yes && yes.isEnabled();
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
            @Override public void openUrl(String url) {openedUrls.add(url);}
        };
        edt(()->{
            app=new AppShell(()->{},()->{},()->{});
            JFrame frame=new JFrame();frame.setContentPane(app);frame.pack();
            app.setSize(412,917);app.doLayout();
            require(find(app,Bookshelf.class).getComponentCount()>0,"Demo books before connecting");
            LibraryController library=new LibraryController(new LibraryApi(new ApiClient(address)),host);
            app.connect(library);library.reloadShelf();
            ((JButton)named(app,"카카오 로그인")).doClick(0);
            return true;
        });
        waitFor("server shelf replaces demo books",()->find(app,Bookshelf.class).getComponentCount()==0);
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

        // Sentence capture for a shelf book: same camera, and the photo is taken at the press.
        edt(()->{
            Component spine=find(app,Bookshelf.class).getComponent(0);
            spine.dispatchEvent(new java.awt.event.MouseEvent(spine,java.awt.event.MouseEvent.MOUSE_CLICKED,
                    System.currentTimeMillis(),0,3,3,1,false));
            return true;
        });
        click("책 스캔 선택");
        require(lastHostEvent().equals("show 52,141 272x369"),"Sentence capture asks for the live preview: "+lastHostEvent());
        int requestsBefore=scanRequests.size();
        click("문장 촬영");
        require(lastHostEvent().equals("capture"),"Page photo is taken at the press: "+lastHostEvent());
        require(edt(()->named(app,"문장 저장")!=null),"Sentence screen moves on to its save state");
        Thread.sleep(300);
        require(scanRequests.size()==requestsBefore && created.get()==1,"A page photo starts no book scan");
        edt(()->{app.navigate(Destination.ARCHIVE);return true;});
        require(lastHostEvent().equals("hide"),"Leaving sentence capture turns the preview off: "+lastHostEvent());

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

        // Server unreachable: the popup reports it instead of waiting forever.
        server.stop(0);
        edt(()->{app.navigate(Destination.ADD_BOOK);return true;});
        click("도서 스캔");
        waitFor("request failed message",()->"request failed".equals(statusText()));

        System.out.println("PASS: JSON, server shelf, YES24 credit, camera preview requests, capture at press, scan -> book -> confirm -> shelf, sentence capture, no barcode, cancel, late answer, server down");
    }

    public static void main(String[] args) {
        try {run();System.exit(0);}
        catch(Throwable error) {error.printStackTrace();System.exit(1);}
    }
}
