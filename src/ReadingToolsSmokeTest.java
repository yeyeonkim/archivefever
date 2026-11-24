import archivefever.model.*;
import archivefever.ui.components.ReadingText;
import archivefever.ui.screens.ReadingPanel;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import javax.imageio.ImageIO;
import java.io.File;

public class ReadingToolsSmokeTest {
    private static void layout(Container root){root.doLayout();for(Component c:root.getComponents())if(c instanceof Container child)layout(child);}
    private static void click(ReadingText text,int x,int y){
        text.dispatchEvent(new MouseEvent(text,MouseEvent.MOUSE_CLICKED,System.currentTimeMillis(),0,x,y,1,false));
    }
    private static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception {
        System.setProperty("archivefever.notes.dir",Files.createTempDirectory("archivefever-notes-test-").toString());
        SwingUtilities.invokeAndWait(()->{
            try {
                BookSpine book=new BookSpine("annotations-test","죽은 신을 위하여",new BufferedImage(1,1,2),1);
                ReadingPanel panel=new ReadingPanel(book,()->{});panel.setSize(412,917);layout(panel);
                ReadingText text=panel.readingText();ReadingDocument doc=text.document();
                require(doc.sentences().size()==2,"Korean sentence segmentation");
                // Clicking the text with no selected tool must leave it untouched.
                click(text,10,6);require(!doc.mark(0).underlined(),"No implicit annotation");
                panel.stationeryButtons().get(0).doClick(0);click(text,10,6);
                require(doc.mark(0).underlined() && !doc.mark(1).underlined(),"Underline only selected sentence");
                panel.stationeryButtons().get(2).doClick(0);click(text,10,34);
                require(!doc.mark(0).highlighted() && doc.mark(1).highlighted(),"Highlight only second sentence");
                click(text,10,6);require(doc.mark(0).highlighted() && doc.mark(0).underlined(),"Combined annotations");
                panel.stationeryButtons().get(3).doClick(0);click(text,10,6);
                require(doc.mark(0).memo() && text.memoEditor().isVisible(),"Sentence memo editor");
                text.memoEditor().setText("첫 번째 문장에 대한 메모");
                text.memoEditor().getActionMap().get("save").actionPerformed(new ActionEvent(text,0,"save"));
                require(!text.memoEditor().isVisible() && doc.mark(0).note().equals("첫 번째 문장에 대한 메모"),"Enter saves memo");
                ReadingDocument reopened=new ReadingDocument(book.id(),doc.text());
                require(reopened.mark(0).equals(doc.mark(0)) && reopened.mark(1).equals(doc.mark(1)),"Persist after reopening book");
                require(!new ReadingDocument("different-book",doc.text()).mark(0).underlined(),"Book isolation");
                click(text,10,34);text.memoEditor().setText("두 번째 문장 메모");text.saveMemo();
                require(doc.mark(0).note().equals("첫 번째 문장에 대한 메모") && doc.mark(1).note().equals("두 번째 문장 메모"),"Separate sentence notes");
                panel.stationeryButtons().get(0).doClick(0);click(text,10,6);
                require(!doc.mark(0).underlined() && doc.mark(0).highlighted(),"Underline toggle preserves highlight");
                click(text,10,6);require(doc.mark(0).underlined(),"Underline toggles back on");
                panel.stationeryButtons().get(2).doClick(0);click(text,10,34);
                require(!doc.mark(1).highlighted() && doc.mark(0).underlined(),"Highlight toggles off independently");
                click(text,10,34);require(doc.mark(1).highlighted(),"Highlight toggles back on");
                click(text,10,60);
                require(text.memoEditor().isVisible() && text.memoEditor().getText().equals("첫 번째 문장에 대한 메모"),"Click saved memo to edit");
                text.memoEditor().setText("수정된 메모");text.saveMemo();
                require(new ReadingDocument(book.id(),doc.text()).mark(0).note().equals("수정된 메모"),"Edited memo persists");
                click(text,10,60);text.memoEditor().setText("첫 번째 문장에 대한 메모");text.saveMemo();
                text.deleteMemo(1);
                require(!doc.mark(1).memo() && doc.mark(1).highlighted() && doc.mark(0).memo(),"Delete only chosen memo");
                require(!new ReadingDocument(book.id(),doc.text()).mark(1).memo(),"Deletion persists");
                panel.stationeryButtons().get(3).doClick(0);click(text,10,34);
                text.memoEditor().setText("임시 메모");text.saveMemo();
                click(text,10,34);text.memoEditor().setText("   ");text.saveMemo();
                require(!doc.mark(1).memo(),"Empty Enter deletes saved memo");
                click(text,10,34);text.memoEditor().setText("");text.saveMemo();
                require(!doc.mark(1).memo(),"Empty Enter also removes newly opened empty memo");
                BufferedImage headerBefore=new BufferedImage(412,917,2);
                Graphics2D headerGraphics=headerBefore.createGraphics();panel.paint(headerGraphics);headerGraphics.dispose();
                int before=text.memoEditor().getFont().getSize();
                panel.stationeryButtons().get(1).doClick(0);
                require(text.zoomStep()==1 && text.memoEditor().getFont().getSize()>before,"Zoom body and memo");
                BufferedImage headerAfter=new BufferedImage(412,917,2);
                Graphics2D afterGraphics=headerAfter.createGraphics();panel.paint(afterGraphics);afterGraphics.dispose();
                for(int y=45;y<100;y++)for(int x=220;x<412;x++)
                    require(headerBefore.getRGB(x,y)==headerAfter.getRGB(x,y),"Header must not zoom");
                panel.stationeryButtons().get(1).doClick(0);require(text.zoomStep()==2,"Smaller step");
                panel.stationeryButtons().get(1).doClick(0);require(text.zoomStep()==0,"Restore normal size");
                panel.setExpandedMode(true);panel.setSize(1024,720);layout(panel);
                require(doc.mark(0).underlined() && doc.mark(1).highlighted(),"Annotations survive layout change");
                panel.beginTransition(false);panel.setTransitionProgress(.5f);panel.finishTransition();
                panel.setSize(412,917);layout(panel);
                require(doc.mark(0).note().equals("첫 번째 문장에 대한 메모"),"Memo survives animated snap");
                // Verify inverse scaled hit testing rather than applying to a stale text position.
                panel.setSize(330,734);layout(panel);
                panel.stationeryButtons().get(0).doClick(0);click(text,8,27);
                require(doc.mark(1).underlined(),"Scaled sentence click");
                panel.setSize(412,917);layout(panel);
                BufferedImage image=new BufferedImage(412,917,2);Graphics2D g=image.createGraphics();panel.paint(g);g.dispose();
                ImageIO.write(image,"png",new File(System.getProperty("java.io.tmpdir"),"archivefever-reading-tools.png"));
                System.out.println("PASS: sentence clicks, combined marks, Enter save, persistence, book isolation, zoom cycle, scaled clicks, animated snap");
            }catch(Exception e){throw new RuntimeException(e);}
        });
    }
}
