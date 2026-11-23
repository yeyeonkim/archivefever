package archivefever.ui.components;

import archivefever.model.BookSpine;
import archivefever.ui.theme.AppTheme;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.function.Consumer;

/** Accepts already-loaded spine images; network I/O belongs outside the EDT. */
public final class Bookshelf extends JPanel {
    private final BufferedImage texture=AppTheme.image("images/main/bookshelf-texture.png");
    private final BufferedImage grain=AppTheme.image("images/main/shelf-grain.png");
    private final Consumer<BookSpine> onBookSelected;
    private List<BookSpine> books=List.of();
    private boolean tinted;

    /** Applies the darker wood layer of the Figma "Books for Swap" shelf, beneath the books. */
    public void setTinted(boolean tinted) {this.tinted=tinted;repaint();}

    public Bookshelf(List<BookSpine> books, Consumer<BookSpine> onBookSelected) {
        this.onBookSelected=onBookSelected;
        setOpaque(false);
        setLayout(null);
        setBooks(books);
    }

    public void setBooks(List<BookSpine> books) {
        if (!SwingUtilities.isEventDispatchThread())
            throw new IllegalStateException("Update Swing on the EDT");
        this.books=List.copyOf(books);
        removeAll();
        int row=1, x=10;
        // Fill the second shelf first, as in the design, then the remaining shelves.
        int[] bottoms={184,184,552,736};
        for (BookSpine book:this.books) {
            if (x+book.width()>369) { row++; x=10; }
            if (row>4) break;
            JLabel spine=new JLabel(new ImageIcon(book.image()
                    .getScaledInstance(book.width(),172,Image.SCALE_SMOOTH)));
            spine.setBounds(x,bottoms[row-1]-172,book.width(),172);
            spine.setToolTipText(book.title());
            spine.getAccessibleContext().setAccessibleName(book.title());
            spine.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            spine.addMouseListener(new java.awt.event.MouseAdapter() {
                @Override public void mouseClicked(java.awt.event.MouseEvent e) {
                    onBookSelected.accept(book);
                }
            });
            add(spine);
            x+=book.width()+1;
        }
        revalidate();
        repaint();
    }

    @Override protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g=(Graphics2D)graphics.create();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        for(int section=0;section<2;section++) {
            int y=section*368;
            Graphics2D frame=(Graphics2D)g.create();
            frame.clip(new java.awt.geom.RoundRectangle2D.Double(0,y,377,381,5,5));
            frame.drawImage(texture,-218,y-33,600,1200,null);
            for(int shelfY:new int[]{184,368}) {
                frame.setColor(Color.WHITE);
                frame.fillRect(7,y+shelfY,363,12);
                SceneImages.cover(frame,grain,7,y+shelfY,363,12);
            }
            frame.dispose();
        }
        for(int row=0;row<4;row++) {
            int y=row==0?9:196+(row-1)*184;
            int h=row==0?175:172;
            Graphics2D interior=(Graphics2D)g.create();
            interior.clipRect(8,y,361,h);
            interior.setComposite(AlphaComposite.SrcOver.derive(.36f));
            SceneImages.cover(interior,grain,8,y,361,h);
            interior.setComposite(AlphaComposite.SrcOver);
            interior.setPaint(new GradientPaint(8,y,new Color(0,0,0,78),88,y,new Color(0,0,0,0)));
            interior.fillRect(8,y,361,h);
            interior.setPaint(new GradientPaint(8,y,new Color(0,0,0,60),8,y+75,new Color(0,0,0,0)));
            interior.fillRect(8,y,361,h);
            interior.setPaint(new GradientPaint(8,y+h-28,new Color(0,0,0,0),8,y+h,new Color(0,0,0,28)));
            interior.fillRect(8,y+h-28,361,28);
            interior.dispose();
        }
        if(tinted) {g.setColor(new Color(0xB0,0x6C,0x14,58));g.fillRect(0,0,377,749);}
        g.dispose();
    }
}
