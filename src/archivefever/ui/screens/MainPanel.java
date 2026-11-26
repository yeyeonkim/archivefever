package archivefever.ui.screens;

import archivefever.model.BookSpine;
import archivefever.ui.components.Bookshelf;
import archivefever.ui.components.SceneImages;
import archivefever.ui.theme.*;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.function.Consumer;

public final class MainPanel extends JPanel {
    private final BufferedImage wallpaper=AppTheme.image("images/login-wallpaper.png");
    private final BufferedImage wood=AppTheme.image("images/login-wood.png");
    private final Bookshelf bookshelf;
    private final JLabel notice=new JLabel("",SwingConstants.CENTER);
    private final Timer noticeTimer=new Timer(3500,e->{notice.setVisible(false);repaint();});

    public MainPanel(List<BookSpine> books, Consumer<BookSpine> onBookSelected) {
        setLayout(null);setBackground(AppColors.WALL);
        JLabel title=new JLabel("My Book Archive",SwingConstants.CENTER);
        title.setFont(AppFonts.heading(36));title.setForeground(AppColors.BROWN);
        title.setBounds(30,25,352,45);add(title);
        bookshelf=new Bookshelf(books,onBookSelected);
        bookshelf.setBounds(17,72,377,749);add(bookshelf);
        notice.setFont(AppFonts.body(14));notice.setForeground(new Color(0x9B2D20));
        notice.setOpaque(true);notice.setBackground(new Color(0xF6F1E7));
        notice.setBorder(BorderFactory.createLineBorder(AppColors.LIGHT_BROWN));
        notice.getAccessibleContext().setAccessibleName("책장 안내");
        notice.setBounds(51,636,311,40);notice.setVisible(false);add(notice,0);
        noticeTimer.setRepeats(false);
    }

    public void setBooks(List<BookSpine> books) {bookshelf.setBooks(books);}

    public List<BookSpine> books() {return bookshelf.books();}

    /** Tells the reader something about the shelf for a few seconds, such as why a book could not be taken off it. */
    public void showNotice(String message) {
        notice.setText(message);notice.setVisible(true);noticeTimer.restart();repaint();
    }

    @Override protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g=(Graphics2D)graphics.create();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        for(int y:new int[]{0,244}) {
            Graphics2D wall=(Graphics2D)g.create();
            wall.clipRect(-29,y,450,y==0?263:278);
            wall.drawImage(wallpaper,-29,y,450,446,null);wall.dispose();
        }
        g.setColor(AppColors.NAVIGATION_BACKGROUND);g.fillRect(0,821,412,97);
        SceneImages.cover(g,wood,72,518,347,304);
        Graphics2D left=(Graphics2D)g.create();left.clipRect(-223,518,302,304);
        left.drawImage(wood,-223,518,347,304,null);left.dispose();
        g.dispose();
    }
}
