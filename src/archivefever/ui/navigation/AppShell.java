package archivefever.ui.navigation;

import archivefever.mock.DemoBooks;
import archivefever.mock.DemoSocial;
import archivefever.model.BookSwap;
import archivefever.ui.components.ChoicePopup;
import archivefever.audio.SoundPlayer;
import archivefever.ui.components.ScanConfirmationPopup;
import archivefever.model.BookSpine;
import archivefever.ui.components.BookSelectionPopup;
import archivefever.ui.components.NotificationPopup;
import archivefever.ui.components.ProfilePopup;
import archivefever.ui.screens.*;
import archivefever.ui.components.BookmarkNavigation;
import archivefever.ui.theme.AppTheme;
import javax.swing.*;
import java.awt.*;

public final class AppShell extends JPanel {
    private final CardLayout screens=new CardLayout();
    private final JPanel content=new JPanel(screens);
    private final BookmarkNavigation navigation;
    private final BookSelectionPopup selectionPopup;
    private final NotificationPopup notificationPopup;
    private final ProfilePopup profilePopup;
    private final MenuPanel menuPanel;
    private final AddBookPanel addBookPanel;
    private final SentenceScanPanel sentenceScanPanel;
    private final ScanConfirmationPopup scanPopup;
    private final DemoSocial demoSocial=new DemoSocial(250,DemoBooks.load());
    private final FriendPanel friendPanel;
    private final BookSwapPanel bookSwapPanel;
    private final ChoicePopup choicePopup=new ChoicePopup(this::dismissChoice);
    private final Runnable shutterSound;
    private final Runnable notificationSound;
    private final Runnable editSound;
    private Destination currentDestination;
    private boolean loggedIn;
    private JFrame readingFrame;
    private Container mainContentPane;
    private Dimension mainWindowSize;
    private Point mainWindowLocation;
    private boolean mainWindowResizable;
    private ReadingPanel readingPanel;
    private Timer readingTransition;

    public AppShell(){this(new SoundPlayer("sounds/camera-shutter-click-07.wav")::play,
            new SoundPlayer("sounds/mixkit-cooking-bell-ding-1791.wav")::play);}

    public AppShell(Runnable shutterSound){
        this(shutterSound,new SoundPlayer("sounds/mixkit-cooking-bell-ding-1791.wav")::play);
    }

    public AppShell(Runnable shutterSound,Runnable notificationSound){
        this(shutterSound,notificationSound,new SoundPlayer("sounds/mixkit-scissors-cutting-paper-2378.wav")::play);
    }

    public AppShell(Runnable shutterSound,Runnable notificationSound,Runnable editSound) {
        this.editSound=java.util.Objects.requireNonNull(editSound);
        this.shutterSound=java.util.Objects.requireNonNull(shutterSound);
        this.notificationSound=java.util.Objects.requireNonNull(notificationSound);
        setLayout(null);
        setPreferredSize(new Dimension(AppTheme.WIDTH,AppTheme.HEIGHT));
        content.setBounds(0,0,412,917);
        content.add(new LoginPanel(this::onKakaoLogin),"login");
        content.add(new MainPanel(DemoBooks.load(),this::showBookSelection),Destination.ARCHIVE.name());
        menuPanel=new MenuPanel(this::showNotifications,this::showProfile,()->{
            if(loggedIn && currentDestination==Destination.MENU)this.editSound.run();
        });
        content.add(menuPanel,Destination.MENU.name());
        addBookPanel=new AddBookPanel(this::scanBook);
        content.add(addBookPanel,Destination.ADD_BOOK.name());
        sentenceScanPanel=new SentenceScanPanel(this::captureSentence,()->navigate(Destination.ARCHIVE));
        content.add(sentenceScanPanel,"sentence-scan");
        friendPanel=new FriendPanel(demoSocial,choicePopup,this::showRequestedSwap);
        content.add(friendPanel,Destination.FRIEND.name());
        bookSwapPanel=new BookSwapPanel(demoSocial,this::openSwapNote);
        content.add(bookSwapPanel,Destination.BOOK_SWAP.name());
        for(Destination destination:Destination.values())
            if(destination!=Destination.ARCHIVE && destination!=Destination.MENU && destination!=Destination.ADD_BOOK
                    && destination!=Destination.FRIEND && destination!=Destination.BOOK_SWAP)
                content.add(new BlankPanel(destination.label()),destination.name());
        add(content);
        navigation=new BookmarkNavigation(this::navigate);
        navigation.setBounds(0,750,412,167);navigation.setVisible(false);
        add(navigation,0);
        selectionPopup=new BookSelectionPopup(this::openReading,this::openSentenceScan,this::dismissBookSelection);
        selectionPopup.setBounds(0,0,412,917);selectionPopup.setVisible(false);
        add(selectionPopup,0);
        notificationPopup=new NotificationPopup(this::dismissMenuPopups,this.notificationSound,
                ()->demoSocial.confirmRequest(DemoSocial.REQUESTER_ID),()->demoSocial.declineRequest(DemoSocial.REQUESTER_ID));
        profilePopup=new ProfilePopup(this::dismissMenuPopups,this::logout);
        for(JPanel popup:new JPanel[]{notificationPopup,profilePopup}){
            popup.setBounds(0,0,412,917);popup.setVisible(false);add(popup,0);
        }
        scanPopup=new ScanConfirmationPopup(this::dismissScanConfirmation,()->{
            dismissScanConfirmation();navigate(Destination.ARCHIVE);
        });
        scanPopup.setBounds(0,0,412,917);scanPopup.setVisible(false);add(scanPopup,0);
        choicePopup.setBounds(0,0,412,917);choicePopup.setVisible(false);add(choicePopup,0);
        screens.show(content,"login");
    }

    public void navigate(Destination destination) {
        if(!loggedIn) return;
        dismissMenuPopups();dismissScanConfirmation();selectionPopup.setVisible(false);menuPanel.resetHover();
        sentenceScanPanel.reset();
        currentDestination=destination;
        screens.show(content,destination.name());
        if(destination==Destination.FRIEND)friendPanel.reload();
        if(destination==Destination.BOOK_SWAP)bookSwapPanel.reload();
        navigation.setDestination(destination);navigation.setSentenceCaptureLayout(false);
        navigation.setVisible(true);
    }

    private void openSentenceScan(BookSpine book){
        if(!loggedIn || currentDestination!=Destination.ARCHIVE)return;
        dismissMenuPopups();dismissScanConfirmation();selectionPopup.setVisible(false);
        sentenceScanPanel.setBook(book);
        screens.show(content,"sentence-scan");
        navigation.setDestination(Destination.ARCHIVE);navigation.setSentenceCaptureLayout(true);navigation.setVisible(true);
        sentenceScanPanel.requestFocusInWindow();repaint();
    }
    private void captureSentence(){
        if(!loggedIn || sentenceScanPanel.selectedBook()==null || sentenceScanPanel.isCaptured())return;
        shutterSound.run();sentenceScanPanel.showCaptured();repaint();
    }

    private void scanBook(){
        if(!loggedIn || currentDestination!=Destination.ADD_BOOK || scanPopup.isVisible())return;
        scanPopup.resetResult();addBookPanel.setReviewing(true);
        shutterSound.run();scanPopup.setVisible(true);scanPopup.requestFocusInWindow();repaint();
    }
    private void dismissScanConfirmation(){
        scanPopup.setVisible(false);addBookPanel.setReviewing(false);repaint();
    }

    private void showNotifications(){
        if(!loggedIn || currentDestination!=Destination.MENU || notificationPopup.isVisible())return;
        notificationSound.run();
        dismissMenuPopups();menuPanel.resetHover();
        notificationPopup.syncFriendRequest(demoSocial.requestResult(DemoSocial.REQUESTER_ID));
        notificationPopup.setVisible(true);
        notificationPopup.requestFocusInWindow();repaint();
    }
    private void showProfile(){
        if(!loggedIn || currentDestination!=Destination.MENU)return;
        dismissMenuPopups();menuPanel.resetHover();profilePopup.setVisible(true);
        profilePopup.requestFocusInWindow();repaint();
    }
    private void dismissMenuPopups(){
        notificationPopup.setVisible(false);profilePopup.setVisible(false);choicePopup.setVisible(false);repaint();
    }
    private void dismissChoice(){choicePopup.setVisible(false);repaint();}
    private void showRequestedSwap(){bookSwapPanel.showNewest();navigate(Destination.BOOK_SWAP);}
    private void openSwapNote(BookSwap swap){
        showReading(new ReadingPanel(swap.id(),swap.bookTitle(),swap.author(),swap.passage(),
                swap.comments(),"@sujin",this::returnToArchive));
    }
    public DemoSocial demoSocial(){return demoSocial;}
    private void logout(){
        dismissMenuPopups();dismissScanConfirmation();selectionPopup.setVisible(false);menuPanel.resetHover();
        sentenceScanPanel.reset();
        friendPanel.reset();bookSwapPanel.reset();
        notificationPopup.resetSession();loggedIn=false;currentDestination=null;
        navigation.setDestination(null);navigation.setVisible(false);
        screens.show(content,"login");repaint();
    }

    public Destination currentDestination() {return currentDestination;}

    public boolean isReading() {return readingPanel!=null;}

    public boolean isExpandedMode() {return readingPanel!=null && readingPanel.isExpandedMode();}

    public void setExpandedMode(boolean expanded) {
        if(readingPanel!=null) readingPanel.setExpandedMode(expanded);
    }

    public boolean isReadingTransitionRunning() {
        return readingTransition!=null && readingTransition.isRunning();
    }

    public void animateReadingSnap(JFrame frame,boolean expanded) {
        if(readingPanel==null || isReadingTransitionRunning())return;
        Dimension start=frame.getSize(),target=readingWindowSize(frame,expanded);
        ReadingPanel panel=readingPanel;
        boolean changingMode=expanded!=panel.isExpandedMode();
        if(!changingMode && start.equals(target))return;
        if(changingMode)panel.beginTransition(expanded);
        long began=System.nanoTime();
        readingTransition=new Timer(15,event->{
            Timer timer=(Timer)event.getSource();
            if(!frame.isDisplayable() || readingPanel!=panel) {
                timer.stop();panel.finishTransition();return;
            }
            double t=Math.min(1,(System.nanoTime()-began)/300_000_000.0);
            // Smooth acceleration/deceleration, with no abrupt start or stop.
            double eased=t*t*t*(t*(t*6-15)+10);
            frame.setSize((int)Math.round(start.width+(target.width-start.width)*eased),
                    (int)Math.round(start.height+(target.height-start.height)*eased));
            if(changingMode)panel.setTransitionProgress((float)eased);
            if(t>=1) {
                timer.stop();panel.finishTransition();
                frame.revalidate();frame.repaint();
            }
        });
        readingTransition.start();
    }

    public static boolean expandedModeForWidth(int contentWidth) {
        return expandedModeForWidth(contentWidth,ReadingPanel.PORTRAIT_WIDTH+12);
    }

    public static boolean expandedModeForWidth(int contentWidth,int threshold) {
        return contentWidth>=threshold;
    }

    public static int expandedWidthThreshold(JFrame frame) {
        Insets insets=frame.getInsets();
        int expandedWidth=readingWindowSize(frame,true).width-insets.left-insets.right;
        int portraitWidth=readingWindowSize(frame,false).width-insets.left-insets.right;
        if(expandedWidth<=portraitWidth+1) return Integer.MAX_VALUE;
        // A small deliberate horizontal resize opens the book immediately.
        return Math.min(expandedWidth,portraitWidth+12);
    }

    public boolean expandedModeAfterResize(JFrame frame) {
        Insets insets=frame.getInsets();
        int portrait=readingWindowSize(frame,false).width-insets.left-insets.right;
        int expanded=readingWindowSize(frame,true).width-insets.left-insets.right;
        return expandedModeAfterResize(frame.getContentPane().getWidth(),portrait,expanded,isExpandedMode());
    }

    public static boolean expandedModeAfterResize(int width,int portrait,int expanded,boolean wasExpanded) {
        if(expanded<=portrait+1) return false;
        // Each layout has its own trigger, so a small resize works both ways
        // without the programmatic snap immediately switching back again.
        return wasExpanded ? width>expanded-12 : width>=Math.min(expanded,portrait+12);
    }

    public static Dimension readingWindowSize(JFrame frame,boolean expanded) {
        GraphicsConfiguration display=frame.getGraphicsConfiguration();
        Rectangle bounds=display==null?new Rectangle(Toolkit.getDefaultToolkit().getScreenSize())
                :display.getBounds();
        Insets screenInsets=display==null?new Insets(0,0,0,0)
                :Toolkit.getDefaultToolkit().getScreenInsets(display);
        Insets windowInsets=frame.getInsets();
        int availableWidth=Math.max(1,bounds.width-screenInsets.left-screenInsets.right
                -windowInsets.left-windowInsets.right-48);
        int availableHeight=Math.max(1,bounds.height-screenInsets.top-screenInsets.bottom
                -windowInsets.top-windowInsets.bottom-48);
        int designWidth=expanded?ReadingPanel.EXPANDED_WIDTH:ReadingPanel.PORTRAIT_WIDTH;
        int designHeight=expanded?ReadingPanel.EXPANDED_HEIGHT:ReadingPanel.PORTRAIT_HEIGHT;
        int maxWidth=expanded?1024:ReadingPanel.PORTRAIT_WIDTH;
        int maxHeight=expanded?720:800;
        double scale=Math.min(1,Math.min((double)Math.min(maxWidth,availableWidth)/designWidth,
                (double)Math.min(maxHeight,availableHeight)/designHeight));
        int width=(int)Math.round(designWidth*scale);
        int height=(int)Math.round(designHeight*scale);
        return new Dimension(width+windowInsets.left+windowInsets.right,
                height+windowInsets.top+windowInsets.bottom);
    }

    private void showBookSelection(BookSpine book) {
        selectionPopup.setBook(book);
        selectionPopup.setVisible(true);
        selectionPopup.requestFocusInWindow();
        repaint();
    }

    private void openReading(BookSpine book) {
        showReading(new ReadingPanel(book,this::returnToArchive));
    }

    private void showReading(ReadingPanel panel) {
        Window window=SwingUtilities.getWindowAncestor(this);
        if(!(window instanceof JFrame frame)) return;
        selectionPopup.setVisible(false);
        readingFrame=frame;
        mainContentPane=frame.getContentPane();
        mainWindowSize=frame.getSize();
        mainWindowLocation=frame.getLocation();
        mainWindowResizable=frame.isResizable();
        readingPanel=panel;
        frame.setResizable(true);
        frame.setContentPane(readingPanel);
        frame.setSize(readingWindowSize(frame,false));
        frame.revalidate();
        frame.repaint();
    }

    private void returnToArchive() {
        if(readingFrame==null) return;
        JFrame frame=readingFrame;
        if(readingTransition!=null){readingTransition.stop();readingTransition=null;}
        readingPanel.finishTransition();
        frame.setContentPane(mainContentPane);
        frame.setResizable(mainWindowResizable);
        frame.setSize(mainWindowSize);
        frame.setLocation(mainWindowLocation);
        frame.revalidate();
        frame.repaint();
        readingPanel=null;
        readingFrame=null;
        mainContentPane=null;
        mainWindowSize=null;
        mainWindowLocation=null;
    }

    private void dismissBookSelection() {
        selectionPopup.setVisible(false);
        repaint();
    }

    private void onKakaoLogin() {
        // Replace with the backend authentication-success callback.
        loggedIn=true;
        navigate(Destination.ARCHIVE);
    }
}
