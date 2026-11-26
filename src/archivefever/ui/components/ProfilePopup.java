package archivefever.ui.components;

import archivefever.ui.theme.AppFonts;
import java.awt.Rectangle;

/** Shows who is signed in; the rest of the profile follows once it is connected. */
public final class ProfilePopup extends PaperPopup {
    private final javax.swing.JLabel user,handle;
    public ProfilePopup(Runnable dismiss,Runnable logout){
        super(new Rectangle(51,267,311,384),dismiss);
        label("profile",51,273,311,34,24,true);separator(300,true);
        user=label("",51,322,311,30,20,true);
        user.getAccessibleContext().setAccessibleName("로그인한 사용자");
        handle=label("",51,354,311,24,15,true);
        handle.getAccessibleContext().setAccessibleName("내 아이디");
        paperAction("logout","로그아웃",166,602,84,35,logout);
    }
    /** @param name the name the signed-in person goes by; empty when there are no accounts */
    public void setUser(String name){setUser(name,"");}
    /**
     * @param name the name the signed-in person goes by; empty when there are no accounts
     * @param handle the name friends find them by, with the "@"; empty when there are no accounts
     */
    public void setUser(String name,String handle){
        String shown=name==null?"":name;
        // The typewriter face has no Korean letters; a Korean name is set in the body face.
        boolean korean=shown.codePoints().anyMatch(c->Character.UnicodeScript.of(c)==Character.UnicodeScript.HANGUL);
        user.setFont(korean?AppFonts.body(18):AppFonts.typewriter(20));
        user.setText(shown);
        this.handle.setText(handle==null?"":handle);
    }
}
