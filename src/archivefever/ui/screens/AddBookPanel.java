package archivefever.ui.screens;

import archivefever.ui.theme.AppColors;
import java.awt.Color;

/** Book registration uses the shared blank camera shell: scan the barcode, or search by title instead. */
public final class AddBookPanel extends CameraScanPanel {
    public AddBookPanel(Runnable onScan){this(onScan,null);}
    /** @param onSearch opens the search by title; null leaves the barcode as the only way to add a book */
    public AddBookPanel(Runnable onScan,Runnable onSearch){
        super("Add book","도서 스캔",onScan);
        if(onSearch==null)return;
        // The other way, said smaller between the heading and the camera; "scan." stays where the design has it.
        javax.swing.JButton search=textAction("or search by title","도서 검색",onSearch);
        search.setFont(archivefever.ui.theme.AppFonts.headingRegular(21));
        search.setBounds(106,74,200,30);add(search);
    }
    public void setReviewing(boolean reviewing){
        // Header text from the supplied mockup, not a connected user identity.
        setHeading(reviewing?"Sujin’s book Archive":"Add book",reviewing?Color.BLACK:AppColors.BROWN);
    }
}
