package archivefever.ui.screens;

import archivefever.mock.DemoSocial;
import archivefever.model.BookSpine;
import archivefever.model.Friend;
import archivefever.ui.components.Bookshelf;
import archivefever.ui.components.ChoicePopup;
import archivefever.ui.components.SceneImages;
import archivefever.ui.theme.*;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.List;

/** Friend's Archive, Find your friend, a friend's cabinet and Books for Swap. */
public final class FriendPanel extends SocialPanel {
    private enum View {LIST,FIND,LIBRARY,SWAP}
    private static final int PAGE_SIZE=4, ROW=121, LIST_TOP=116, FIND_TOP=190, FIND_BOTTOM=790;
    private static final int SCROLL_STEP=60, SEARCH_DELAY_MILLIS=250;    private static final int[] BUST_X={26,28,28,26}, BUST_Y={-11,-8,-8,-8}, BUST_SIZE={139,136,136,139};
    private static final int[] SHELF_FLOORS={314,475,614};
    private static final Color PILL_MUTED=new Color(0x7A736B);
    private static final BufferedImage DAMASK=AppTheme.image("images/social/damask.png");
    private static final BufferedImage PAPER=AppTheme.image("images/main/book-popup-paper.png");
    private static final BufferedImage BUST_A=AppTheme.image("images/social/bust-a.png");
    private static final BufferedImage BUST_B=AppTheme.image("images/social/bust-b.png");
    private static final BufferedImage CABINET=AppTheme.image("images/social/cabinet.png");
    private static final BufferedImage WALL=AppTheme.image("images/read/wall.png");
    private static final Shape SEARCH_ICON=SceneImages.svgPath("images/social/search.svg");
    private final DemoSocial social;
    private final ChoicePopup choices;
    private final Runnable onRequested;
    private final JPanel rows=new JPanel(null);
    private final JTextField search=new JTextField();
    private final JButton searchButton=hit("친구 검색",38,158,20,20,this::runSearch);
    private final JButton findMore=link("find more....","친구 찾기",265,665,142,28,typewriter(20),Color.BLACK);
    private final JButton previous=link("prev","이전 페이지",40,665,52,28,typewriter(20),AppColors.LIGHT_BROWN);
    private final JButton next=link("next","다음 페이지",112,665,52,28,typewriter(20),AppColors.LIGHT_BROWN);
    private final Bookshelf shelf;
    private List<Friend> friends=List.of(), results=List.of();
    private List<BookSpine> library=List.of();
    private Friend openFriend;
    private View view=View.LIST;
    private int page, findScroll;
    private final Timer searchTimer=new Timer(SEARCH_DELAY_MILLIS,e->{if(view==View.FIND)runSearch();});

    public FriendPanel(DemoSocial social,ChoicePopup choices,Runnable onRequested) {
        super("friend");
        this.social=social;this.choices=choices;this.onRequested=onRequested;
        search.setBounds(71,154,302,28);search.setFont(typewriter(20));search.setForeground(AppColors.GRAY);
        search.setCaretColor(AppColors.GRAY);search.setOpaque(false);search.setBorder(null);
        search.getAccessibleContext().setAccessibleName("친구 검색어");
        search.addActionListener(e->runSearch());
        search.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) {searchTimer.restart();repaint();}
            public void removeUpdate(javax.swing.event.DocumentEvent e) {searchTimer.restart();repaint();}
            public void changedUpdate(javax.swing.event.DocumentEvent e) {repaint();}
        });
        searchTimer.setRepeats(false);
        addMouseWheelListener(this::scrollResults);
        findMore.addActionListener(e->openFind());
        previous.addActionListener(e->showPage(page-1));
        next.addActionListener(e->showPage(page+1));
        shelf=new Bookshelf(List.of(),this::askSwap);
        shelf.setBounds(18,86,377,749);shelf.setTinted(true);
        rows.setOpaque(false);rows.setBounds(0,23,412,894);
        for(JComponent component:new JComponent[]{search,searchButton,findMore,previous,next,shelf,rows})add(component);
        showView(View.LIST);
    }

    public void reload() {showView(View.LIST);loadFriends();}

    public boolean isBusyForTest() {return isBusy();}

    public String noticeForTest() {return statusText();}

    public void reset() {
        cancelRequests();search.setText("");searchTimer.stop();
        friends=List.of();results=List.of();library=List.of();openFriend=null;findScroll=0;
        showView(View.LIST);
    }

    private void loadFriends() {
        request("loading...",()->social.friends(""),result->{
            friends=result;showPage(page);
            if(result.isEmpty())setMessage("no friends yet");
        });
    }

    private void openFind() {
        cancelRequests();search.setText("");searchTimer.stop();results=List.of();findScroll=0;
        showView(View.FIND);search.requestFocusInWindow();
    }

    /** Runs shortly after typing stops, or at once on Enter; no loading text so results do not flicker. */
    private void runSearch() {
        searchTimer.stop();
        String query=search.getText().strip();
        if(query.isEmpty()) {cancelRequests();results=List.of();findScroll=0;showPage(0);return;}
        request("",()->social.friends(query),result->{
            results=result;findScroll=0;showPage(0);
            if(result.isEmpty())setMessage("no results");
        });
    }

    private int maxFindScroll() {return Math.max(0,results.size()*ROW-(FIND_BOTTOM-FIND_TOP));}

    private void scrollResults(java.awt.event.MouseWheelEvent event) {
        if(view!=View.FIND)return;
        int target=findScroll+(int)Math.round(event.getPreciseWheelRotation()*SCROLL_STEP);
        target=Math.max(0,Math.min(target,maxFindScroll()));
        if(target!=findScroll) {findScroll=target;showPage(0);}
    }

    private void requestFriend(Friend friend) {
        if(isBusy())return;
        request("sending request...",()->social.requestFriend(friend.id()),sent->{
            replaceResult(sent);showNotice("request sent");
        });
    }

    private void confirmRequest(Friend friend) {
        if(isBusy())return;
        request("confirming...",()->social.confirmRequest(friend.id()),added->{
            replaceResult(added);showNotice(added.name()+" added");
        });
    }

    private void declineRequest(Friend friend) {
        if(isBusy())return;
        request("declining...",()->social.declineRequest(friend.id()),declined->{
            replaceResult(declined);showNotice("request declined");
        });
    }

    private void replaceResult(Friend updated) {
        results=results.stream().map(item->item.id().equals(updated.id())?updated:item).toList();
        showPage(page);
    }

    private void askNext(Friend friend) {
        choices.ask("what’s next?","visit","swap","remove",()->openLibrary(friend,View.LIBRARY),
                ()->openLibrary(friend,View.SWAP),()->askRemove(friend));
    }

    private void askRemove(Friend friend) {
        if(isBusy())return;
        choices.ask("remove?","yes","no",()->removeFriend(friend),null);
    }

    private void removeFriend(Friend friend) {
        request("removing...",()->social.removeFriend(friend.id()),removed->{
            friends=friends.stream().filter(item->!item.id().equals(removed.id())).toList();
            results=results.stream().map(item->item.id().equals(removed.id())?removed:item).toList();
            showPage(page);
            if(view==View.LIST && friends.isEmpty())setMessage("no friends yet");
            else showNotice(removed.name()+" removed");
        });
    }

    private void openLibrary(Friend friend,View target) {
        openFriend=friend;library=List.of();shelf.setBooks(List.of());showView(target);
        request("loading shelf...",()->social.library(friend.id()),books->{
            library=books;
            if(target==View.SWAP)shelf.setBooks(books);else showPage(0);
            if(books.isEmpty())setMessage("no books yet");
        });
    }

    private void askSwap(BookSpine book) {
        Friend friend=openFriend;
        if(friend==null || isBusy())return;
        choices.ask("send request?","yes","no",()->requestSwap(friend,book),null);
    }

    private void requestSwap(Friend friend,BookSpine book) {
        request("sending request...",()->social.requestSwap(friend.id(),book),swap->onRequested.run());
    }

    private void backToList() {openFriend=null;showView(View.LIST);loadFriends();}

    private void showView(View target) {
        setMessage("");
        view=target;page=0;
        switch(target) {
            case LIST->setStatusArea(614,false);
            case LIBRARY->setStatusArea(660,true);
            case SWAP->setStatusArea(442,true);
            case FIND->{}
        }
        boolean finding=target==View.FIND;
        search.setVisible(finding);searchButton.setVisible(finding);
        findMore.setVisible(target==View.LIST);
        shelf.setVisible(target==View.SWAP);
        rows.setVisible(target!=View.SWAP);
        setBackAction(target==View.LIST?null:this::backToList);
        showPage(0);
    }

    private void showPage(int requested) {
        rows.removeAll();
        boolean paged=view==View.LIST;
        int pages=paged?Math.max(1,(friends.size()+PAGE_SIZE-1)/PAGE_SIZE):1;
        page=Math.max(0,Math.min(requested,pages-1));
        previous.setVisible(paged && pages>1);next.setVisible(paged && pages>1);
        previous.setEnabled(page>0);next.setEnabled(page<pages-1);
        if(view==View.LIST)addFriendRows();
        else if(view==View.FIND) {
            findScroll=Math.min(findScroll,maxFindScroll());
            // With results on the paper, status moves to a slip so it never sits on a row.
            setStatusArea(results.isEmpty()?200:746,!results.isEmpty());
            addResultRows();
        } else if(view==View.LIBRARY)addSpines();
        rows.revalidate();repaint();
    }

    private void addFriendRows() {
        for(int i=page*PAGE_SIZE;i<Math.min((page+1)*PAGE_SIZE,friends.size());i++) {
            Friend friend=friends.get(i);
            int y=LIST_TOP+(i-page*PAGE_SIZE)*ROW;
            rows.add(hit(friend.handle()+" 선택",26,y+1-23,348,ROW-2,()->askNext(friend)));
        }
    }

    private void addResultRows() {
        for(int i=0;i<results.size();i++) {
            Friend friend=results.get(i);
            int y=FIND_TOP+i*ROW-findScroll;
            if(y+ROW<=FIND_TOP || y>=FIND_BOTTOM)continue;
            if(friend.added()) {
                int top=Math.max(y+1,FIND_TOP), bottom=Math.min(y+ROW-1,FIND_BOTTOM);
                rows.add(hit(friend.handle()+" 선택",26,top-23,348,bottom-top,()->askNext(friend)));
            } else if(y+83>=FIND_TOP && y+112<=FIND_BOTTOM)addRequestPills(friend,y+83-23);
        }
    }

    private void addRequestPills(Friend friend,int y) {
        String handle=friend.handle();
        switch(friend.status()) {
            case NONE->rows.add(pill("REQUEST",handle+" 친구 신청",268,y,101,AppColors.GRAY,()->requestFriend(friend)));
            case REQUESTED->rows.add(pill("REQUESTED",handle+" 신청 대기",244,y,125,PILL_MUTED,null));
            case INCOMING->{
                rows.add(pill("DECLINE",handle+" 신청 거절",165,y,98,PILL_MUTED,()->declineRequest(friend)));
                rows.add(pill("CONFIRM",handle+" 신청 수락",271,y,98,AppColors.GRAY,()->confirmRequest(friend)));
            }
            case ADDED->{}
        }
    }

    private JButton pill(String text,String name,int x,int y,int width,Color fill,Runnable action) {
        JButton button=new JButton(text) {
            @Override protected void paintComponent(Graphics graphics) {
                Graphics2D g=(Graphics2D)graphics.create();smooth(g);
                g.setColor(fill);g.fillRoundRect(0,0,getWidth(),getHeight(),24,24);
                Font font=typewriter(20);float textWidth=textWidth(g,text,font);
                drawText(g,text,font,Color.WHITE,(getWidth()-textWidth)/2,4,false);
                if(hasFocus()) {g.setColor(Color.WHITE);g.drawRoundRect(2,2,getWidth()-5,getHeight()-5,20,20);}
                g.dispose();
            }
        };
        button.setBounds(x,y,width,29);button.setBorder(null);button.setOpaque(false);
        button.setContentAreaFilled(false);button.setFocusPainted(false);
        button.getAccessibleContext().setAccessibleName(name);
        if(action==null)button.setEnabled(false);
        else {
            button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            button.addActionListener(e->action.run());
        }
        return button;
    }

    private void paintAddedPill(Graphics2D g,int y) {
        g.setColor(Color.WHITE);g.fillRoundRect(295,y,74,29,24,24);
        g.setColor(AppColors.GRAY);g.setStroke(new BasicStroke(1f));g.drawRoundRect(295,y,73,28,24,24);
        Font font=typewriter(20);
        drawText(g,"ADDED",font,AppColors.GRAY,295+(74-textWidth(g,"ADDED",font))/2,y+4,false);
    }

    private void addSpines() {
        int shelfIndex=0, x=53;
        for(BookSpine book:library) {
            int width=Math.max(1,Math.round(book.width()*118f/172));
            if(x+width>333) {shelfIndex++;x=53;}
            if(shelfIndex>=SHELF_FLOORS.length)break;
            JLabel spine=new JLabel(new ImageIcon(book.image().getScaledInstance(width,118,Image.SCALE_SMOOTH)));
            spine.setBounds(x,SHELF_FLOORS[shelfIndex]-118-23,width,118);
            spine.setToolTipText(book.title());spine.getAccessibleContext().setAccessibleName(book.title());
            rows.add(spine);
            x+=width+1;
        }
    }

    @Override void paintScene(Graphics2D g) {
        SceneImages.cover(g,DAMASK,-6,-1,418,557);
        SceneImages.cover(g,DAMASK,-4,556,418,557);
        switch(view) {
            case LIST->paintList(g);
            case FIND->paintFind(g);
            case LIBRARY->paintLibrary(g);
            case SWAP->paintSwap(g);
        }
    }

    private void paintPaper(Graphics2D g,int height) {
        g.setColor(Color.WHITE);g.fillRect(14,44,385,height);
        Graphics2D paper=(Graphics2D)g.create();
        paper.setComposite(AlphaComposite.SrcOver.derive(.8f));
        SceneImages.cover(paper,PAPER,14,44,385,height);paper.dispose();
    }

    private void rule(Graphics2D g,int x,int y) {g.setColor(AppColors.LIGHT_BROWN);g.fillRect(x,y-1,335,1);}

    private void paintList(Graphics2D g) {
        paintPaper(g,660);
        Font title=typewriter(36);
        drawText(g,"Friend's Archive",title,AppColors.GRAY,206.5f-textWidth(g,"Friend's Archive",title)/2,69,false);
        for(int i=0;i<=PAGE_SIZE;i++)rule(g,39,LIST_TOP+i*ROW);
        paintPeople(g,friends,LIST_TOP,29,75,page*PAGE_SIZE,Math.min((page+1)*PAGE_SIZE,friends.size()));
    }

    private void paintFind(Graphics2D g) {
        paintPaper(g,778);
        drawText(g,"Find your friend",typewriter(36),AppColors.LIGHT_BROWN,49,69,false);
        rule(g,38,116);rule(g,38,184);
        drawText(g,"FRIEND DIRECTORY 001",typewriter(16),AppColors.LIGHT_BROWN,39,116,false);
        Graphics2D icon=(Graphics2D)g.create();icon.translate(38,158);icon.setColor(AppColors.GRAY);
        icon.setStroke(new BasicStroke(2,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));icon.draw(SEARCH_ICON);icon.dispose();
        if(search.getText().isEmpty())drawText(g,"Serch name or @user name",typewriter(15),AppColors.LIGHT_BROWN,71,161,false);
        if(!results.isEmpty()) {
            rule(g,38,FIND_TOP);
            Graphics2D list=(Graphics2D)g.create();
            list.clipRect(14,FIND_TOP,385,FIND_BOTTOM-FIND_TOP);
            int top=FIND_TOP-findScroll;
            for(int i=1;i<=results.size();i++)rule(list,39,top+i*ROW);
            paintPeople(list,results,top,7,53,0,results.size());
            list.dispose();
        }
    }

    private void paintPeople(Graphics2D g,List<Friend> people,int top,int nameOffset,int handleOffset,int from,int to) {
        Font nameFont=typewriter(32), handleFont=typewriter(20);
        for(int i=from;i<to;i++) {
            Friend friend=people.get(i);
            int slot=(i-from)%PAGE_SIZE, y=top+(i-from)*ROW;
            g.drawImage(slot==0 || slot==3?BUST_A:BUST_B,BUST_X[slot],y+BUST_Y[slot],BUST_SIZE[slot],BUST_SIZE[slot],null);
            drawText(g,friend.name(),nameFont,AppColors.GRAY,372-textWidth(g,friend.name(),nameFont),y+nameOffset,false);
            drawText(g,friend.handle(),handleFont,AppColors.LIGHT_BROWN,367-textWidth(g,friend.handle(),handleFont),y+handleOffset,true);
            if(view==View.FIND && friend.added())paintAddedPill(g,y+83);
        }
    }

    private void paintLibrary(Graphics2D g) {
        drawShadowed(g,CABINET,-12,106,430,799,10,20,8,.25f);
        if(openFriend==null)return;
        String name=openFriend.name();
        String title=Character.toUpperCase(name.charAt(0))+name.substring(1)+"’s book Archive";
        Font font=typewriter(36);
        float width=textWidth(g,title,font);
        if(width>380) {font=typewriter(36*380/width);width=textWidth(g,title,font);}
        drawText(g,title,font,new Color(0x6F6B47),206-width/2,71,false);
    }

    private void paintSwap(Graphics2D g) {
        Graphics2D wall=(Graphics2D)g.create();wall.clipRect(0,830,412,87);
        SceneImages.cover(wall,WALL,-51,830,496,917);
        wall.setColor(new Color(245,156,156,61));wall.fillRect(0,830,412,87);wall.dispose();
        drawText(g,"Books for Swap",AppFonts.heading(36),AppColors.BROWN,100,30,false);
    }
}
