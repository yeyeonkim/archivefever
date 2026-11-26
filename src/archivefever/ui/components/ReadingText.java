package archivefever.ui.components;

import archivefever.model.ReadingDocument;
import archivefever.ui.theme.AppFonts;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.font.*;
import java.awt.geom.*;
import java.io.IOException;
import java.text.AttributedString;
import java.util.*;
import java.util.List;

/** TextLayout is shared by painting and sentence hit-testing, including zoom. */
public final class ReadingText extends JPanel {
    public enum Tool { NONE, PENCIL, HIGHLIGHTER, PEN }
    private record Row(TextLayout layout,int start,int end,float baseline) {}
    private record Memo(int sentence,List<Row> rows,float top,float height) {}
    private static final FontRenderContext FRC=new FontRenderContext(null,true,true);
    private final ReadingDocument document;
    private final JTextArea editor=new JTextArea();
    private final Map<Integer,String> drafts=new HashMap<>();
    private final List<Row> rows=new ArrayList<>();
    private final List<Memo> memos=new ArrayList<>();
    private Tool tool=Tool.NONE;
    private int editing=-1,zoom;
    private double scale=1;
    private float paragraphBottom;
    private String status;
    private final List<String> comments;
    private final String memoAuthor;
    private final List<Row> commentRows=new ArrayList<>();
    private final Map<Row,Integer> nameLengths=new HashMap<>();
    private int bodyX,commentX;
    private String placeholder="";
    /** How far the text is scrolled up, and where its last line or memo ends, in design pixels. */
    private float scroll,contentBottom;

    public ReadingText(ReadingDocument document) {this(document,List.of(),"");}

    /** Shared-note variant: friends' comments and "@me : note" lines appear under the passage. */
    public ReadingText(ReadingDocument document,List<String> comments,String memoAuthor) {
        this.document=document;status=document.loadWarning();
        this.comments=List.copyOf(comments);this.memoAuthor=memoAuthor;
        setLayout(null);setOpaque(false);
        getAccessibleContext().setAccessibleName("독서 본문: 필기구 선택 후 문장을 클릭하세요");
        editor.setOpaque(false);editor.setLineWrap(true);editor.setWrapStyleWord(true);
        editor.setBorder(BorderFactory.createEmptyBorder());editor.setForeground(new Color(0x474747));
        editor.getAccessibleContext().setAccessibleName("문장 메모: Enter로 저장");
        editor.setToolTipText("Enter: 저장 · 내용을 비우고 Enter: 삭제 · Shift+Enter: 줄바꿈");
        editor.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER,0),"save");
        editor.getActionMap().put("save",new AbstractAction(){
            @Override public void actionPerformed(ActionEvent e){saveMemo();}
        });
        editor.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE,0),"cancel");
        editor.getActionMap().put("cancel",new AbstractAction(){
            @Override public void actionPerformed(ActionEvent e){stash();editing=-1;editor.setVisible(false);refresh();}
        });
        editor.getDocument().addDocumentListener(new javax.swing.event.DocumentListener(){
            public void insertUpdate(javax.swing.event.DocumentEvent e){later();}
            public void removeUpdate(javax.swing.event.DocumentEvent e){later();}
            public void changedUpdate(javax.swing.event.DocumentEvent e){later();}
            private void later(){SwingUtilities.invokeLater(()->refresh());}
        });
        editor.setVisible(false);add(editor);
        editor.addMouseListener(new MouseAdapter(){
            @Override public void mousePressed(MouseEvent e){if(e.isPopupTrigger() && editing>=0)menu(e,editing);}
            @Override public void mouseReleased(MouseEvent e){if(e.isPopupTrigger() && editing>=0)menu(e,editing);}
        });
        MouseAdapter mouse=new MouseAdapter(){
            private int pressY;
            private float pressScroll;
            private boolean dragged;
            private void popup(MouseEvent e){
                if(!e.isPopupTrigger())return;
                double y=e.getY()/scale+scroll;
                for(Memo memo:memos)if(y>=memo.top && y<memo.top+memo.height){menu(e,memo.sentence);break;}
            }
            @Override public void mousePressed(MouseEvent e){pressY=e.getY();pressScroll=scroll;dragged=false;popup(e);}
            @Override public void mouseReleased(MouseEvent e){popup(e);}
            // Dragging up or down moves a text that is longer than the page; so does the wheel.
            @Override public void mouseDragged(MouseEvent e){
                double moved=(pressY-e.getY())/scale;
                if(!dragged && Math.abs(moved)<6)return;
                dragged=true;scrollTo(pressScroll+(float)moved);
            }
            @Override public void mouseWheelMoved(MouseWheelEvent e){
                scrollTo(scroll+(float)(e.getPreciseWheelRotation()*lineHeight()*3));
            }
            @Override public void mouseClicked(MouseEvent e){
                if(dragged)return;
                if(!SwingUtilities.isLeftMouseButton(e) && e.getButton()!=MouseEvent.NOBUTTON)return;
                Point2D point=new Point2D.Double(e.getX()/scale-bodyX,e.getY()/scale+scroll);
                int sentence=sentenceAt(point);
                if(sentence>=0)apply(sentence);
                else for(Memo memo:memos)if(point.getY()>=memo.top && point.getY()<memo.top+memo.height){edit(memo.sentence);break;}
            }
        };
        addMouseListener(mouse);addMouseMotionListener(mouse);addMouseWheelListener(mouse);
    }
    /** How far the text can be scrolled: zero while all of it fits on the page. */
    public float maxScroll(){return (float)Math.max(0,contentBottom+lineHeight()-getHeight()/scale);}
    public float scrollOffset(){return scroll;}
    public void scrollTo(float offset){
        float next=Math.max(0,Math.min(offset,maxScroll()));
        if(next==scroll)return;
        scroll=next;
        // Only an open memo editor has a place of its own to move; the rest is repainted.
        if(editing>=0)doLayout();
        repaint();
    }
    private void menu(MouseEvent event,int sentence){
        JPopupMenu menu=new JPopupMenu();
        JMenuItem edit=new JMenuItem("메모 수정"),delete=new JMenuItem("메모 삭제");
        edit.addActionListener(e->edit(sentence));
        delete.addActionListener(e->deleteMemo(sentence));
        menu.add(edit);menu.add(delete);
        menu.show(event.getComponent(),event.getX(),event.getY());
    }
    /** Says why, when the document takes no more memos, and tells the caller to leave it at that. */
    private boolean memosClosed(){
        String why=document.memosClosed();
        if(why==null)return false;
        status=why;repaint();
        return true;
    }
    public void deleteMemo(int sentence){
        if(memosClosed())return;
        try{
            document.deleteMemo(sentence);drafts.remove(sentence);
            if(editing==sentence){editing=-1;editor.setVisible(false);}
            status="";refresh();
        }catch(IOException e){status="메모를 삭제하지 못했습니다. 다시 시도해주세요.";repaint();}
    }

    public ReadingDocument document(){return document;}
    public JTextArea memoEditor(){return editor;}
    public Tool tool(){return tool;}
    public int zoomStep(){return zoom;}
    public float zoomFactor(){return zoom==1?18f/14:zoom==2?11f/14:1;}
    public void setTool(Tool tool){this.tool=tool;setCursor(Cursor.getPredefinedCursor(tool==Tool.NONE?Cursor.DEFAULT_CURSOR:Cursor.HAND_CURSOR));}
    public void cycleZoom(){zoom=(zoom+1)%3;refresh();}
    public void setDisplayScale(double scale){this.scale=scale;refresh();}
    public void setInsets(int bodyX,int commentX){this.bodyX=bodyX;this.commentX=commentX;}
    /** Shown in place of the body while the document has no text. */
    public void setPlaceholder(String text){placeholder=text==null?"":text;repaint();}
    private void rememberName(String text,List<Row> noteRows){
        int end=text.indexOf(' ');
        if(text.startsWith("@") && end>0 && !noteRows.isEmpty())nameLengths.put(noteRows.get(0),end);
    }
    private void drawNoteRow(Graphics2D g,Row row){
        row.layout.draw(g,0,row.baseline);
        Integer name=nameLengths.get(row);
        if(name==null)return;
        double right=row.layout.getLogicalHighlightShape(0,name).getBounds2D().getMaxX();
        g.setStroke(new BasicStroke(.7f));g.draw(new Line2D.Double(0,row.baseline+2,right,row.baseline+2));
    }
    private Font font(){return AppFonts.body(14*zoomFactor()).deriveFont(Map.of(TextAttribute.TRACKING,-.02f));}
    private float lineHeight(){return 14*zoomFactor();}
    private static List<Row> wrap(String text,Font font,float width,float top) {
        List<Row> result=new ArrayList<>();if(text.isEmpty())return result;
        // Preserve explicit memo newlines while keeping document character offsets.
        int offset=0;
        for(String paragraph:text.split("\\n",-1)){
            if(paragraph.isEmpty()){top+=font.getSize2D();offset++;continue;}
            AttributedString attributed=new AttributedString(paragraph);attributed.addAttribute(TextAttribute.FONT,font);
            LineBreakMeasurer measure=new LineBreakMeasurer(attributed.getIterator(),FRC);
            while(measure.getPosition()<paragraph.length()){
                int start=measure.getPosition();TextLayout line=measure.nextLayout(width);
                float baseline=top+(font.getSize2D()-line.getAscent()-line.getDescent())/2+line.getAscent();
                result.add(new Row(line,offset+start,offset+measure.getPosition(),baseline));top+=font.getSize2D();
            }
            offset+=paragraph.length()+1;
        }
        return result;
    }
    /** Top of the line a row was laid out on; the reverse of how {@link #wrap} places a baseline. */
    private float lineTop(Row row){
        return row.baseline-row.layout.getAscent()-(lineHeight()-row.layout.getAscent()-row.layout.getDescent())/2;
    }
    private void refresh(){revalidate();doLayout();repaint();}
    private void stash(){if(editing>=0)drafts.put(editing,editor.getText());}
    private void edit(int sentence){
        if(memosClosed())return;
        stash();editing=sentence;
        editor.setText(drafts.getOrDefault(sentence,document.mark(sentence).note()));
        editor.setVisible(true);refresh();editor.requestFocusInWindow();
    }
    private void apply(int sentence){
        try{
            switch(tool){
                case PENCIL->document.underline(sentence);
                case HIGHLIGHTER->document.highlight(sentence);
                case PEN->{if(memosClosed())return;document.enableMemo(sentence);edit(sentence);}
                case NONE->{return;}
            }
            status="";refresh();
        }catch(IOException e){status="저장하지 못했습니다. 다시 시도해주세요.";repaint();}
    }
    public void saveMemo(){
        if(editing<0)return;
        try{
            document.saveMemo(editing,editor.getText().strip());drafts.remove(editing);
            editing=-1;editor.setVisible(false);status="";refresh();
        }catch(IOException e){status="메모 저장 실패: 입력 내용은 유지됩니다.";repaint();}
    }
    @Override public void doLayout(){
        rows.clear();rows.addAll(wrap(document.text(),font(),327,0));
        // Where the last line ends, not the number of lines: a blank line between passages takes room too.
        paragraphBottom=rows.isEmpty()?0:lineTop(rows.get(rows.size()-1))+lineHeight();
        memos.clear();commentRows.clear();nameLengths.clear();float y=paragraphBottom+15;
        editor.setFont(font().deriveFont((float)(font().getSize2D()*scale)));
        for(String comment:comments){
            List<Row> noteRows=wrap(comment,font(),327,y);
            rememberName(comment,noteRows);commentRows.addAll(noteRows);
            y+=Math.max(lineHeight()*2,noteRows.size()*lineHeight())+8;
        }
        for(int i=0;i<document.sentences().size();i++){
            ReadingDocument.Mark mark=document.mark(i);if(!mark.memo())continue;
            String content=i==editing?editor.getText():memoAuthor.isEmpty()?mark.note():memoAuthor+" : "+mark.note();
            List<Row> noteRows=wrap(content,font(),327,y);
            if(i!=editing)rememberName(content,noteRows);
            float height=Math.max(lineHeight()*2,noteRows.size()*lineHeight())+8;
            memos.add(new Memo(i,noteRows,y,height));
            if(i==editing)editor.setBounds((int)Math.round(commentX*scale),(int)Math.round((y-scroll)*scale),(int)Math.round(327*scale),
                    (int)Math.ceil(Math.min(height+lineHeight()*2,Math.max(lineHeight()*2,getHeight()/scale-(y-scroll)))*scale));
            y+=height;
        }
        contentBottom=memos.isEmpty() && commentRows.isEmpty()?paragraphBottom:y;
        scroll=Math.max(0,Math.min(scroll,maxScroll()));
    }
    private Shape segment(Row row,int start,int end){
        Shape shape=row.layout.getLogicalHighlightShape(start-row.start,end-row.start);
        return AffineTransform.getTranslateInstance(0,row.baseline).createTransformedShape(shape);
    }
    private int sentenceAt(Point2D point){
        for(Row row:rows){
            Rectangle2D bounds=new Rectangle2D.Float(0,row.baseline-row.layout.getAscent(),row.layout.getAdvance(),lineHeight()+2);
            if(!bounds.contains(point))continue;
            int character=row.start+row.layout.hitTestChar((float)point.getX(),(float)point.getY()-row.baseline).getCharIndex();
            for(int i=0;i<document.sentences().size();i++){
                var sentence=document.sentences().get(i);
                if(character>=sentence.start() && character<sentence.end())return i;
            }
        }
        return -1;
    }
    @Override protected void paintComponent(Graphics graphics){
        Graphics2D g=(Graphics2D)graphics.create();g.scale(scale,scale);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        if(maxScroll()>0){
            // A thin marker at the right edge shows there is more, and where the page is in it.
            double page=getHeight()/scale,whole=page+maxScroll();
            g.setColor(new Color(0,0,0,70));
            g.fill(new RoundRectangle2D.Double(332,scroll/whole*page,2,page/whole*page,2,2));
        }
        g.translate(bodyX,-scroll);
        if(rows.isEmpty() && !placeholder.isEmpty()){
            g.setColor(new Color(0x8A8A8A));
            for(Row row:wrap(placeholder,font(),327,0))row.layout.draw(g,0,row.baseline);
        }
        double viewTop=scroll-lineHeight()*2,viewBottom=scroll+getHeight()/scale+lineHeight()*2;
        for(Row row:rows){
            if(row.baseline<viewTop || row.baseline>viewBottom)continue;
            for(int i=0;i<document.sentences().size();i++){
                var sentence=document.sentences().get(i);var mark=document.mark(i);
                int start=Math.max(row.start,sentence.start()),end=Math.min(row.end,sentence.end());
                if(start>=end)continue;
                Shape area=segment(row,start,end);
                if(mark.highlighted()){g.setColor(new Color(251,234,84,156));g.fill(area);}
                if(mark.underlined()){
                    Rectangle2D bounds=area.getBounds2D();g.setColor(Color.BLACK);g.setStroke(new BasicStroke(.7f));
                    g.draw(new Line2D.Double(bounds.getMinX(),row.baseline+1,bounds.getMaxX(),row.baseline+1));
                }
            }
            g.setColor(Color.BLACK);row.layout.draw(g,0,row.baseline);
        }
        g.translate(-bodyX,0);
        if(!memos.isEmpty() || !commentRows.isEmpty()){
            g.setColor(Color.BLACK);g.setStroke(new BasicStroke(.7f));
            g.draw(new Line2D.Float(0,paragraphBottom+11,335,paragraphBottom+11));
            g.setColor(new Color(0x474747));
            g.translate(commentX,0);
            for(Row row:commentRows)drawNoteRow(g,row);
            for(Memo memo:memos)if(memo.sentence!=editing)for(Row row:memo.rows)drawNoteRow(g,row);
            g.translate(-commentX,0);
        }
        if(!status.isEmpty()){
            g.setColor(new Color(0x9A2929));g.setFont(AppFonts.body(11));
            // Below the memos, when there are that many, and never off the bottom of the page.
            float below=Math.max(paragraphBottom+130,memos.isEmpty() && commentRows.isEmpty()?0:contentBottom+lineHeight());
            g.drawString(status,0,Math.min(scroll+(float)(getHeight()/scale)-8,below));
        }
        g.dispose();
    }
}
