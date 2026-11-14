package archivefever.ui.screens;

import archivefever.model.BookSpine;
import archivefever.ui.theme.AppFonts;
import javax.swing.*;
import java.awt.event.*;

/** Capture/review UI only; no OCR, fake recognized sentences or persistence. */
public final class SentenceScanPanel extends CameraScanPanel {
    private BookSpine book;
    private boolean captured;
    public SentenceScanPanel(Runnable capture,Runnable returnToArchive){
        super("Archive sentence","문장 촬영",capture);
        setActionBounds(159,716,96,48);
        configureAction("scan.","문장 촬영",AppFonts.typewriter(30),true);
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE,0),"return");
        getActionMap().put("return",new AbstractAction(){
            @Override public void actionPerformed(ActionEvent e){returnToArchive.run();}
        });
    }
    public void setBook(BookSpine book){reset();this.book=java.util.Objects.requireNonNull(book);}
    public BookSpine selectedBook(){return book;}
    public boolean isCaptured(){return captured;}
    public void showCaptured(){
        captured=true;
        configureAction("save.","문장 저장",AppFonts.typewriter(30),false);
        setActionHint("인식된 문장이 있어야 저장할 수 있어요");
    }
    public void reset(){
        book=null;captured=false;
        configureAction("scan.","문장 촬영",AppFonts.typewriter(30),true);setActionHint(null);
    }
}
