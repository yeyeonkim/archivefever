package archivefever.ui.components;

import javax.swing.*;
import java.awt.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;

/** Demo notifications; the friend request is answered through the given background calls. */
public final class NotificationPopup extends PaperPopup {
    private static final Rectangle FRIEND_CONFIRM=new Rectangle(256,350,96,35);
    private static final int RESULT_RIGHT=352;
    private final JButton friendConfirm,friendDecline,swapConfirm;
    private final JLabel friendError;
    private final Runnable confirmSound;
    private final Callable<?> acceptFriend,declineFriend;
    private boolean deciding;
    private int decision;
    public NotificationPopup(Runnable dismiss){this(dismiss,()->{});}
    public NotificationPopup(Runnable dismiss,Runnable confirmSound){this(dismiss,confirmSound,()->null,()->null);}
    public NotificationPopup(Runnable dismiss,Runnable confirmSound,Callable<?> acceptFriend,Callable<?> declineFriend){
        super(new Rectangle(51,267,311,384),dismiss);
        this.confirmSound=java.util.Objects.requireNonNull(confirmSound);
        this.acceptFriend=java.util.Objects.requireNonNull(acceptFriend);
        this.declineFriend=java.util.Objects.requireNonNull(declineFriend);
        label("notification",51,273,311,34,24,true);
        separator(300,true);separator(382,false);separator(460,false);separator(549,false);
        inlineLabel("@yeyeon"," sent you a friend request.",66,316,288,26);
        inlineLabel("@gwanjun"," smashed your archive!",66,400,288,26);
        label("@yeyeon sent you a Book swap request.",66,483,288,26,14,false);
        friendError=label("",66,350,76,35,15,false);friendError.setForeground(new Color(0x9B2D20));
        friendDecline=paperAction("Decline","친구 요청 거절",146,350,98,35,()->decideFriend(false));
        friendConfirm=paperAction("Confirm","친구 요청 확인",FRIEND_CONFIRM.x,FRIEND_CONFIRM.y,FRIEND_CONFIRM.width,FRIEND_CONFIRM.height,()->decideFriend(true));
        swapConfirm=paperAction("Confirm","도서 교환 요청 확인",256,517,96,35,this::confirmSwap);
    }
    private void confirmSwap(){
        if(!swapConfirm.isEnabled())return;
        confirmSound.run();
        swapConfirm.setText("Confirmed");swapConfirm.setEnabled(false);
    }
    private void decideFriend(boolean accept){
        if(deciding || !friendConfirm.isEnabled())return;
        if(accept)confirmSound.run();
        deciding=true;friendError.setText("");
        friendConfirm.setEnabled(false);friendDecline.setEnabled(false);
        int current=++decision;
        Callable<?> call=accept?acceptFriend:declineFriend;
        new SwingWorker<Void,Void>(){
            @Override protected Void doInBackground() throws Exception {call.call();return null;}
            @Override protected void done(){
                if(current!=decision)return;
                deciding=false;
                try {
                    get();showFriendResult(accept?"Confirmed":"Declined");
                } catch(ExecutionException e) {
                    showFriendButtons();friendError.setText("failed");
                } catch(InterruptedException e) {
                    Thread.currentThread().interrupt();showFriendButtons();friendError.setText("failed");
                }
                repaint();
            }
        }.execute();
    }
    /** Shows the saved answer when the popup opens, so it agrees with the friend search screen. */
    public void syncFriendRequest(String result){
        if(deciding)return;
        if(result==null)showFriendButtons();else showFriendResult(result);
    }
    private void showFriendButtons(){
        friendConfirm.putClientProperty(SETTLED,false);friendConfirm.setText("Confirm");
        friendConfirm.setBounds(FRIEND_CONFIRM);friendConfirm.setEnabled(true);
        friendDecline.setVisible(true);friendDecline.setEnabled(true);friendError.setText("");
    }
    private void showFriendResult(String result){
        friendDecline.setVisible(false);friendError.setText("");
        friendConfirm.putClientProperty(SETTLED,true);friendConfirm.setText(result);friendConfirm.setEnabled(false);
        int width=friendConfirm.getFontMetrics(friendConfirm.getFont()).stringWidth(result)+1;
        friendConfirm.setBounds(RESULT_RIGHT-width,FRIEND_CONFIRM.y,width,FRIEND_CONFIRM.height);
        repaint();
    }
    public void resetSession(){
        decision++;deciding=false;showFriendButtons();
        swapConfirm.setText("Confirm");swapConfirm.setEnabled(true);
    }
}
