package archivefever.ui.components;

import javax.swing.*;
import java.awt.*;

/** Underlined text that acts as a link, drawn like the app's other paper actions. */
public final class LinkLabel extends JButton {
    private final boolean alignRight;

    public LinkLabel(String text,String accessibleName,Font font,Color color,boolean alignRight,Runnable action) {
        super(text);
        this.alignRight=alignRight;
        setFont(font);setForeground(color);
        setBorder(null);setOpaque(false);setContentAreaFilled(false);setFocusPainted(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        getAccessibleContext().setAccessibleName(accessibleName);
        addActionListener(e->action.run());
    }

    @Override protected void paintComponent(Graphics graphics) {
        Graphics2D g=(Graphics2D)graphics.create();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
        g.setFont(getFont());g.setColor(getForeground());
        FontMetrics fm=g.getFontMetrics();
        int width=fm.stringWidth(getText());
        int x=alignRight?getWidth()-width-1:0;
        int baseline=(getHeight()-fm.getHeight())/2+fm.getAscent();
        g.drawString(getText(),x,baseline);
        g.setStroke(new BasicStroke(.65f));g.drawLine(x,baseline+2,x+width,baseline+2);
        if(hasFocus())g.drawRect(0,0,getWidth()-1,getHeight()-1);
        g.dispose();
    }
}
