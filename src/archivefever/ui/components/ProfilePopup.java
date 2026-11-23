package archivefever.ui.components;

import java.awt.Rectangle;

/** Shows who is signed in; the rest of the profile follows once it is connected. */
public final class ProfilePopup extends PaperPopup {
    private final javax.swing.JLabel user;
    public ProfilePopup(Runnable dismiss,Runnable logout){
        super(new Rectangle(51,267,311,384),dismiss);
        label("profile",51,273,311,34,24,true);separator(300,true);
        user=label("",51,322,311,30,20,true);
        user.getAccessibleContext().setAccessibleName("로그인한 사용자");
        paperAction("logout","로그아웃",166,602,84,35,logout);
    }
    /** @param name the name the signed-in person goes by; empty when there are no accounts */
    public void setUser(String name){user.setText(name==null?"":name);}
}
