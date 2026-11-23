package archivefever.ui.screens;

import archivefever.model.BookSpine;
import archivefever.ui.components.StationeryButton;
import archivefever.ui.components.ReadingText;
import archivefever.model.ReadingDocument;
import archivefever.ui.theme.AppFonts;
import archivefever.ui.theme.AppTheme;
import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.awt.font.*;
import java.awt.geom.AffineTransform;
import java.util.List;

/** The portrait reading page and its wider, open-book layout. */
public final class ReadingPanel extends JPanel {
    public static final int PORTRAIT_WIDTH=412;
    public static final int PORTRAIT_HEIGHT=917;
    public static final int EXPANDED_WIDTH=1280;
    public static final int EXPANDED_HEIGHT=900;

    private final Scene scene;
    private boolean expandedMode;
    private BufferedImage transitionFrom,transitionTo;
    private float transitionProgress;
    private boolean resumeMemoFocus;

    public ReadingPanel(BookSpine book,Runnable onReturn) {
        this(book.id(),book.title(),"슬라보예 지젝",Scene.QUOTE,List.of(),"",onReturn);
    }

    /** Book swap notes reuse this page; a non-empty memoAuthor selects the shared-note layout. */
    public ReadingPanel(String documentId,String title,String author,String text,
                        List<String> comments,String memoAuthor,Runnable onReturn) {
        setLayout(new BorderLayout());
        scene=new Scene(documentId,title,author,text,comments,memoAuthor,onReturn,this);
        add(scene,BorderLayout.CENTER);
    }

    public boolean isExpandedMode() {return expandedMode;}

    public void setExpandedMode(boolean expandedMode) {
        if(this.expandedMode==expandedMode) return;
        this.expandedMode=expandedMode;
        scene.revalidate();
        scene.repaint();
        revalidate();
        repaint();
    }

    public void beginTransition(boolean expanded) {
        resumeMemoFocus=scene.readingText.memoEditor().isFocusOwner();
        transitionFrom=snapshot(expandedMode);
        transitionTo=snapshot(expanded);
        expandedMode=expanded;
        transitionProgress=0;
        // The snapshots receive no input; controls become active again at the end.
        scene.setVisible(false);
    }

    public void setTransitionProgress(float progress) {
        transitionProgress=Math.max(0,Math.min(1,progress));
        repaint();
    }

    public void finishTransition() {
        transitionFrom=null;transitionTo=null;
        scene.setVisible(true);
        revalidate();repaint();
        if(resumeMemoFocus){
            resumeMemoFocus=false;
            SwingUtilities.invokeLater(()->{
                if(isShowing() && scene.readingText.memoEditor().isVisible())
                    scene.readingText.memoEditor().requestFocusInWindow();
            });
        }
    }

    private BufferedImage snapshot(boolean expanded) {
        boolean previous=expandedMode;
        Dimension previousSize=scene.getSize();
        expandedMode=expanded;
        Dimension size=scene.designSize();
        scene.setSize(size);scene.doLayout();
        BufferedImage image=new BufferedImage(size.width*2,size.height*2,BufferedImage.TYPE_INT_ARGB);
        Graphics2D g=image.createGraphics();g.scale(2,2);scene.printAll(g);g.dispose();
        expandedMode=previous;scene.setSize(previousSize);scene.doLayout();
        return image;
    }

    @Override protected void paintChildren(Graphics graphics) {
        if(transitionFrom==null){super.paintChildren(graphics);return;}
        Graphics2D g=(Graphics2D)graphics.create();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        archivefever.ui.components.SceneImages.cover(g,Scene.WOOD,0,0,getWidth(),getHeight());
        paintSnapshot(g,transitionFrom,1);
        paintSnapshot(g,transitionTo,transitionProgress);
        g.dispose();
    }

    private void paintSnapshot(Graphics2D g,BufferedImage image,float opacity) {
        double scale=Math.min((double)getWidth()/image.getWidth(),(double)getHeight()/image.getHeight());
        double width=image.getWidth()*scale,height=image.getHeight()*scale;
        AffineTransform transform=new AffineTransform();
        transform.translate((getWidth()-width)/2,(getHeight()-height)/2);transform.scale(scale,scale);
        g.setComposite(AlphaComposite.SrcOver.derive(opacity));
        g.drawImage(image,transform,null);
    }

    public ReadingText readingText(){return scene.readingText;}

    public List<StationeryButton> stationeryButtons() {return scene.stationery;}

    private static final class Scene extends JPanel {
        private static final BufferedImage WALL_TRIM=AppTheme.image("images/read/wood-trim.png");
        private static final BufferedImage WOOD=AppTheme.image("images/read/book-edge.png");
        private static final BufferedImage PORTRAIT_PAGE=AppTheme.image("images/read/portrait-page.png");
        private static final BufferedImage OPEN_BOOK=AppTheme.image("images/read/open-book.png");
        private static final BufferedImage EXPANDED_WALL=AppTheme.image("images/read/expanded-wall.png");
        private final ReadingPanel owner;
        private final String title,author;
        private final boolean sharedNote;
        private static final String QUOTE="즉 타락은 자체로 이미 자기 지양이요, 상처는 자체로 이미 자신의 치유이다. 그러니 타락의 인식은 결국 오인이요, 왜곡된 시각의 효과이다.";
        private final ReadingText readingText;
        private final ReturnButton returnButton;
        private final List<StationeryButton> stationery;
        private double scale=1,offsetX,offsetY;

        Scene(String documentId,String title,String author,String text,List<String> comments,
              String memoAuthor,Runnable onReturn,ReadingPanel owner) {
            this.owner=owner;
            setOpaque(true);
            setBackground(Color.WHITE);
            setLayout(new ReadingLayout(this));
            this.title=title;this.author=author;sharedNote=!memoAuthor.isEmpty();
            readingText=new ReadingText(new ReadingDocument(documentId,text),comments,memoAuthor);
            returnButton=new ReturnButton(onReturn);
            stationery=List.of(
                    new StationeryButton("pencil","images/read/pencil.png"),
                    new StationeryButton("magnifying glass","images/read/hand-lens.png"),
                    new StationeryButton("highlighter","images/read/highlighter.png"),
                    new StationeryButton("fountain pen","images/read/fountain-pen.png"));
            add(returnButton);
            // Later Figma layers appear above earlier ones.
            stationery.forEach(button->add(button,0));
            add(readingText,0);
            stationery.get(0).setToolTipText("연필: 선택 후 문장을 클릭하면 밑줄");
            stationery.get(2).setToolTipText("형광펜: 선택 후 문장을 클릭하면 하이라이트");
            stationery.get(3).setToolTipText("볼펜: 선택 후 문장을 클릭하면 메모 · Enter로 저장");
            stationery.get(1).setToolTipText("돋보기: 확대 → 축소 → 기본");
            stationery.get(0).addActionListener(e->selectTool(ReadingText.Tool.PENCIL,0));
            stationery.get(2).addActionListener(e->selectTool(ReadingText.Tool.HIGHLIGHTER,2));
            stationery.get(3).addActionListener(e->selectTool(ReadingText.Tool.PEN,3));
            stationery.get(1).addActionListener(e->{
                readingText.cycleZoom();
                stationery.get(1).setActive(readingText.zoomStep()!=0);
                revalidate();doLayout();repaint();
            });
            getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE,0),"return");
            getActionMap().put("return",new AbstractAction() {
                @Override public void actionPerformed(java.awt.event.ActionEvent e) {onReturn.run();}
            });
        }

        private void selectTool(ReadingText.Tool tool,int index){
            readingText.setTool(tool);
            for(int i:new int[]{0,2,3})stationery.get(i).setActive(i==index);
        }

        Dimension designSize() {
            return owner.expandedMode?new Dimension(EXPANDED_WIDTH,EXPANDED_HEIGHT)
                    :new Dimension(PORTRAIT_WIDTH,PORTRAIT_HEIGHT);
        }

        private void layoutScene() {
            boolean expanded=owner.expandedMode;
            double designWidth=expanded?EXPANDED_WIDTH:PORTRAIT_WIDTH;
            double designHeight=expanded?EXPANDED_HEIGHT:PORTRAIT_HEIGHT;
            scale=Math.min(getWidth()/designWidth,getHeight()/designHeight);
            if(!Double.isFinite(scale) || scale<=0) scale=1;
            offsetX=(getWidth()-designWidth*scale)/2;
            offsetY=(getHeight()-designHeight*scale)/2;

            if(expanded) {
                setAt(returnButton,-81.42,18,274.245,91.619);
                stationery.get(0).setSceneBounds(1016.57,402.07,338.436,416.001,123.83,true);
                stationery.get(1).setSceneBounds(1124.58,758,172,249,90,true);
                stationery.get(2).setSceneBounds(1060.58,497,242,367,116.99,true);
                stationery.get(3).setSceneBounds(1076.58,545,414.173,414.173,-16.36,true);
            } else {
                setAt(returnButton,-81,36,274.245,91.619);
                stationery.get(0).setSceneBounds(-46,667,297.587,416,-33.83,false);
                stationery.get(1).setSceneBounds(277,814,172,249,0,false);
                stationery.get(2).setSceneBounds(16,750,242,367,-26.99,false);
                stationery.get(3).setSceneBounds(64,766,414.173,414.173,106.36,false);
            }
            boolean sharedPortrait=sharedNote && !expanded;
            setAt(readingText,expanded?243.58:sharedPortrait?38:28,expanded?146:sharedPortrait?179:163,335,expanded?600:500);
            readingText.setInsets(sharedPortrait?6:0,sharedPortrait?3:0);
            readingText.setDisplayScale(scale);
            stationery.forEach(button->button.layoutAt(scale,offsetX,offsetY));
            returnButton.setDisplayScale(scale);
        }

        private void setAt(Component component,double x,double y,double width,double height) {
            component.setBounds((int)Math.round(offsetX+x*scale),(int)Math.round(offsetY+y*scale),
                    Math.max(0,(int)Math.round(width*scale)),
                    Math.max(0,(int)Math.round(height*scale)));
        }

        @Override protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g=(Graphics2D)graphics.create();
            g.translate(offsetX,offsetY);
            g.scale(scale,scale);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            if(owner.expandedMode) paintExpanded(g); else paintPortrait(g);
            paintText(g);
            g.dispose();
        }

        private void paintPortrait(Graphics2D g) {
            // Keep image-fill scale and clip, including the removed status bar area.
            wallpaper(g,0,263);
            wallpaper(g,687,239);
            drawRotated(g,WOOD,-2,46,414,234);
            drawRotated(g,WOOD,-2,683,414,234);
            drawCover(g,PORTRAIT_PAGE,-2,44,795,839);
        }

        private void paintExpanded(Graphics2D g) {
            g.drawImage(EXPANDED_WALL,0,-36,1691,354,null);
            drawRotated(g,WOOD,0,29.3,551.381,311.368,false);
            drawRotated(g,WOOD,0,335.62,551.381,311.368,false);
            drawRotated(g,WOOD,825.74,339.62,551.381,311.368,false);
            drawRotated(g,WOOD,551.38,29.3,551.381,311.368,true);
            drawRotated(g,WOOD,1101.43,29.3,551.381,311.368,false);
            drawRotated(g,WOOD,0,579.35,551.381,EXPANDED_HEIGHT-579.35,false);
            drawRotated(g,WOOD,551.38,579.35,551.381,EXPANDED_HEIGHT-579.35,true);
            drawRotated(g,WOOD,1101.43,579.35,551.381,EXPANDED_HEIGHT-579.35,false);
            drawCover(g,OPEN_BOOK,174,62,921,838);
        }

        private void wallpaper(Graphics2D g,int y,int h) {
            Graphics2D clipped=(Graphics2D)g.create();clipped.clipRect(-29,y,450,h);
            clipped.drawImage(WALL_TRIM,-29,y,450,446,null);clipped.dispose();
        }

        private void paintText(Graphics2D g) {
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS,RenderingHints.VALUE_FRACTIONALMETRICS_ON);
            boolean wide=owner.expandedMode;
            drawLine(g,title,(wide?EXPANDED_WIDTH:PORTRAIT_WIDTH)-12,wide?44:50,20,Color.WHITE);
            if(!author.isEmpty())drawLine(g,author,(wide?EXPANDED_WIDTH:PORTRAIT_WIDTH)-12,wide?70:76,15,Color.WHITE);
        }
        private static void drawLine(Graphics2D g,String text,float right,float y,float size,Color color) {
            Font font=AppFonts.body(size).deriveFont(java.util.Map.of(TextAttribute.TRACKING,-.02f));
            TextLayout line=new TextLayout(text,font,g.getFontRenderContext());
            g.setColor(color);line.draw(g,right-line.getAdvance(),y+(size-line.getAscent()-line.getDescent())/2+line.getAscent());
        }

        private static void drawCover(Graphics2D g,BufferedImage image,int x,int y,int width,int height) {
            double imageScale=Math.max((double)width/image.getWidth(),(double)height/image.getHeight());
            int drawnWidth=(int)Math.round(image.getWidth()*imageScale);
            int drawnHeight=(int)Math.round(image.getHeight()*imageScale);
            Shape oldClip=g.getClip();
            g.clipRect(x,y,width,height);
            g.drawImage(image,x+(width-drawnWidth)/2,y+(height-drawnHeight)/2,drawnWidth,drawnHeight,null);
            g.setClip(oldClip);
        }

        private static void drawRotated(Graphics2D g,BufferedImage image,int x,int y,int width,int height) {
            Graphics2D rotated=(Graphics2D)g.create();
            rotated.translate(x+width/2.0,y+height/2.0);
            rotated.rotate(Math.PI/2);
            rotated.drawImage(image,-height/2,-width/2,height,width,null);
            rotated.dispose();
        }

        private static void drawRotated(Graphics2D g,BufferedImage image,double x,double y,
                                        double width,double height,boolean flipVertically) {
            Graphics2D rotated=(Graphics2D)g.create();
            rotated.translate(x+width/2,y+height/2);
            rotated.rotate(Math.PI/2);
            if(flipVertically) rotated.scale(1,-1);
            AffineTransform fill=new AffineTransform();
            fill.translate(-height/2,-width/2);
            fill.scale(height/image.getWidth(),width/image.getHeight());
            rotated.drawImage(image,fill,null);
            rotated.dispose();
        }
    }

    private static final class ReadingLayout implements LayoutManager2 {
        private final Scene scene;

        ReadingLayout(Scene scene) {this.scene=scene;}
        @Override public void addLayoutComponent(Component component,Object constraints) {}
        @Override public void addLayoutComponent(String name,Component component) {}
        @Override public void removeLayoutComponent(Component component) {}
        @Override public Dimension preferredLayoutSize(Container parent) {return scene.designSize();}
        @Override public Dimension minimumLayoutSize(Container parent) {return new Dimension(320,480);}
        @Override public Dimension maximumLayoutSize(Container parent) {return new Dimension(Integer.MAX_VALUE,Integer.MAX_VALUE);}
        @Override public float getLayoutAlignmentX(Container target) {return .5f;}
        @Override public float getLayoutAlignmentY(Container target) {return .5f;}
        @Override public void invalidateLayout(Container target) {}
        @Override public void layoutContainer(Container parent) {scene.layoutScene();}
    }

    private static final class ReturnButton extends JButton {
        private static final BufferedImage RIBBON=AppTheme.image("images/read/return-ribbon.png");
        private double displayScale=1;
        ReturnButton(Runnable onReturn) {
            setOpaque(false);setContentAreaFilled(false);setBorderPainted(false);setFocusPainted(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            getAccessibleContext().setAccessibleName("return to archive");
            addActionListener(e->onReturn.run());
        }
        void setDisplayScale(double scale){displayScale=scale;}
        @Override protected void paintComponent(Graphics graphics) {
            Graphics2D g=(Graphics2D)graphics.create();g.scale(displayScale,displayScale);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            Graphics2D ribbon=(Graphics2D)g.create();
            ribbon.translate(274.245/2,91.619/2);ribbon.rotate(Math.toRadians(86.18));
            ribbon.translate(-73.803/2,-269.929/2);
            ribbon.clip(new java.awt.geom.Rectangle2D.Double(0,0,73.803,269.929));
            AffineTransform fill=new AffineTransform();fill.translate(-3.1713*73.803,0);
            fill.scale(5.5556*73.803/RIBBON.getWidth(),2.0253*269.929/RIBBON.getHeight());
            ribbon.drawImage(RIBBON,fill,null);ribbon.dispose();
            g.setColor(Color.WHITE);g.setFont(AppFonts.headingRegular(32));
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.drawString("return",109,61);
            if(hasFocus()){g.setColor(Color.WHITE);g.drawLine(109,67,185,67);}
            g.dispose();
        }
    }
}
