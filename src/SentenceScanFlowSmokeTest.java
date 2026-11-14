import archivefever.ui.navigation.*;
import archivefever.ui.components.*;
import archivefever.ui.screens.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.concurrent.atomic.AtomicInteger;
import javax.imageio.ImageIO;

public class SentenceScanFlowSmokeTest {
    private static <T> T find(Container root,Class<T> type){
        for(Component child:root.getComponents()){
            if(type.isInstance(child))return type.cast(child);
            if(child instanceof Container c){T result=find(c,type);if(result!=null)return result;}
        }return null;
    }
    private static JButton button(Container root,String name){
        for(Component child:root.getComponents()){
            if(child instanceof JButton b && name.equals(b.getAccessibleContext().getAccessibleName()))return b;
            if(child instanceof Container c){JButton result=button(c,name);if(result!=null)return result;}
        }return null;
    }
    private static void layout(Container root){root.doLayout();for(Component child:root.getComponents())if(child instanceof Container c)layout(c);}
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static void click(ScaledAppView view,int x,int y){
        Component input=view.getComponent(0);int sx=(int)Math.round(x*view.scale()),sy=(int)Math.round(y*view.scale());
        for(int id:new int[]{MouseEvent.MOUSE_PRESSED,MouseEvent.MOUSE_RELEASED,MouseEvent.MOUSE_CLICKED})
            input.dispatchEvent(new MouseEvent(input,id,System.currentTimeMillis(),0,sx,sy,sx,sy,1,false,MouseEvent.BUTTON1));
    }
    private static void preview(AppShell app,String name)throws Exception{
        BufferedImage image=new BufferedImage(412,917,2);Graphics2D g=image.createGraphics();app.paint(g);g.dispose();
        ImageIO.write(image,"png",new File("/tmp/archivefever-"+name+".png"));
    }
    public static void main(String[]args)throws Exception{
        AtomicInteger sounds=new AtomicInteger();
        SwingUtilities.invokeAndWait(()->{try{
            AppShell app=new AppShell(sounds::incrementAndGet,()->{});ScaledAppView view=new ScaledAppView(app,.65);
            view.setSize(view.getPreferredSize());layout(view);find(app,KakaoLoginButton.class).doClick(0);layout(view);
            // First actual book label -> what's next -> sentence scan.
            click(view,30,130);
            require(find(app,BookSelectionPopup.class).isVisible(),"Book selection opens");
            click(view,262,450);layout(view);
            SentenceScanPanel panel=find(app,SentenceScanPanel.class);
            require(panel.isVisible() && panel.selectedBook()!=null && !panel.isCaptured(),"What's next scan route preserves book");
            require(!find(app,BookSelectionPopup.class).isVisible() && sounds.get()==0,"Entering camera does not play shutter");
            String selectedId=panel.selectedBook().id();
            preview(app,"sentence-camera");
            require(SwingUtilities.getDeepestComponentAt(app,205,739)==button(app,"문장 촬영"),"Scan hit area");
            click(view,205,739);layout(view);preview(app,"sentence-selection");
            require(sounds.get()==1 && panel.isCaptured() && panel.selectedBook().id().equals(selectedId),"One shutter and review for same book");
            JButton save=button(app,"문장 저장");
            require(save!=null && !save.isEnabled(),"OCR/save deliberately unavailable");
            require(!find(app,ScanConfirmationPopup.class).isVisible(),"No add-book confirmation in sentence flow");
            save.doClick(0);require(panel.isCaptured() && sounds.get()==1,"No fake OCR save or duplicate shutter");
            panel.getActionMap().get("return").actionPerformed(new ActionEvent(panel,0,"return"));
            require(!panel.isVisible() && panel.selectedBook()==null && !panel.isCaptured(),"Escape resets scan and returns to shelf");
            // Another book starts a fresh capture rather than retaining the previous save state.
            Bookshelf shelf=find(app,Bookshelf.class);Component other=shelf.getComponent(1);
            other.dispatchEvent(new MouseEvent(other,MouseEvent.MOUSE_CLICKED,System.currentTimeMillis(),0,3,3,1,false));
            button(app,"책 스캔 선택").doClick(0);layout(view);
            require(!panel.isCaptured() && !panel.selectedBook().id().equals(selectedId),"Different book context and reset");
            app.navigate(Destination.ADD_BOOK);layout(view);
            button(app,"도서 스캔").doClick(0);
            require(find(app,ScanConfirmationPopup.class).isVisible() && sounds.get()==2,"Add-book flow remains separate");
            require(!panel.isVisible() && panel.selectedBook()==null,"Leaving sentence scan clears transient context");
            System.out.println("PASS: scaled book chooser -> camera -> shutter -> save screen, selected book, reset, separate add-book flow, no OCR");
        }catch(Exception e){throw new RuntimeException(e);}});
    }
}
