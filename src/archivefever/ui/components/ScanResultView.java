package archivefever.ui.components;

import archivefever.ui.theme.AppColors;
import archivefever.ui.theme.AppFonts;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

/**
 * Fills the result area of {@link ScanConfirmationPopup}: a status line while the scan
 * is in progress, then the book that was found.
 */
public final class ScanResultView {
    private static final int COVER_WIDTH=100, COVER_HEIGHT=150, TEXT_X=112;
    private final JPanel area;

    /** @param area the popup's result area, laid out by absolute bounds */
    public ScanResultView(JPanel area) {this.area=area;}

    public void showMessage(String text) {
        area.removeAll();
        JLabel label=new JLabel(text,SwingConstants.CENTER);
        label.setFont(AppFonts.typewriter(18));label.setForeground(AppColors.GRAY);
        label.setBounds(0,0,area.getWidth(),area.getHeight());
        label.getAccessibleContext().setAccessibleName("도서 조회 상태");
        area.add(label);
        refresh();
    }

    /**
     * @param cover the book's cover, or null when it could not be loaded
     * @param openSource opens the book's YES24 page; YES24 requires this credit and link
     */
    public void showBook(String title,String author,BufferedImage cover,Runnable openSource) {
        area.removeAll();
        int textWidth=area.getWidth()-TEXT_X;
        if(cover!=null) {
            double scale=Math.min((double)COVER_WIDTH/cover.getWidth(),(double)COVER_HEIGHT/cover.getHeight());
            int width=Math.max(1,(int)Math.round(cover.getWidth()*scale));
            int height=Math.max(1,(int)Math.round(cover.getHeight()*scale));
            JLabel picture=new JLabel(new ImageIcon(cover.getScaledInstance(width,height,Image.SCALE_SMOOTH)));
            picture.setBounds((COVER_WIDTH-width)/2,(area.getHeight()-height)/2,width,height);
            picture.getAccessibleContext().setAccessibleName("표지");
            area.add(picture);
        }
        JTextArea titleText=new JTextArea(title);
        titleText.setFont(AppFonts.bodyBold(15));titleText.setForeground(Color.BLACK);
        titleText.setLineWrap(true);titleText.setWrapStyleWord(false);
        titleText.setEditable(false);titleText.setFocusable(false);titleText.setOpaque(false);titleText.setBorder(null);
        titleText.setBounds(TEXT_X,8,textWidth,84);
        titleText.getAccessibleContext().setAccessibleName("조회된 도서 제목");
        area.add(titleText);
        JLabel authorText=new JLabel(author==null?"":author);
        authorText.setFont(AppFonts.body(13));authorText.setForeground(AppColors.GRAY);
        authorText.setBounds(TEXT_X,96,textWidth,22);
        area.add(authorText);
        LinkLabel source=new LinkLabel("from YES24","YES24 상품 페이지 열기",AppFonts.typewriter(15),
                AppColors.LIGHT_BROWN,false,openSource);
        source.setBounds(TEXT_X,area.getHeight()-30,textWidth,24);
        area.add(source);
        refresh();
    }

    private void refresh() {
        area.revalidate();area.repaint();
        // The popup is painted scaled by its ancestors; repaint from the top so the change shows.
        Window window=SwingUtilities.getWindowAncestor(area);
        if(window!=null)window.repaint();
    }
}
