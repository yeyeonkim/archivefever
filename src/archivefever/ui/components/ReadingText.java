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

    public ReadingText(ReadingDocument document) {
        this.document=document;status=document.loadWarning();
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
        addMouseListener(new MouseAdapter(){
            private void popup(MouseEvent e){
                if(!e.isPopupTrigger())return;
                for(Memo memo:memos)if(e.getY()/scale>=memo.top && e.getY()/scale<memo.top+memo.height){menu(e,memo.sentence);break;}
            }
            @Override public void mousePressed(MouseEvent e){popup(e);}
            @Override public void mouseReleased(MouseEvent e){popup(e);}
            @Override public void mouseClicked(MouseEvent e){
                if(!SwingUtilities.isLeftMouseButton(e) && e.getButton()!=MouseEvent.NOBUTTON)return;
                Point2D point=new Point2D.Double(e.getX()/scale,e.getY()/scale);
                int sentence=sentenceAt(point);
                if(sentence>=0)apply(sentence);
                else for(Memo memo:memos)if(point.getY()>=memo.top && point.getY()<memo.top+memo.height){edit(memo.sentence);break;}
            }
        });
    }
    private void menu(MouseEvent event,int sentence){
        JPopupMenu menu=new JPopupMenu();
        JMenuItem edit=new JMenuItem("메모 수정"),delete=new JMenuItem("메모 삭제");
        edit.addActionListener(e->edit(sentence));
        delete.addActionListener(e->deleteMemo(sentence));
        menu.add(edit);menu.add(delete);
        menu.show(event.getComponent(),event.getX(),event.getY());
    }
    public void deleteMemo(int sentence){
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
    private void refresh(){revalidate();doLayout();repaint();}
    private void stash(){if(editing>=0)drafts.put(editing,editor.getText());}
    private void edit(int sentence){
        stash();editing=sentence;
        editor.setText(drafts.getOrDefault(sentence,document.mark(sentence).note()));
        editor.setVisible(true);refresh();editor.requestFocusInWindow();
    }
    private void apply(int sentence){
        try{
            switch(tool){
                case PENCIL->document.underline(sentence);
                case HIGHLIGHTER->document.highlight(sentence);
                case PEN->{document.enableMemo(sentence);edit(sentence);}
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
        paragraphBottom=rows.size()*lineHeight();
        memos.clear();float y=paragraphBottom+15;
        editor.setFont(font().deriveFont((float)(font().getSize2D()*scale)));
        for(int i=0;i<document.sentences().size();i++){
            ReadingDocument.Mark mark=document.mark(i);if(!mark.memo())continue;
            String content=i==editing?editor.getText():mark.note();
            List<Row> noteRows=wrap(content,font(),327,y);
            float height=Math.max(lineHeight()*2,noteRows.size()*lineHeight())+8;
            memos.add(new Memo(i,noteRows,y,height));
            if(i==editing)editor.setBounds(0,(int)Math.round(y*scale),(int)Math.round(327*scale),
                    (int)Math.ceil(Math.min(height+lineHeight()*2,Math.max(lineHeight()*2,getHeight()/scale-y))*scale));
            y+=height;
        }
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
        for(Row row:rows){
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
        if(!memos.isEmpty()){
            g.setColor(Color.BLACK);g.setStroke(new BasicStroke(.7f));
            g.draw(new Line2D.Float(0,paragraphBottom+11,335,paragraphBottom+11));
            g.setColor(new Color(0x474747));
            for(Memo memo:memos)if(memo.sentence!=editing)for(Row row:memo.rows)row.layout.draw(g,0,row.baseline);
        }
        if(!status.isEmpty()){
            g.setColor(new Color(0x9A2929));g.setFont(AppFonts.body(11));
            g.drawString(status,0,Math.min((float)(getHeight()/scale)-8,paragraphBottom+130));
        }
        g.dispose();
    }
}
