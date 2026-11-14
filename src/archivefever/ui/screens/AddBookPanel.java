package archivefever.ui.screens;

import archivefever.ui.theme.AppColors;
import java.awt.Color;

/** Book registration uses the shared blank camera shell. */
public final class AddBookPanel extends CameraScanPanel {
    public AddBookPanel(Runnable onScan){super("Add book","도서 스캔",onScan);}
    public void setReviewing(boolean reviewing){
        // Header text from the supplied mockup, not a connected user identity.
        setHeading(reviewing?"Sujin’s book Archive":"Add book",reviewing?Color.BLACK:AppColors.BROWN);
    }
}
