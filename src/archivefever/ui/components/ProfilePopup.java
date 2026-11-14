package archivefever.ui.components;

import java.awt.Rectangle;

/** Empty profile content follows the supplied frame until user data is connected. */
public final class ProfilePopup extends PaperPopup {
    public ProfilePopup(Runnable dismiss,Runnable logout){
        super(new Rectangle(51,267,311,384),dismiss);
        label("profile",51,273,311,34,24,true);separator(300,true);
        paperAction("logout","로그아웃",166,602,84,35,logout);
    }
}
