package archivefever.ui.screens;

import archivefever.ui.components.SceneImages;
import archivefever.ui.theme.*;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

/** Visual camera shell. No device access, preview, recognition or network request. */
public class CameraScanPanel extends JPanel {
    private final BufferedImage wallpaper=AppTheme.image("images/addbook/wallpaper.png");
    private final BufferedImage camera=AppTheme.image("images/addbook/camera-frame.png");
    private final JLabel title=new JLabel("",SwingConstants.CENTER);
    private final JButton primaryAction;

    protected CameraScanPanel(String heading,String accessibleName,Runnable onScan){
        setLayout(null);setBackground(Color.BLACK);
        title.setText(heading);
        title.setBounds(20,25,372,45);title.setFont(AppFonts.heading(36));title.setForeground(AppColors.BROWN);add(title);
        JButton scan=new JButton("scan."){
            @Override protected void paintComponent(Graphics graphics){
                Graphics2D g=(Graphics2D)graphics.create();
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
                g.setFont(getFont());FontMetrics fm=g.getFontMetrics();
                int x=(getWidth()-fm.stringWidth(getText()))/2;
                int baseline=(getHeight()-fm.getHeight())/2+fm.getAscent();
                g.setColor(isEnabled()?Color.BLACK:new Color(0x666666));g.drawString(getText(),x,baseline);
                g.setStroke(new BasicStroke(.65f));g.drawLine(x,baseline+2,x+fm.stringWidth(getText()),baseline+2);
                if(hasFocus())g.drawRect(1,1,getWidth()-3,getHeight()-3);
                g.dispose();
            }
        };
        primaryAction=scan;scan.setFont(AppFonts.headingRegular(30));
        scan.setBounds(150,716,96,48);scan.setOpaque(false);scan.setBorder(null);
        scan.setContentAreaFilled(false);scan.setFocusPainted(false);
        scan.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        scan.getAccessibleContext().setAccessibleName(accessibleName);
        scan.addActionListener(e->onScan.run());add(scan);
    }
    protected void setHeading(String heading,Color color){title.setText(heading);title.setForeground(color);}
    protected void configureAction(String text,String name,Font font,boolean enabled){
        primaryAction.setText(text);primaryAction.setFont(font);primaryAction.setEnabled(enabled);
        primaryAction.getAccessibleContext().setAccessibleName(name);
    }
    protected void setActionBounds(int x,int y,int width,int height){primaryAction.setBounds(x,y,width,height);}
    protected void setActionHint(String hint){primaryAction.setToolTipText(hint);}
    @Override protected void paintComponent(Graphics graphics){
        super.paintComponent(graphics);Graphics2D g=(Graphics2D)graphics.create();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        SceneImages.cover(g,wallpaper,-175,-108,736,1308);
        // Blank camera preview instead of the reference design's example book photo.
        g.setColor(Color.BLACK);g.fillRect(29,138,347,501);
        g.drawImage(camera,-197,80,806,650,null);g.dispose();
    }
}
