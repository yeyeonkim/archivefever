package archivefever.ui.components;

import archivefever.ui.theme.AppFonts;
import javax.swing.*;
import java.awt.*;

/** The choice paper used by the Figma "what's next?", "send request?" and "remove?" popups. */
public final class ChoicePopup extends PaperPopup {
    private static final int LEFT_CENTER=150, RIGHT_CENTER=264, ACTION_Y=439, ACTION_HEIGHT=32;
    private static final int FIRST_OF_THREE=113, SECOND_OF_THREE=207, THIRD_OF_THREE=300;
    private final Runnable onDismiss;
    private final JLabel title, note;
    private final JButton left, right, third;
    private Runnable leftAction, rightAction, thirdAction;

    public ChoicePopup(Runnable onDismiss) {
        super(new Rectangle(51,360,311,141),onDismiss);
        this.onDismiss=onDismiss;
        title=label("",51,389,311,42,24,true);
        // Under the choices: what choosing will do, when that needs saying. It may be Korean, which the title's face lacks.
        note=label("",51,474,311,18,12,true);
        note.setFont(AppFonts.body(12));note.setForeground(new Color(0x474747));
        left=paperAction("","",0,ACTION_Y,1,ACTION_HEIGHT,()->choose(leftAction));
        right=paperAction("","",0,ACTION_Y,1,ACTION_HEIGHT,()->choose(rightAction));
        third=paperAction("","",0,ACTION_Y,1,ACTION_HEIGHT,()->choose(thirdAction));
        left.setFont(AppFonts.headingRegular(24));right.setFont(AppFonts.headingRegular(24));
        third.setFont(AppFonts.headingRegular(24));
    }

    /** A question whose answer cannot be taken back: the note under the choices says what it will do. */
    public void ask(String question,String consequence,String leftText,String rightText,Runnable onLeft,Runnable onRight) {
        ask(question,leftText,rightText,onLeft,onRight);
        note.setText(consequence);
    }

    public void ask(String question,String leftText,String rightText,Runnable onLeft,Runnable onRight) {
        note.setText("");
        title.setText(question);leftAction=onLeft;rightAction=onRight;thirdAction=null;
        place(left,leftText,LEFT_CENTER);place(right,rightText,RIGHT_CENTER);
        third.setVisible(false);
        open();
    }

    public void ask(String question,String firstText,String secondText,String thirdText,
                    Runnable onFirst,Runnable onSecond,Runnable onThird) {
        note.setText("");
        title.setText(question);leftAction=onFirst;rightAction=onSecond;thirdAction=onThird;
        place(left,firstText,FIRST_OF_THREE);place(right,secondText,SECOND_OF_THREE);place(third,thirdText,THIRD_OF_THREE);
        third.setVisible(true);
        open();
    }

    private void open() {
        setVisible(true);requestFocusInWindow();
        if(getParent()!=null)getParent().repaint();
    }

    private void place(JButton button,String text,int center) {
        int width=button.getFontMetrics(button.getFont()).stringWidth(text)+2;
        button.setText(text);button.getAccessibleContext().setAccessibleName(text);
        button.setBounds(center-width/2,ACTION_Y,width,ACTION_HEIGHT);
    }

    private void choose(Runnable action) {
        onDismiss.run();
        if(action!=null)action.run();
    }
}
