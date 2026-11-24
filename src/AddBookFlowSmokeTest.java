import archivefever.ui.navigation.*;
import archivefever.ui.components.*;
import archivefever.audio.SoundPlayer;
import javax.swing.*;
import javax.sound.sampled.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.concurrent.atomic.AtomicInteger;
import javax.imageio.ImageIO;

public class AddBookFlowSmokeTest {
    private static <T> T find(Container parent,Class<T> type){
        for(Component c:parent.getComponents()){
            if(type.isInstance(c))return type.cast(c);
            if(c instanceof Container child){T found=find(child,type);if(found!=null)return found;}
        }return null;
    }
    private static JButton button(Container parent,String name){
        for(Component c:parent.getComponents()){
            if(c instanceof JButton b && name.equals(b.getAccessibleContext().getAccessibleName()))return b;
            if(c instanceof Container child){JButton found=button(child,name);if(found!=null)return found;}
        }return null;
    }
    private static void layout(Container parent){parent.doLayout();for(Component c:parent.getComponents())if(c instanceof Container child)layout(child);}
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static void click(ScaledAppView view,int x,int y){
        Component input=view.getComponent(0);int sx=(int)Math.round(x*view.scale()),sy=(int)Math.round(y*view.scale());
        for(int id:new int[]{MouseEvent.MOUSE_PRESSED,MouseEvent.MOUSE_RELEASED,MouseEvent.MOUSE_CLICKED})
            input.dispatchEvent(new MouseEvent(input,id,System.currentTimeMillis(),0,sx,sy,sx,sy,1,false,MouseEvent.BUTTON1));
    }
    private static void preview(AppShell app,String name)throws Exception{
        BufferedImage image=new BufferedImage(412,917,2);Graphics2D g=image.createGraphics();app.paint(g);g.dispose();
        ImageIO.write(image,"png",new File(System.getProperty("java.io.tmpdir"),"archivefever-"+name+".png"));
    }
    public static void main(String[]args)throws Exception{
        try(AudioInputStream audio=SoundPlayer.openAudioStream("sounds/camera-shutter-click-07.wav")){
            require(audio.getFormat().getSampleSizeInBits()==16 && audio.getFormat().getChannels()==2,"24-bit WAV converted for playback");
            require(audio.readAllBytes().length>0,"WAV samples decoded");
        }
        AtomicInteger sounds=new AtomicInteger();
        SwingUtilities.invokeAndWait(()->{try{
            AppShell app=new AppShell(sounds::incrementAndGet);app.setSize(412,917);layout(app);
            find(app,KakaoLoginButton.class).doClick(0);app.navigate(Destination.ADD_BOOK);layout(app);
            preview(app,"addbook");
            Component target=SwingUtilities.getDeepestComponentAt(app,205,739);
            require(target==button(app,"도서 스캔"),"Scan hit area");
            int books=find(app,Bookshelf.class).getComponentCount();
            button(app,"도서 스캔").doClick(0);layout(app);preview(app,"scan-confirmation");
            ScanConfirmationPopup popup=find(app,ScanConfirmationPopup.class);
            require(sounds.get()==1 && popup.isVisible(),"Scan plays sound and opens confirmation");
            require(popup.resultArea().getComponentCount()==0,"API area is empty");
            require(!button(app,"스캔한 도서 추가").isEnabled(),"Cannot add a nonexistent API result");
            button(app,"도서 스캔").doClick(0);require(sounds.get()==1,"No duplicate scan behind modal");
            require(SwingUtilities.getDeepestComponentAt(app,45,865)==popup,"Modal blocks navigation");
            button(app,"스캔한 도서 추가").doClick(0);
            require(popup.isVisible() && find(app,Bookshelf.class).getComponentCount()==books,"No fake book added");
            button(app,"스캔 다시 하기").doClick(0);
            require(!popup.isVisible() && app.currentDestination()==Destination.ADD_BOOK,"No returns to scan");
            button(app,"도서 스캔").doClick(0);require(sounds.get()==2,"Rescan sound");
            popup.getActionMap().get("dismiss").actionPerformed(new ActionEvent(app,0,"dismiss"));
            require(!popup.isVisible(),"Escape dismisses");
            button(app,"도서 스캔").doClick(0);app.navigate(Destination.MENU);
            require(!popup.isVisible(),"Leaving add book clears modal");
            app.navigate(Destination.ADD_BOOK);
            ScaledAppView scaled=new ScaledAppView(app,.65);scaled.setSize(scaled.getPreferredSize());layout(scaled);
            click(scaled,205,739);require(popup.isVisible() && sounds.get()==4,"Scaled scan and sound");
            click(scaled,145,540);require(!popup.isVisible(),"Scaled no button");
            click(scaled,205,739);click(scaled,10,10);require(!popup.isVisible(),"Scaled backdrop dismiss");
            System.out.println("PASS: shutter WAV conversion, scan, sound callback, empty camera/API shell, modal, retry, no fake book, scaled input");
        }catch(Exception e){throw new RuntimeException(e);}});
    }
}
