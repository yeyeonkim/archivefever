import archivefever.ui.components.*;
import archivefever.ui.navigation.*;
import archivefever.ui.screens.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;

/** Exercises the development-data friend and swap flow through real Swing components. */
public class SocialFlowSmokeTest {
    /** The swap page slide ignores swipes until it settles; Swing timers tick coarsely on Windows. */
    private static final int SLIDE_SETTLE_MILLIS=1500;
    private static AppShell app;
    private static JFrame frame;

    private static Component named(Container parent,String name) {
        for(int i=0;i<parent.getComponentCount();i++) {
            Component child=parent.getComponent(i);
            if(child.isVisible() && child.getAccessibleContext()!=null
                    && name.equals(child.getAccessibleContext().getAccessibleName()))return child;
            if(child.isVisible() && child instanceof Container container) {
                Component found=named(container,name);if(found!=null)return found;
            }
        }
        return null;
    }
    private static boolean hasText(Container parent,String text) {
        for(Component child:parent.getComponents()) {
            if(!child.isVisible())continue;
            if(child instanceof JLabel label && text.equals(label.getText()))return true;
            if(child instanceof Container container && hasText(container,text))return true;
        }
        return false;
    }
    private static <T> T edt(Callable<T> call) throws Exception {
        AtomicReference<T> value=new AtomicReference<>();
        AtomicReference<Exception> error=new AtomicReference<>();
        SwingUtilities.invokeAndWait(()->{try{value.set(call.call());}catch(Exception e){error.set(e);}});
        if(error.get()!=null)throw error.get();
        return value.get();
    }
    private static void waitFor(String what,Callable<Boolean> condition) throws Exception {
        long end=System.currentTimeMillis()+5000;
        while(System.currentTimeMillis()<end) {
            if(edt(condition))return;
            Thread.sleep(25);
        }
        throw new AssertionError("Timed out: "+what);
    }
    private static void waitNamed(String name) throws Exception {waitFor(name,()->named(app,name)!=null);}
    private static void waitText(String text) throws Exception {waitFor(text,()->hasText(app,text));}
    private static void click(String name) throws Exception {
        waitNamed(name);
        edt(()->{((JButton)named(app,name)).doClick(0);return true;});
    }

    public static void main(String[] args) throws Exception {
        edt(()->{
            app=new AppShell(()->{},()->{},()->{});
            frame=new JFrame();frame.setContentPane(app);frame.pack();
            app.setSize(412,917);app.doLayout();
            ((JButton)named(app,"카카오 로그인")).doClick(0);
            app.navigate(Destination.FRIEND);
            return true;
        });
        long before=System.nanoTime();
        edt(()->true);
        require((System.nanoTime()-before)/1_000_000<200,"EDT stays responsive during loading");
        waitNamed("@jinlims2 선택");

        click("@gwanjun02 선택");click("remove");click("no");
        require(edt(()->named(app,"@gwanjun02 선택")!=null),"Declining keeps the friend");
        edt(()->{app.demoSocial().failNextRequest();return true;});
        click("@gwanjun02 선택");click("remove");click("yes");waitText("request failed");
        require(edt(()->named(app,"@gwanjun02 선택")!=null),"A failed removal keeps the friend");
        click("다시 시도");
        waitFor("removal notice",()->"parkgwanjun removed".equals(find(app,FriendPanel.class).noticeForTest()));
        require(edt(()->named(app,"@gwanjun02 선택")==null),"Removed friend leaves the list");
        waitFor("notice clears",()->find(app,FriendPanel.class).noticeForTest().isEmpty());
        click("친구 찾기");waitNamed("친구 검색어");
        search("@gwanjun02");waitNamed("@gwanjun02 친구 신청");
        click("뒤로가기");waitNamed("@jinlims2 선택");
        require(edt(()->named(app,"@gwanjun02 선택")==null),"Removal survives a list reload");

        click("친구 찾기");waitNamed("친구 검색어");
        search("zzz");waitText("no results");
        edt(()->{((JTextField)named(app,"친구 검색어")).setText("@");return true;});
        waitNamed("@jinlims2 선택");
        require(edt(()->named(app,"@dohyun 친구 신청")==null),"Rows past the paper stay hidden until scrolled");
        edt(()->{
            FriendPanel panel=find(app,FriendPanel.class);
            panel.dispatchEvent(new java.awt.event.MouseWheelEvent(panel,MouseEvent.MOUSE_WHEEL,System.currentTimeMillis(),0,
                    200,400,0,false,java.awt.event.MouseWheelEvent.WHEEL_UNIT_SCROLL,3,5));
            return true;
        });
        waitNamed("@dohyun 친구 신청");
        search("@dohyun");waitFor("search done",()->!find(app,FriendPanel.class).isBusyForTest());
        befriend("@dohyun","choidohyun");click("@dohyun 선택");click("visit");waitText("no books yet");
        click("뒤로가기");waitNamed("@jinlims2 선택");

        click("친구 찾기");waitNamed("친구 검색어");
        search("@yeyeon");waitNamed("@yeyeon 신청 수락");
        edt(()->{app.demoSocial().failNextRequest();return true;});
        click("@yeyeon 신청 거절");waitText("request failed");
        require(edt(()->app.demoSocial().requestResult(archivefever.mock.DemoSocial.REQUESTER_ID)==null),"A failed decline stays unanswered");
        click("다시 시도");waitNamed("@yeyeon 친구 신청");
        require(edt(()->"Declined".equals(app.demoSocial().requestResult(archivefever.mock.DemoSocial.REQUESTER_ID))),"Decline is saved");
        require(edt(()->named(app,"@yeyeon 선택")==null),"A declined request does not add a friend");
        click("뒤로가기");waitNamed("@jinlims2 선택");
        require(edt(()->named(app,"@yeyeon 선택")==null),"A declined user stays off the list");

        click("친구 찾기");waitNamed("친구 검색어");
        search("@haneul");waitNamed("@haneul 친구 신청");befriend("@haneul","leehaneul");click("@haneul 선택");click("swap");waitNamed("시지프 신화");
        clickSpine("시지프 신화");click("no");
        require(edt(()->app.currentDestination()==Destination.FRIEND),"Declining stays on the shelf");

        edt(()->{app.demoSocial().failNextRequest();return true;});
        clickSpine("시지프 신화");click("yes");waitText("request failed");
        click("다시 시도");
        waitFor("swap route",()->app.currentDestination()==Destination.BOOK_SWAP);
        waitNamed("시지프 신화 기록 열기");

        click("페이지 1");waitNamed("소년이 온다 기록 열기");
        waitFor("no second page rows",()->named(app,"시지프 신화 기록 열기")==null);
        Thread.sleep(SLIDE_SETTLE_MILLIS);
        edt(()->{
            JButton cover=(JButton)named(app,"소년이 온다 기록 열기");
            drag(cover,MouseEvent.MOUSE_PRESSED,200);drag(cover,MouseEvent.MOUSE_DRAGGED,90);drag(cover,MouseEvent.MOUSE_RELEASED,90);
            return true;
        });
        waitNamed("시지프 신화 기록 열기");
        require(edt(()->!app.isReading()),"Swiping across a cover does not open it");
        Thread.sleep(SLIDE_SETTLE_MILLIS);
        click("페이지 1");waitNamed("소년이 온다 기록 열기");Thread.sleep(SLIDE_SETTLE_MILLIS);
        click("소년이 온다 기록 열기");
        waitFor("swap note",()->app.isReading());
        edt(()->{
            ((ReadingPanel)frame.getContentPane()).stationeryButtons().size();
            ((JButton)named(frame.getContentPane(),"return to archive")).doClick(0);
            return true;
        });
        require(edt(()->!app.isReading() && app.currentDestination()==Destination.BOOK_SWAP),"Return stays on Book swap");
        checkNotificationConfirm();
        System.out.println("PASS: friend list, search, request, approve, decline, notification, visit, empty, swap shelf, request error retry, swap cabinet, note, return");
        System.exit(0);
    }

    private static void befriend(String handle,String id) throws Exception {
        click(handle+" 친구 신청");waitNamed(handle+" 신청 대기");
        require(edt(()->named(app,handle+" 선택")==null),"A sent request is not a friend yet");
        edt(()->{app.demoSocial().approveRequestForDemo(id);return true;});
        search(handle);waitNamed(handle+" 선택");
    }

    private static void checkNotificationConfirm() throws Exception {
        edt(()->{
            app=new AppShell(()->{},()->{},()->{});
            app.setSize(412,917);app.doLayout();
            ((JButton)named(app,"카카오 로그인")).doClick(0);
            app.navigate(Destination.MENU);
            return true;
        });
        click("notification");waitNamed("친구 요청 거절");
        edt(()->{app.demoSocial().failNextRequest();return true;});
        click("친구 요청 확인");
        waitText("failed");
        require(edt(()->((JButton)named(app,"친구 요청 확인")).isEnabled()),"A failed answer can be retried");
        click("친구 요청 확인");
        waitFor("confirmed",()->"Confirmed".equals(((JButton)named(app,"친구 요청 확인")).getText()));
        require(edt(()->named(app,"친구 요청 거절")==null),"Decline disappears after answering");
        edt(()->{app.navigate(Destination.FRIEND);return true;});
        waitNamed("@jinlims2 선택");click("다음 페이지");waitNamed("@yeyeon 선택");
        edt(()->{app.navigate(Destination.MENU);return true;});
        click("notification");
        waitFor("answer kept",()->"Confirmed".equals(((JButton)named(app,"친구 요청 확인")).getText()));
    }

    private static void drag(Component target,int id,int x) {
        int modifiers=id==MouseEvent.MOUSE_RELEASED?0:MouseEvent.BUTTON1_DOWN_MASK;
        int button=id==MouseEvent.MOUSE_DRAGGED?MouseEvent.NOBUTTON:MouseEvent.BUTTON1;
        target.dispatchEvent(new MouseEvent(target,id,System.currentTimeMillis(),modifiers,x,20,1,false,button));
    }

    private static void clickSpine(String title) throws Exception {
        waitNamed(title);
        waitFor("idle",()->!find(app,FriendPanel.class).isBusyForTest());
        edt(()->{
            Component spine=named(app,title);
            spine.dispatchEvent(new MouseEvent(spine,MouseEvent.MOUSE_CLICKED,System.currentTimeMillis(),0,2,2,1,false));
            return true;
        });
    }
    private static void require(boolean value,String message) {if(!value)throw new AssertionError(message);}
    private static void search(String text) throws Exception {
        waitFor("idle before search",()->named(app,"친구 검색어")!=null && !find(app,FriendPanel.class).isBusyForTest());
        edt(()->{JTextField field=(JTextField)named(app,"친구 검색어");field.setText(text);field.postActionEvent();return true;});
    }
    private static <T> T find(Container parent,Class<T> type) {
        for(Component child:parent.getComponents()) {
            if(type.isInstance(child))return type.cast(child);
            if(child instanceof Container container) {T found=find(container,type);if(found!=null)return found;}
        }
        return null;
    }
}
