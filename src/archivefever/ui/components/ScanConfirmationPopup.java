package archivefever.ui.components;

import archivefever.ui.theme.AppFonts;
import javax.swing.*;
import java.awt.*;

/** Recognition result intentionally empty until the backend supplies a book. */
public final class ScanConfirmationPopup extends PaperPopup {
    private final JButton yes;
    private final JPanel resultArea=new JPanel(null);

    public ScanConfirmationPopup(Runnable cancel,Runnable confirm){
        super(new Rectangle(51,208,311,367),cancel);
        label("Is this the book",51,235,311,34,24,true);
        label("you're looking for?",51,259,311,34,24,true);
        resultArea.setOpaque(false);resultArea.setBounds(85,313,239,168);
        resultArea.getAccessibleContext().setAccessibleName("도서 조회 결과 영역");add(resultArea);
        JButton no=paperAction("no.","스캔 다시 하기",129,518,40,36,cancel);
        no.setFont(AppFonts.headingRegular(24));
        yes=paperAction("yes.","스캔한 도서 추가",245,518,42,36,confirm);
        yes.setFont(AppFonts.headingRegular(24));
        resetResult();
    }
    public void resetResult(){
        resultArea.removeAll();setConfirmationAvailable(false);resultArea.revalidate();resultArea.repaint();
    }
    public JPanel resultArea(){return resultArea;}
    public void setConfirmationAvailable(boolean available){
        yes.setEnabled(available);
        yes.setToolTipText(available?null:"조회된 도서가 있어야 추가할 수 있어요");
    }
}
