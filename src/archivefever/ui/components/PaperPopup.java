package archivefever.ui.components;

import archivefever.ui.theme.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;

/** Shared paper and full-screen scrim used by book, scan, notification and profile popups. */
public class PaperPopup extends JPanel {
    private static final BufferedImage PAPER=AppTheme.image("images/main/book-popup-paper.png");
    private final Rectangle paperBounds;

    protected PaperPopup(Rectangle bounds,Runnable onDismiss) {
        paperBounds=new Rectangle(bounds);
        setOpaque(false);setLayout(null);setFocusable(true);
        addMouseListener(new MouseAdapter(){
            @Override public void mouseClicked(MouseEvent e){if(!paperBounds.contains(e.getPoint()))onDismiss.run();}
        });
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE,0),"dismiss");
        getActionMap().put("dismiss",new AbstractAction(){
            @Override public void actionPerformed(ActionEvent e){onDismiss.run();}
        });
    }

    protected JLabel label(String text,int x,int y,int width,int height,float size,boolean centered) {
        JLabel label=new JLabel(text,centered?SwingConstants.CENTER:SwingConstants.LEFT);
        label.setFont(AppFonts.typewriter(size));label.setForeground(Color.BLACK);
        label.setBounds(x,y,width,height);add(label);return label;
    }

    protected JButton paperAction(String text,String name,int x,int y,int width,int height,Runnable action) {
        JButton button=new JButton(text){
            @Override protected void paintComponent(Graphics graphics){
                Graphics2D g=(Graphics2D)graphics.create();
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                Font font=getFont();FontMetrics fm=g.getFontMetrics(font);
                if(fm.stringWidth(getText())>getWidth())font=font.deriveFont(font.getSize2D()*getWidth()/fm.stringWidth(getText()));
                g.setFont(font);fm=g.getFontMetrics();
                int baseline=(getHeight()-fm.getHeight())/2+fm.getAscent();
                g.setColor(isEnabled()?Color.BLACK:new Color(0x666666));
                g.drawString(getText(),0,baseline);
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
                g.setStroke(new BasicStroke(.65f));
                g.drawLine(0,baseline+2,Math.min(getWidth()-1,fm.stringWidth(getText())),baseline+2);
                if(hasFocus())g.drawRect(0,0,getWidth()-1,getHeight()-1);
                g.dispose();
            }
        };
        button.setBounds(x,y,width,height);button.setFont(AppFonts.typewriter(24));
        button.setBorder(null);button.setOpaque(false);button.setContentAreaFilled(false);button.setFocusPainted(false);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.getAccessibleContext().setAccessibleName(name);
        button.addActionListener(e->action.run());add(button);return button;
    }

    protected void inlineLabel(String username,String message,int x,int y,int width,int height){
        JLabel label=new JLabel(username+message){
            @Override protected void paintComponent(Graphics graphics){
                Graphics2D g=(Graphics2D)graphics.create();
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g.setFont(getFont());g.setColor(Color.BLACK);
                FontMetrics fm=g.getFontMetrics();int baseline=(getHeight()-fm.getHeight())/2+fm.getAscent();
                g.drawString(getText(),0,baseline);
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
                g.setStroke(new BasicStroke(.65f));g.drawLine(0,baseline+1,fm.stringWidth(username),baseline+1);g.dispose();
            }
        };
        label.setFont(AppFonts.typewriter(15));label.setBounds(x,y,width,height);add(label);
    }

    protected void separator(int y,boolean spaced) {
        JPanel separator=new JPanel(){
            @Override protected void paintComponent(Graphics graphics){
                Graphics2D g=(Graphics2D)graphics.create();
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g.setFont(AppFonts.typewriter(15));g.setColor(Color.BLACK);
                g.drawString((spaced?"- ":"-").repeat(60),0,14);g.dispose();
            }
        };
        separator.setOpaque(false);separator.setBounds(58,y,296,20);add(separator);
    }

    @Override protected void paintComponent(Graphics graphics){
        Graphics2D g=(Graphics2D)graphics.create();
        g.setColor(new Color(0,0,0,179));g.fillRect(0,0,getWidth(),getHeight());
        g.setColor(Color.WHITE);g.fill(paperBounds);
        g.clip(paperBounds);g.setComposite(AlphaComposite.SrcOver.derive(.8f));
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        SceneImages.cover(g,PAPER,paperBounds.x,paperBounds.y,paperBounds.width,paperBounds.height);
        g.dispose();
    }
}
