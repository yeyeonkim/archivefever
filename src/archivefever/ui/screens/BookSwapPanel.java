package archivefever.ui.screens;

import archivefever.model.BookSwap;
import archivefever.model.SwapRecord;
import archivefever.social.Social;
import archivefever.ui.components.SceneImages;
import archivefever.ui.theme.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** The Figma Book swap cabinet: two books per page, swiped or chosen with the page dots. */
public final class BookSwapPanel extends SocialPanel {
    private static final int PAGE_SIZE=2, SCENE_WIDTH=412, SCENE_HEIGHT=917;
    private static final int DOT=8, DOT_GAP=10, DOT_TOP=741, MAX_DOTS=5, DRAG_START=6, SWIPE_DISTANCE=70;
    private static final Rectangle[] COVERS={new Rectangle(18,181,134,207),new Rectangle(13,440,143,207)};
    private static final int[] TEXT_TOP={299,554};
    private static final BufferedImage WALLPAPER=AppTheme.image("images/social/swap-wallpaper.png");
    private static final BufferedImage STRIPE=AppTheme.image("images/social/swap-stripe.png");
    private static final BufferedImage CABINET=AppTheme.image("images/social/cabinet.png");
    private Social social;
    private final BiConsumer<BookSwap,SwapRecord> onOpen;
    private final JPanel rows=new JPanel(null);
    private final JPanel dots=new JPanel(null);
    private final Timer settle=new Timer(16,e->settleStep());
    private final Map<Integer,BufferedImage> pageLayers=new HashMap<>();
    private BufferedImage backgroundLayer;
    private List<BookSwap> swaps=List.of();
    private int page, otherPage=-1, dragStart;
    private float slide;
    private boolean dragging, dragged;

    /** @param onOpen given a swap and its shared record, once the record has been read */
    public BookSwapPanel(Social social,BiConsumer<BookSwap,SwapRecord> onOpen) {
        super("Book swap");
        this.social=social;this.onOpen=onOpen;
        setStatusArea(676,true);
        rows.setOpaque(false);rows.setBounds(0,23,SCENE_WIDTH,700);
        dots.setOpaque(false);dots.setBounds(0,DOT_TOP-8,SCENE_WIDTH,24);
        for(JComponent component:new JComponent[]{dots,rows})add(component);
        for(JComponent component:new JComponent[]{this,rows})listenForSwipe(component);
        showPage(0);
    }

    public void reload() {
        request("loading...",social::swaps,result->{
            swaps=result;stopMotion();pageLayers.clear();showPage(page);
            if(result.isEmpty())setMessage("no book swaps yet");
        });
    }

    public void showNewest() {page=Integer.MAX_VALUE;}

    public boolean isBusyForTest() {return isBusy();}

    /** Where swaps are kept from now on; what the screen was showing came from the old place. */
    public void setSocial(Social social) {this.social=social;reset();}

    /**
     * A swap the friend has accepted opens on its shared record, and so does the record kept
     * from one that ended; a swap still waiting has nothing to show yet.
     */
    private void open(BookSwap swap) {
        if(dragged || isBusy())return;
        if(swap.waiting()) {showNotice("waiting for "+swap.friendHandle());return;}
        request("opening...",()->social.record(swap),record->onOpen.accept(swap,record));
    }

    public void reset() {cancelRequests();stopMotion();pageLayers.clear();swaps=List.of();page=0;showPage(0);}

    private int pageCount() {return Math.max(1,(swaps.size()+PAGE_SIZE-1)/PAGE_SIZE);}

    private void showPage(int requested) {
        int pages=pageCount();
        page=Math.max(0,Math.min(requested,pages-1));
        rows.removeAll();
        for(int i=page*PAGE_SIZE;i<Math.min((page+1)*PAGE_SIZE,swaps.size());i++) {
            BookSwap swap=swaps.get(i);
            Rectangle cover=COVERS[i-page*PAGE_SIZE];
            JButton open=hit(swap.bookTitle()+(swap.waiting()?" 수락 대기":swap.ended()?" 지난 기록 열기":" 기록 열기"),
                    cover.x,cover.y-23,381-cover.x,cover.height,
                    ()->open(swap));
            listenForSwipe(open);
            rows.add(open);
        }
        addDots(pages);
        rows.revalidate();dots.revalidate();repaint();
    }

    /** Shows at most five dots, sliding the window so the current page stays in view. */
    private int firstDot(int pages) {return Math.max(0,Math.min(page-MAX_DOTS/2,pages-MAX_DOTS));}

    private int dotX(int index,int count) {return 206-(count*DOT+(count-1)*DOT_GAP)/2+index*(DOT+DOT_GAP);}

    private void addDots(int pages) {
        dots.removeAll();
        if(pages<=1)return;
        int count=Math.min(pages,MAX_DOTS), first=firstDot(pages);
        for(int i=0;i<count;i++) {
            int target=first+i;
            dots.add(hit("페이지 "+(target+1),dotX(i,count)-5,3,DOT+10,DOT+10,()->goTo(target)));
        }
    }

    private void listenForSwipe(JComponent component) {
        MouseAdapter swipe=new MouseAdapter() {
            private int x(MouseEvent e) {return SwingUtilities.convertPoint(e.getComponent(),e.getPoint(),BookSwapPanel.this).x;}
            @Override public void mousePressed(MouseEvent e) {
                dragging=!settle.isRunning() && pageCount()>1;dragged=false;dragStart=x(e);
            }
            @Override public void mouseDragged(MouseEvent e) {
                if(!dragging)return;
                int distance=x(e)-dragStart;
                if(!dragged && Math.abs(distance)<DRAG_START)return;
                dragged=true;
                int neighbor=distance<0?page+1:page-1;
                boolean exists=neighbor>=0 && neighbor<pageCount();
                otherPage=exists?neighbor:-1;
                slide=exists?distance:distance/3f;
                repaint();
            }
            @Override public void mouseReleased(MouseEvent e) {
                if(!dragging)return;
                dragging=false;
                if(dragged) {
                    if(otherPage>=0 && Math.abs(slide)>=SWIPE_DISTANCE)goTo(otherPage);
                    else settle.restart();
                }
                // The button's own release handler runs in this event too; let it see the drag first.
                SwingUtilities.invokeLater(()->dragged=false);
            }
        };
        component.addMouseListener(swipe);component.addMouseMotionListener(swipe);
    }

    private void goTo(int target) {
        if(target==page)return;
        slide+=target>page?SCENE_WIDTH:-SCENE_WIDTH;
        otherPage=page;
        showPage(target);
        settle.restart();
    }

    private void settleStep() {
        slide*=.72f;
        if(Math.abs(slide)<1)stopMotion();
        repaint();
    }

    private void stopMotion() {settle.stop();slide=0;otherPage=-1;dragging=false;}

    private BufferedImage layer(Consumer<Graphics2D> painter) {
        BufferedImage image=new BufferedImage(SCENE_WIDTH,SCENE_HEIGHT,BufferedImage.TYPE_INT_ARGB);
        Graphics2D g=image.createGraphics();smooth(g);painter.accept(g);g.dispose();
        return image;
    }

    @Override void paintScene(Graphics2D g) {
        if(slide==0) {
            paintBackground(g);paintBooks(g,page);
        } else {
            // Shadows are too slow to redraw every frame, so a moving page uses cached layers.
            if(backgroundLayer==null)backgroundLayer=layer(this::paintBackground);
            g.drawImage(backgroundLayer,0,0,null);
            int x=Math.round(slide);
            g.drawImage(pageLayer(page),x,0,null);
            if(otherPage>=0)g.drawImage(pageLayer(otherPage),x+(otherPage<page?-SCENE_WIDTH:SCENE_WIDTH),0,null);
        }
        paintDots(g);
    }

    private BufferedImage pageLayer(int index) {
        return pageLayers.computeIfAbsent(index,key->layer(g->paintBooks(g,key)));
    }

    private void paintDots(Graphics2D g) {
        int pages=pageCount();
        if(pages<=1)return;
        int count=Math.min(pages,MAX_DOTS), first=firstDot(pages);
        for(int i=0;i<count;i++) {
            g.setColor(first+i==page?Color.WHITE:new Color(255,255,255,115));
            g.fillOval(dotX(i,count),DOT_TOP,DOT,DOT);
        }
    }

    private void paintBackground(Graphics2D g) {
        SceneImages.cover(g,WALLPAPER,-153,24,718,893);
        SceneImages.cover(g,STRIPE,-28,-20,465,775);
        drawShadowed(g,CABINET,-133,71,677,1259,10,20,8,.25f);
        Font heading=AppFonts.heading(40);
        drawText(g,"Book swap",heading,AppColors.BROWN,206-textWidth(g,"Book swap",heading)/2,30,false);
    }

    private void paintBooks(Graphics2D g,int index) {
        for(int i=index*PAGE_SIZE;i<Math.min((index+1)*PAGE_SIZE,swaps.size());i++) {
            int slot=i-index*PAGE_SIZE;
            paintCover(g,swaps.get(i).cover(),COVERS[slot]);
            paintDetails(g,swaps.get(i),TEXT_TOP[slot]);
        }
    }

    private static void paintCover(Graphics2D g,BufferedImage image,Rectangle slot) {
        double scale=Math.min((double)slot.width/image.getWidth(),(double)slot.height/image.getHeight());
        int width=(int)Math.round(image.getWidth()*scale), height=(int)Math.round(image.getHeight()*scale);
        drawShadowed(g,image,slot.x+(slot.width-width)/2,slot.y+slot.height-height,width,height,16,16,12,.25f);
    }

    private static void paintDetails(Graphics2D g,BookSwap swap,int top) {
        float y=top;
        y=shadowLine(g,swap.bookTitle(),186,y);
        if(!swap.author().isEmpty())y=shadowLine(g,swap.author(),186,y);
        // Until the friend accepts, the swap is only asked for; after the friendship, it is a record of what was.
        String with=swap.waiting()?"waiting for ":swap.ended()?"once with ":"with ";
        Font latin=fitted(g,with+swap.friendHandle(),AppFonts.headingRegular(24),195);
        float withWidth=textWidth(g,with,latin);
        softShadow(g,with,latin,186,y);softShadow(g,swap.friendHandle(),latin,186+withWidth,y);
        drawText(g,with,latin,Color.WHITE,186,y,false);
        drawText(g,swap.friendHandle(),latin,Color.WHITE,186+withWidth,y,true);
    }

    private static float shadowLine(Graphics2D g,String text,float x,float y) {
        boolean korean=text.codePoints().anyMatch(c->Character.UnicodeScript.of(c)==Character.UnicodeScript.HANGUL);
        Font font=korean?AppFonts.body(24):AppFonts.headingRegular(24);
        float width=textWidth(g,text,font);
        if(width>195)font=font.deriveFont(24*195/width);
        softShadow(g,text,font,x,y);
        drawText(g,text,font,Color.WHITE,x,y,false);
        return y+24*(korean?1.29f:1.2f);
    }

    private static void softShadow(Graphics2D g,String text,Font font,float x,float y) {
        Color shadow=new Color(0x3D,0x3D,0x3D,70);
        for(int dx=-2;dx<=2;dx++)for(int dy=-2;dy<=2;dy++)
            if(dx!=0 || dy!=0)drawText(g,text,font,shadow,x+dx,y+dy,false);
    }
}
