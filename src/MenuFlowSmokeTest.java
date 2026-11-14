import archivefever.ui.navigation.*;
import archivefever.ui.components.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

public class MenuFlowSmokeTest {
    private static AppShell app;
    private static final java.util.concurrent.atomic.AtomicInteger cuts=new java.util.concurrent.atomic.AtomicInteger();
    private static final java.util.concurrent.atomic.AtomicInteger bells=new java.util.concurrent.atomic.AtomicInteger();
    private static MenuObjectButton hover;
    private static Rectangle hoverBounds;
    private static <T> T find(Container parent,Class<T> type){
        for(Component child:parent.getComponents()){
            if(type.isInstance(child))return type.cast(child);
            if(child instanceof Container container){T found=find(container,type);if(found!=null)return found;}
        }return null;
    }
    private static JButton button(Container parent,String name){
        for(Component child:parent.getComponents()){
            if(child instanceof JButton button && name.equals(button.getAccessibleContext().getAccessibleName()))return button;
            if(child instanceof Container container){JButton found=button(container,name);if(found!=null)return found;}
        }return null;
    }
    private static void layout(Container parent){parent.doLayout();for(Component child:parent.getComponents())if(child instanceof Container container)layout(container);}
    private static void scaledClick(ScaledAppView view,int x,int y){
        Component input=view.getComponent(0);
        int sx=(int)Math.round(x*view.scale()),sy=(int)Math.round(y*view.scale());
        for(int id:new int[]{MouseEvent.MOUSE_PRESSED,MouseEvent.MOUSE_RELEASED,MouseEvent.MOUSE_CLICKED})
            input.dispatchEvent(new MouseEvent(input,id,System.currentTimeMillis(),0,sx,sy,sx,sy,1,false,MouseEvent.BUTTON1));
    }
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static void preview(String name)throws Exception{
        BufferedImage image=new BufferedImage(412,917,2);Graphics2D g=image.createGraphics();app.paint(g);g.dispose();
        ImageIO.write(image,"png",new File("/tmp/archivefever-"+name+".png"));
    }
    public static void main(String[] args)throws Exception{
        SwingUtilities.invokeAndWait(()->{try{
            app=new AppShell(()->{},bells::incrementAndGet,cuts::incrementAndGet);app.setSize(412,917);layout(app);
            find(app,KakaoLoginButton.class).doClick(0);app.navigate(Destination.MENU);layout(app);
            require(app.currentDestination()==Destination.MENU,"Menu route");
            preview("menu");
            int[][] points={{110,405},{230,480},{125,598}};
            String[] names={"edit","notification","profile"};
            for(int i=0;i<points.length;i++){
                Component target=SwingUtilities.getDeepestComponentAt(app,points[i][0],points[i][1]);
                require(target instanceof MenuObjectButton && names[i].equals(((JButton)target).getActionCommand()),"Paired label hit "+names[i]+": "+target);
            }
            button(app,"edit").doClick(0);require(cuts.get()==1,"Edit plays scissors sound");
            require(!find(app,NotificationPopup.class).isVisible() && !find(app,ProfilePopup.class).isVisible(),"Edit deliberately unimplemented");
            button(app,"notification").doClick(0);layout(app);preview("notifications");
            NotificationPopup notifications=find(app,NotificationPopup.class);
            require(notifications.isVisible(),"Notification opens");
            require(bells.get()==1,"Notification plays bell sound");
            button(app,"notification").doClick(0);require(bells.get()==1,"No duplicate sound behind modal");
            require(SwingUtilities.getDeepestComponentAt(app,10,880)==notifications,"Popup blocks navigation");
            BufferedImage image=new BufferedImage(412,917,2);Graphics2D g=image.createGraphics();app.paint(g);g.dispose();
            notifications.getActionMap().get("dismiss").actionPerformed(new ActionEvent(app,0,"dismiss"));
            BufferedImage plain=new BufferedImage(412,917,2);g=plain.createGraphics();app.paint(g);g.dispose();
            require(image.getRGB(10,0)!=plain.getRGB(10,0),"Mask covers top row");
            button(app,"notification").doClick(0);
            int beforeConfirm=bells.get();
            button(app,"친구 요청 확인").doClick(0);
            require(bells.get()==beforeConfirm+1,"Friend Confirm plays bell");
            button(app,"친구 요청 확인").doClick(0);
            require(bells.get()==beforeConfirm+1,"Disabled Confirm does not replay bell");
            require(!button(app,"친구 요청 확인").isEnabled() && button(app,"도서 교환 요청 확인").isEnabled(),"Separate confirmations");
            notifications.getActionMap().get("dismiss").actionPerformed(new ActionEvent(app,0,"dismiss"));
            button(app,"notification").doClick(0);
            require(!button(app,"친구 요청 확인").isEnabled(),"Confirmation survives reopening");
            int beforeSwap=bells.get();button(app,"도서 교환 요청 확인").doClick(0);
            require(bells.get()==beforeSwap+1,"Swap Confirm plays bell");
            require(!button(app,"도서 교환 요청 확인").isEnabled(),"Swap confirm");
            notifications.dispatchEvent(new MouseEvent(notifications,MouseEvent.MOUSE_CLICKED,System.currentTimeMillis(),0,5,5,1,false));
            require(!notifications.isVisible(),"Outside dismiss");
            button(app,"profile").doClick(0);layout(app);preview("profile");
            require(find(app,ProfilePopup.class).isVisible(),"Profile opens");
            button(app,"로그아웃").doClick(0);
            require(app.currentDestination()==null && !find(app,BookmarkNavigation.class).isVisible(),"Logout resets UI session");
            app.navigate(Destination.MENU);require(app.currentDestination()==null,"Cannot navigate when logged out");
            find(app,KakaoLoginButton.class).doClick(0);app.navigate(Destination.MENU);layout(app);
            hover=(MenuObjectButton)button(app,"profile");hoverBounds=hover.getBounds();
            hover.dispatchEvent(new MouseEvent(hover,MouseEvent.MOUSE_ENTERED,System.currentTimeMillis(),0,30,30,0,false));
        }catch(Exception e){throw new RuntimeException(e);}});
        Thread.sleep(400);
        SwingUtilities.invokeAndWait(()->{
            require(Math.abs(hover.liftAmount()-12)<.2 && hover.getBounds().equals(hoverBounds),"12px hover with stable hit area");
            hover.dispatchEvent(new MouseEvent(hover,MouseEvent.MOUSE_EXITED,System.currentTimeMillis(),0,0,0,0,false));
        });
        Thread.sleep(400);
        SwingUtilities.invokeAndWait(()->{
            require(hover.liftAmount()<.2,"Hover returns to rest");
            AppShell scaledApp=new AppShell(()->{},bells::incrementAndGet,cuts::incrementAndGet);ScaledAppView scaled=new ScaledAppView(scaledApp,.65);
            scaled.setSize(scaled.getPreferredSize());layout(scaled);
            scaledClick(scaled,200,450);scaledApp.navigate(Destination.MENU);layout(scaled);
            scaledClick(scaled,230,480);
            require(find(scaledApp,NotificationPopup.class).isVisible(),"Scaled notification click");
            scaledClick(scaled,302,368);
            require(!button(scaledApp,"친구 요청 확인").isEnabled(),"Scaled Confirm click");
            scaledClick(scaled,10,10);
            require(!find(scaledApp,NotificationPopup.class).isVisible(),"Scaled backdrop dismiss");
            scaledClick(scaled,125,598);
            require(find(scaledApp,ProfilePopup.class).isVisible(),"Scaled profile click");
            scaledClick(scaled,205,620);
            require(scaledApp.currentDestination()==null && !find(scaledApp,BookmarkNavigation.class).isVisible(),"Scaled logout click");
            System.out.println("PASS: menu pairs, 12px hover, modal blocking, confirmations, dismissal, profile, logout, login and scaled input");
        });
    }
}
