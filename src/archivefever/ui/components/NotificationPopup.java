package archivefever.ui.components;

import javax.swing.*;
import java.awt.*;

/** Demo notifications; confirmation callbacks can later be connected to the backend. */
public final class NotificationPopup extends PaperPopup {
    private final JButton friendConfirm,swapConfirm;
    private final Runnable confirmSound;
    public NotificationPopup(Runnable dismiss){this(dismiss,()->{});}
    public NotificationPopup(Runnable dismiss,Runnable confirmSound){
        super(new Rectangle(51,267,311,384),dismiss);
        this.confirmSound=java.util.Objects.requireNonNull(confirmSound);
        label("notification",51,273,311,34,24,true);
        separator(300,true);separator(382,false);separator(460,false);separator(549,false);
        inlineLabel("@yeyeon"," sent you a friend request.",66,316,288,26);
        inlineLabel("@gwanjun"," smashed your archive!",66,400,288,26);
        label("@yeyeon sent you a Book swap request.",66,483,288,26,14,false);
        friendConfirm=paperAction("Confirm","친구 요청 확인",256,350,96,35,()->confirm(true));
        swapConfirm=paperAction("Confirm","도서 교환 요청 확인",256,517,96,35,()->confirm(false));
    }
    private void confirm(boolean friend){
        JButton button=friend?friendConfirm:swapConfirm;
        if(!button.isEnabled())return;
        confirmSound.run();
        button.setText("Confirmed");button.setEnabled(false);
    }
    public void resetSession(){
        for(JButton button:new JButton[]{friendConfirm,swapConfirm}){button.setText("Confirm");button.setEnabled(true);}
    }
}
