package archivefever.ui.screens;

import archivefever.ui.components.LinkLabel;
import archivefever.ui.components.SceneImages;
import archivefever.ui.theme.*;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.function.Consumer;

/**
 * Find your book: the way to add a book without its barcode. Words of a title or an author
 * are typed in, the catalogue's matches are listed seven to a page, and pressing one hands
 * it on to be looked at and confirmed. Laid out like the friend directory.
 */
public final class BookSearchPanel extends SocialPanel {
    /** A book a search found. @param onShelf whether the reader has it already */
    public record Found(String isbn13,String title,String author,String publisher,boolean onShelf) {}

    /** Where books are looked for. Blocking: it is called off the event thread, and its failure's message is shown as it is. */
    public interface Source {List<Found> search(String words) throws Exception;}

    private static final int PAGE_SIZE=7, ROW=72, LIST_TOP=190, TEXT_WIDTH=330, TEXT_WIDTH_BESIDE_PILL=246;
    private static final BufferedImage WALLPAPER=AppTheme.image("images/addbook/wallpaper.png");
    private static final BufferedImage PAPER=AppTheme.image("images/main/book-popup-paper.png");
    private static final Shape SEARCH_ICON=SceneImages.svgPath("images/social/search.svg");
    private final Consumer<Found> onChosen;
    private final JPanel rows=new JPanel(null);
    private final JTextField search=new JTextField();
    private final JButton previous=link("prev","이전 검색 결과",40,702,52,28,typewriter(20),AppColors.LIGHT_BROWN);
    private final JButton next=link("next","다음 검색 결과",112,702,52,28,typewriter(20),AppColors.LIGHT_BROWN);
    private Source source=words->List.of();
    private List<Found> results=List.of();
    private int page;
    /** Titles and bylines as they are drawn, cut to fit: measuring is slow, and a list is painted many times. */
    private final java.util.Map<String,String> fitted=new java.util.HashMap<>();

    /**
     * @param onBack leaves the search
     * @param onChosen given the book the reader pressed, to show and confirm it
     * @param onCredit opens the catalogue the results come from, which asks to be named and linked
     */
    public BookSearchPanel(Runnable onBack,Consumer<Found> onChosen,Runnable onCredit) {
        super("book search");
        this.onChosen=onChosen;
        search.setBounds(71,154,302,28);search.setFont(typewriter(20));search.setForeground(AppColors.GRAY);
        search.setCaretColor(AppColors.GRAY);search.setOpaque(false);search.setBorder(null);
        search.getAccessibleContext().setAccessibleName("도서 검색어");
        // Looked for when asked, not while typing: every search is a request to the catalogue.
        search.addActionListener(e->runSearch());
        search.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) {typed();}
            public void removeUpdate(javax.swing.event.DocumentEvent e) {typed();}
            public void changedUpdate(javax.swing.event.DocumentEvent e) {typed();}
        });
        JButton searchButton=hit("도서 검색 실행",32,150,34,34,this::runSearch);
        LinkLabel credit=new LinkLabel("book data: YES24","검색 결과 출처 YES24",typewriter(14),AppColors.LIGHT_BROWN,false,onCredit);
        credit.setBounds(39,118,180,20);
        previous.addActionListener(e->showPage(page-1));
        next.addActionListener(e->showPage(page+1));
        rows.setOpaque(false);rows.setBounds(0,0,412,917);
        for(JComponent component:new JComponent[]{search,searchButton,credit,previous,next,rows})add(component);
        setBackAction(onBack);
        showPage(0);
    }

    /** Where books are looked for from now on. */
    public void setSource(Source source) {this.source=source;reset();}

    /** Called when the screen comes into view: empty, with the field ready to type in. */
    public void open() {reset();search.requestFocusInWindow();}

    public void reset() {
        cancelRequests();search.setText("");results=List.of();
        showPage(0);
    }

    public boolean isBusyForTest() {return isBusy();}

    /** The typewriter face has no Korean letters, and most titles are Korean. */
    private void typed() {
        search.setFont(korean(search.getText())?AppFonts.body(18):typewriter(20));
        repaint();
    }

    private void runSearch() {
        String words=search.getText().strip();
        results=List.of();showPage(0);
        if(words.isEmpty()) {cancelRequests();return;}
        Source asked=source;
        request("searching...",()->asked.search(words),found->{
            results=found;showPage(0);
            if(found.isEmpty())setMessage("no results");
        });
    }

    private void showPage(int requested) {
        rows.removeAll();fitted.clear();
        int pages=Math.max(1,(results.size()+PAGE_SIZE-1)/PAGE_SIZE);
        page=Math.max(0,Math.min(requested,pages-1));
        previous.setVisible(pages>1);next.setVisible(pages>1);
        previous.setEnabled(page>0);next.setEnabled(page<pages-1);
        // Nothing listed: notices go where the list would begin. With a list, on a slip under it.
        setStatusArea(results.isEmpty()?230:696,!results.isEmpty());
        for(int i=page*PAGE_SIZE;i<Math.min((page+1)*PAGE_SIZE,results.size());i++) {
            Found found=results.get(i);
            // A book already on the shelf is shown, and not offered again.
            if(found.onShelf())continue;
            int y=LIST_TOP+(i-page*PAGE_SIZE)*ROW;
            rows.add(hit(found.title()+" 고르기",26,y+1,360,ROW-2,()->{if(!isBusy())onChosen.accept(found);}));
        }
        rows.revalidate();repaint();
    }

    @Override void paintScene(Graphics2D g) {
        SceneImages.cover(g,WALLPAPER,-175,-108,736,1308);
        g.setColor(Color.WHITE);g.fillRect(14,44,385,700);
        Graphics2D paper=(Graphics2D)g.create();
        paper.setComposite(AlphaComposite.SrcOver.derive(.8f));
        SceneImages.cover(paper,PAPER,14,44,385,700);paper.dispose();
        drawText(g,"Find your book",typewriter(36),AppColors.LIGHT_BROWN,49,69,false);
        rule(g,116);rule(g,184);
        Graphics2D icon=(Graphics2D)g.create();icon.translate(38,158);icon.setColor(AppColors.GRAY);
        icon.setStroke(new BasicStroke(2,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));icon.draw(SEARCH_ICON);icon.dispose();
        if(search.getText().isEmpty())drawText(g,"Search title or author",typewriter(15),AppColors.LIGHT_BROWN,71,161,false);
        for(int i=page*PAGE_SIZE;i<Math.min((page+1)*PAGE_SIZE,results.size());i++)
            paintBook(g,results.get(i),LIST_TOP+(i-page*PAGE_SIZE)*ROW);
    }

    private void rule(Graphics2D g,int y) {g.setColor(AppColors.LIGHT_BROWN);g.fillRect(38,y-1,335,1);}

    private void paintBook(Graphics2D g,Found found,int y) {
        float width=found.onShelf()?TEXT_WIDTH_BESIDE_PILL:TEXT_WIDTH;
        Font titleFont=writable(found.title(),korean(found.title())?AppFonts.bodyBold(16):AppFonts.headingRegular(22),Font.BOLD,15);
        if(!found.title().isBlank())drawText(g,shortened(g,found.title(),titleFont,width),titleFont,AppColors.GRAY,40,y+14,false);
        String by=found.author()==null || found.author().isBlank()?found.publisher()
                :found.publisher()==null || found.publisher().isBlank()?found.author():found.author()+" · "+found.publisher();
        if(by!=null && !by.isBlank()) {
            Font byFont=writable(by,AppFonts.body(12),Font.PLAIN,12);
            drawText(g,shortened(g,by,byFont,width),byFont,AppColors.LIGHT_BROWN,40,y+42,false);
        }
        if(found.onShelf()) {
            g.setColor(Color.WHITE);g.fillRoundRect(295,y+21,74,29,24,24);
            g.setColor(AppColors.GRAY);g.setStroke(new BasicStroke(1f));g.drawRoundRect(295,y+21,73,28,24,24);
            Font pill=typewriter(20);
            drawText(g,"ADDED",pill,AppColors.GRAY,295+(74-textWidth(g,"ADDED",pill))/2,y+25,false);
        }
        rule(g,y+ROW);
    }

    /**
     * The font, if it has every letter of the text; otherwise the system's own, which knows
     * more scripts than the faces the app brings along. A search finds Chinese and Japanese
     * editions too.
     */
    private static Font writable(String text,Font font,int style,int size) {
        return font.canDisplayUpTo(text)<0?font:new Font(Font.SANS_SERIF,style,size);
    }

    /** The text, or as much of it as fits the width with "…" after it. */
    private String shortened(Graphics2D g,String text,Font font,float width) {
        return fitted.computeIfAbsent(font.getSize2D()+"/"+width+"/"+text,key->{
            if(textWidth(g,text,font)<=width)return text;
            int end=text.length();
            while(end>1 && textWidth(g,text.substring(0,end).stripTrailing()+"…",font)>width)end--;
            return text.substring(0,end).stripTrailing()+"…";
        });
    }
}
