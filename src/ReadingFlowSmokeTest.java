import archivefever.model.BookSpine;
import archivefever.ui.components.BookSelectionPopup;
import archivefever.ui.components.StationeryButton;
import archivefever.ui.navigation.AppShell;
import archivefever.ui.screens.ReadingPanel;
import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.image.BufferedImage;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public final class ReadingFlowSmokeTest {
    public static void main(String[] args) throws Exception {
        BookSpine book=new BookSpine("test-book","테스트 도서",
                new BufferedImage(80,172,BufferedImage.TYPE_INT_ARGB),80);
        AtomicReference<BookSpine> selected=new AtomicReference<>();
        AtomicBoolean dismissed=new AtomicBoolean();
        SwingUtilities.invokeAndWait(() -> {
            BookSelectionPopup popup=new BookSelectionPopup(selected::set,()->dismissed.set(true));
            popup.setBook(book);
            JButton read=findButton(popup,"책 읽기 선택");
            JButton scan=findButton(popup,"책 스캔 선택");
            if(read==null || scan==null) throw new AssertionError("Popup actions are missing");
            read.doClick();
            scan.doClick();
        });
        if(selected.get()!=book) throw new AssertionError("Read action did not pass the selected book");
        if(!dismissed.get()) throw new AssertionError("Scan action no longer dismisses the popup");
        if(!AppShell.expandedModeForWidth(424) || AppShell.expandedModeForWidth(423))
            throw new AssertionError("Expanded mode width threshold is incorrect");

        if(!AppShell.expandedModeAfterResize(372,360,1024,false)
                || AppShell.expandedModeAfterResize(371,360,1024,false)
                || !AppShell.expandedModeAfterResize(1013,360,1024,true)
                || AppShell.expandedModeAfterResize(1012,360,1024,true)
                || !AppShell.expandedModeAfterResize(1024,360,1024,true)
                || AppShell.expandedModeAfterResize(360,360,1024,false))
            throw new AssertionError("Bidirectional snap threshold or stable snap size is incorrect");

        AtomicBoolean returned=new AtomicBoolean();
        ReadingPanel[] panelRef=new ReadingPanel[1];
        StationeryButton[] pencilRef=new StationeryButton[1];
        Rectangle[] portraitBounds=new Rectangle[1];
        SwingUtilities.invokeAndWait(() -> {
            ReadingPanel panel=new ReadingPanel(book,()->returned.set(true));
            panelRef[0]=panel;
            panel.setSize(412,917);
            panel.doLayout();
            Component scene=panel.getComponent(0);
            scene.setSize(412,917);
            scene.doLayout();
            if(panel.stationeryButtons().size()!=4)
                throw new AssertionError("Expected four interactive stationery items");
            JButton returnButton=findButton(panel,"return to archive");
            if(returnButton==null) throw new AssertionError("Return action is missing");
            returnButton.doClick(0);
            StationeryButton pencil=panel.stationeryButtons().get(0);
            pencilRef[0]=pencil;
            portraitBounds[0]=pencil.getBounds();
            dispatch(pencil,MouseEvent.MOUSE_ENTERED);

            BufferedImage rendered=new BufferedImage(412,917,BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics=rendered.createGraphics();
            panel.paint(graphics);
            graphics.dispose();
        });
        if(!returned.get()) throw new AssertionError("Return action did not fire");

        Thread.sleep(450);
        SwingUtilities.invokeAndWait(() -> {
            if(!pencilRef[0].getBounds().equals(portraitBounds[0]))
                throw new AssertionError("Stationery moved on hover");
            pencilRef[0].setActive(true);
            if(!pencilRef[0].isActive() || !pencilRef[0].getBounds().equals(portraitBounds[0]))
                throw new AssertionError("Selection changed stationery position");
            dispatch(pencilRef[0],MouseEvent.MOUSE_EXITED);
        });
        Thread.sleep(450);
        SwingUtilities.invokeAndWait(() -> {
            if(!pencilRef[0].getBounds().equals(portraitBounds[0]))
                throw new AssertionError("Stationery moved after hover");
            ReadingPanel panel=panelRef[0];
            panel.setExpandedMode(true);
            panel.setSize(1024,720);
            panel.doLayout();
            Component scene=panel.getComponent(0);
            scene.setSize(1024,720);
            scene.doLayout();
            if(!panel.isExpandedMode() || panel.stationeryButtons().get(0).getBounds().equals(portraitBounds[0]))
                throw new AssertionError("Expanded layout did not reposition the scene");
            BufferedImage rendered=new BufferedImage(1024,720,BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics=rendered.createGraphics();
            panel.paint(graphics);
            graphics.dispose();
        });
        System.out.println("Reading flow smoke test passed");
    }

    private static JButton findButton(BookSelectionPopup popup,String accessibleName) {
        for(Component component:popup.getComponents()) {
            if(component instanceof JButton button
                    && accessibleName.equals(button.getAccessibleContext().getAccessibleName())) return button;
        }
        return null;
    }

    private static JButton findButton(Container parent,String accessibleName) {
        for(Component component:parent.getComponents()) {
            if(component instanceof JButton button
                    && accessibleName.equals(button.getAccessibleContext().getAccessibleName())) return button;
            if(component instanceof Container child) {
                JButton found=findButton(child,accessibleName);
                if(found!=null) return found;
            }
        }
        return null;
    }

    private static void dispatch(Component component,int id) {
        MouseEvent event=new MouseEvent(component,id,System.currentTimeMillis(),0,
                component.getWidth()/2,component.getHeight()/2,0,false);
        for(MouseListener listener:component.getMouseListeners()) {
            if(id==MouseEvent.MOUSE_ENTERED) listener.mouseEntered(event);
            else listener.mouseExited(event);
        }
    }

}