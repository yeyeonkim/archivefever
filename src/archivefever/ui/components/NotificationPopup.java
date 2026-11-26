package archivefever.ui.components;

import archivefever.model.Notice;
import archivefever.ui.theme.AppFonts;
import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;

/**
 * The notification paper: friend and book swap requests to answer, and news of how the
 * user's own requests were answered, newest first and three to a page. The list is read,
 * and requests are answered, through background calls.
 */
public final class NotificationPopup extends PaperPopup {
    /** Answers the request a notice is about; returns the notice as it reads afterwards. */
    public interface Answerer {Notice answer(Notice notice,boolean accept) throws Exception;}

    private static final int ROW_TOP=306, ROW_HEIGHT=88, PAGE_SIZE=3, RESULT_RIGHT=352;
    private static final Color ERROR=new Color(0x9B2D20);
    private final Runnable confirmSound;
    private final List<Component> rowParts=new ArrayList<>();
    /** Ids of the notices whose answer is on its way, and of those whose answer did not get through. */
    private final Set<String> deciding=new HashSet<>(), failed=new HashSet<>();
    /**
     * Requests answered here, by id, as they read since. A list that was already being read
     * when the answer was given still calls them unanswered, and must not undo them.
     */
    private final Map<String,Notice> answered=new HashMap<>();
    private final JLabel status;
    private final JButton retry, previous, next;
    private Callable<List<Notice>> source=List::of;
    private Answerer answerer=(notice,accept)->notice;
    private List<Notice> notices=List.of();
    private int page;
    /** Bumped by every reload and reset, so that a list read earlier is ignored. */
    private int generation;
    /** Bumped by every reset, so that the answer to a request of the last session is ignored. */
    private int session;

    public NotificationPopup(Runnable dismiss){this(dismiss,()->{});}
    public NotificationPopup(Runnable dismiss,Runnable confirmSound){
        super(new Rectangle(51,267,311,384),dismiss);
        this.confirmSound=Objects.requireNonNull(confirmSound);
        label("notification",51,273,311,34,24,true);
        separator(300,true);
        status=label("",51,430,311,30,20,true);
        retry=paperAction("retry","알림 다시 불러오기",183,464,46,30,this::reload);
        previous=paperAction("prev","이전 알림",120,606,42,30,()->showPage(page-1));
        next=paperAction("next","다음 알림",250,606,42,30,()->showPage(page+1));
        for(JButton small:new JButton[]{retry,previous,next})small.setFont(AppFonts.typewriter(18));
        showPage(0);
    }

    /** Where the notices come from and how a request among them is answered. Both run off the event thread. */
    public void setSource(Callable<List<Notice>> source,Answerer answerer){
        this.source=Objects.requireNonNull(source);this.answerer=Objects.requireNonNull(answerer);
        resetSession();
    }

    /** Reads the list again; what is on the paper stays until the new list arrives. */
    public void reload(){
        int current=++generation;
        setStatus(notices.isEmpty()?"loading...":"",false);
        showPage(page);
        Callable<List<Notice>> call=source;
        new SwingWorker<List<Notice>,Void>(){
            @Override protected List<Notice> doInBackground() throws Exception {return call.call();}
            @Override protected void done(){
                if(current!=generation)return;
                try {
                    notices=get().stream().map(read->read.state()==Notice.State.PENDING
                            ?answered.getOrDefault(read.id(),read):read).toList();
                    setStatus(notices.isEmpty()?"no notifications yet":"",false);
                } catch(ExecutionException e) {
                    if(notices.isEmpty())setStatus("could not load",true);
                } catch(InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                showPage(page);
            }
        }.execute();
    }

    /** Forgets everything shown: the next person to sign in has notices of their own. */
    public void resetSession(){
        generation++;session++;deciding.clear();failed.clear();answered.clear();
        notices=List.of();page=0;setStatus("",false);showPage(0);
    }

    private void setStatus(String text,boolean canRetry){
        status.setText(text);status.setForeground(canRetry?ERROR:Color.BLACK);
        status.setVisible(!text.isEmpty());retry.setVisible(canRetry);
    }

    private void showPage(int requested){
        for(Component part:rowParts)remove(part);
        rowParts.clear();
        int pages=Math.max(1,(notices.size()+PAGE_SIZE-1)/PAGE_SIZE);
        page=Math.max(0,Math.min(requested,pages-1));
        for(int i=page*PAGE_SIZE;i<Math.min((page+1)*PAGE_SIZE,notices.size());i++)
            addRow(notices.get(i),ROW_TOP+(i-page*PAGE_SIZE)*ROW_HEIGHT);
        previous.setVisible(pages>1);next.setVisible(pages>1);
        previous.setEnabled(page>0);next.setEnabled(page<pages-1);
        revalidate();repaint();
    }

    private <T extends Component> T part(T component){rowParts.add(component);return component;}

    private void addRow(Notice notice,int top){
        part(message(notice,top+6));
        if(!notice.book().isEmpty()) {
            JLabel book=part(new JLabel(notice.book()));
            book.setFont(AppFonts.body(13));book.setForeground(new Color(0x474747));
            book.setBounds(66,top+29,288,18);add(book);
        }
        if(notice.isRequest())addAnswers(notice,top+46);
        int count=getComponentCount();
        separator(top+76,false);
        part(getComponent(count));
    }

    /** The handle, underlined, and what its owner did; shrunk to fit the paper when long. */
    private JLabel message(Notice notice,int y){
        String handle=notice.handle();
        JLabel label=new JLabel(handle+notice.message()){
            @Override protected void paintComponent(Graphics graphics){
                Graphics2D g=(Graphics2D)graphics.create();
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                // With letters placed on whole pixels a slightly smaller size is no narrower, and the line's end is cut off.
                g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS,RenderingHints.VALUE_FRACTIONALMETRICS_ON);
                Font font=getFont();
                double width=font.getStringBounds(getText(),g.getFontRenderContext()).getWidth(),room=getWidth()-2;
                if(width>room)font=font.deriveFont((float)(font.getSize2D()*room/width));
                g.setFont(font);g.setColor(Color.BLACK);
                FontMetrics fm=g.getFontMetrics(font);
                int baseline=(getHeight()-fm.getHeight())/2+fm.getAscent();
                g.drawString(getText(),0,baseline);
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
                g.setStroke(new BasicStroke(.65f));
                g.draw(new java.awt.geom.Line2D.Double(0,baseline+1,font.getStringBounds(handle,g.getFontRenderContext()).getWidth(),baseline+1));
                g.dispose();
            }
        };
        label.setFont(AppFonts.typewriter(15));label.setBounds(66,y,288,24);add(label);
        return label;
    }

    private void addAnswers(Notice notice,int y){
        boolean friend=notice.kind()==Notice.Kind.FRIEND_REQUEST;
        String name=notice.handle()+(friend?" 친구 요청 ":" 교환 요청 ");
        boolean waiting=deciding.contains(notice.id());
        JButton confirm=part(paperAction("Confirm",name+"확인",256,y,96,35,()->decide(notice,true)));
        if(notice.state()!=Notice.State.PENDING) {
            // Answered: what was answered stands where the choices were, as plain text.
            String result=notice.state()==Notice.State.CONFIRMED?"Confirmed":"Declined";
            confirm.putClientProperty(SETTLED,true);confirm.setText(result);confirm.setEnabled(false);
            int width=confirm.getFontMetrics(confirm.getFont()).stringWidth(result)+1;
            confirm.setBounds(RESULT_RIGHT-width,y,width,35);
            return;
        }
        JButton decline=part(paperAction("Decline",name+"거절",146,y,98,35,()->decide(notice,false)));
        confirm.setEnabled(!waiting);decline.setEnabled(!waiting);
        if(failed.contains(notice.id())) {
            JLabel error=part(label("failed",66,y,76,35,15,false));
            error.setForeground(ERROR);
        }
    }

    private void decide(Notice notice,boolean accept){
        if(deciding.contains(notice.id()) || notice.state()!=Notice.State.PENDING)return;
        if(accept)confirmSound.run();
        deciding.add(notice.id());failed.remove(notice.id());
        showPage(page);
        int current=session;
        Answerer call=answerer;
        new SwingWorker<Notice,Void>(){
            @Override protected Notice doInBackground() throws Exception {return call.answer(notice,accept);}
            @Override protected void done(){
                if(current!=session)return;
                deciding.remove(notice.id());
                try {
                    Notice now=get();
                    answered.put(notice.id(),now);
                    notices=notices.stream().map(shown->shown.id().equals(notice.id())?now:shown).toList();
                } catch(ExecutionException e) {
                    failed.add(notice.id());
                } catch(InterruptedException e) {
                    Thread.currentThread().interrupt();failed.add(notice.id());
                }
                showPage(page);
            }
        }.execute();
    }
}
