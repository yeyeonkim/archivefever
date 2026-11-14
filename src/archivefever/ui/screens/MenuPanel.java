package archivefever.ui.screens;

import archivefever.ui.components.*;
import archivefever.ui.theme.*;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.List;

public final class MenuPanel extends JPanel {
    private final BufferedImage wallpaper=AppTheme.image("images/menu/wallpaper.png");
    private final BufferedImage caseImage=AppTheme.image("images/menu/case.png");
    private final List<MenuObjectButton> objects;

    public MenuPanel(Runnable notifications,Runnable profile){this(notifications,profile,()->{});}
    public MenuPanel(Runnable notifications,Runnable profile,Runnable edit){
        setLayout(null);setBackground(Color.WHITE);
        objects=List.of(
                new MenuObjectButton("notification","bell",9,368,141.049,141.049,-35.73,186,470,false,notifications),
                new MenuObjectButton("edit","scissors",54,181,238.839,238.839,-121.9,96,397,false,edit),
                new MenuObjectButton("profile","id-card",160,510,177.506,113.061,18.12,100,587,true,profile));
        objects.forEach(button->add(button,0));
        JLabel title=new JLabel("menu",SwingConstants.CENTER);title.setFont(AppFonts.heading(36));
        title.setForeground(AppColors.BROWN);title.setBounds(30,25,352,45);add(title,0);
    }
    public void resetHover(){objects.forEach(MenuObjectButton::resetHover);}
    @Override protected void paintComponent(Graphics graphics){
        super.paintComponent(graphics);Graphics2D g=(Graphics2D)graphics.create();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        SceneImages.cover(g,wallpaper,-37,0,592,917);
        g.translate(-313.77+1355.873/2,12+865.358/2);g.rotate(Math.toRadians(83.26));
        SceneImages.cover(g,caseImage,-360,-640,720,1280);g.dispose();
    }
}
