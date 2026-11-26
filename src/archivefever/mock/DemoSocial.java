package archivefever.mock;

import archivefever.model.BookSpine;
import archivefever.model.BookSwap;
import archivefever.model.Friend;
import archivefever.model.Notice;
import archivefever.model.ReadingDocument;
import archivefever.model.SwapRecord;
import archivefever.social.Social;
import archivefever.ui.theme.AppTheme;
import java.io.IOException;
import java.util.*;

/** Development-only in-memory data, used while the UI runs without a server. */
public final class DemoSocial implements Social {
    /** The demo user whose friend request appears in the notification popup. */
    public static final String REQUESTER_ID="yeyeon";
    /** The demo friend whose book swap request appears in the notification popup. */
    public static final String SWAP_REQUESTER_ID="seominji";
    private static final String DEV_PASSAGE="개발용 본문입니다. 실제 책 내용은 서버 연동 후 표시됩니다. 필기구로 이 문장에 밑줄과 메모를 남겨 보세요.";
    private static final String ME="@sujin";
    private final long latencyMillis;
    private final Map<String,Friend> friends=new LinkedHashMap<>();
    private final Map<String,List<BookSpine>> libraries=new HashMap<>();
    private final Map<String,BookSwap> swaps=new LinkedHashMap<>();
    private final Map<String,List<String>> comments=new HashMap<>();
    private final Map<String,String> requestResults=new HashMap<>();
    /** Oldest first; shown newest first. */
    private final List<Notice> notices=new ArrayList<>();
    /** The book of the demo swap request, or null when the demo has too few books for one. */
    private final BookSpine requestedBook;
    private boolean failNext;

    public DemoSocial(long latencyMillis,List<BookSpine> books) {
        this.latencyMillis=latencyMillis;
        addDemoFriend("kimyeyeon","@jinlims2",Friend.Status.ADDED,slice(books,0,3));
        addDemoFriend(SWAP_REQUESTER_ID,"@mminzza",Friend.Status.ADDED,slice(books,3,6));
        addDemoFriend("munsujin","@sujin05",Friend.Status.ADDED,slice(books,1,4));
        addDemoFriend("parkgwanjun","@gwanjun02",Friend.Status.ADDED,List.of());
        addDemoFriend("leehaneul","@haneul",Friend.Status.NONE,slice(books,0,2));
        addDemoFriend("choidohyun","@dohyun",Friend.Status.NONE,List.of());
        addDemoFriend(REQUESTER_ID,"@yeyeon",Friend.Status.INCOMING,slice(books,2,5));
        addDemoSwap(new BookSwap("swap-archive-fever","kimyeyeon","@jinlim","Archive Fever",
                "jacques Derrda",AppTheme.image("images/social/cover-archive-fever.png"),BookSwap.Stage.READING),List.of());
        addDemoSwap(new BookSwap("swap-human-acts","munsujin","@sujin","소년이 온다","한강",
                AppTheme.image("images/social/cover-human-acts.png"),BookSwap.Stage.READING),
                List.of("@jinlim : 마침표만으로 작가의 의도를 선명하게 표현할 수 있다는게 인상깊었어."));
        addDemoSwap(new BookSwap("swap-stranger",SWAP_REQUESTER_ID,"@mminzza","이방인","알베르 카뮈",
                AppTheme.image("images/social/cover-stranger.png"),BookSwap.Stage.READING),List.of());
        requestedBook=books.size()>4?books.get(4):null;
        if(requestedBook!=null)notices.add(new Notice("notice-swap",Notice.Kind.SWAP_REQUEST,SWAP_REQUESTER_ID,"@mminzza",
                requestedBook.title(),requestedSwapId(),Notice.State.PENDING));
        notices.add(new Notice("notice-friend",Notice.Kind.FRIEND_REQUEST,REQUESTER_ID,"@yeyeon","",null,Notice.State.PENDING));
    }

    private static List<BookSpine> slice(List<BookSpine> books,int from,int to) {
        return books.subList(Math.min(from,books.size()),Math.min(to,books.size()));
    }

    private void addDemoFriend(String id,String handle,Friend.Status status,List<BookSpine> books) {
        friends.put(id,new Friend(id,id,handle,status));
        libraries.put(id,List.copyOf(books));
    }

    private void addDemoSwap(BookSwap swap,List<String> notes) {
        swaps.put(swap.id(),swap);comments.put(swap.id(),notes);
    }

    private String requestedSwapId() {return "swap-"+SWAP_REQUESTER_ID+"-"+requestedBook.id();}

    public synchronized void failNextRequest() {failNext=true;}

    @Override public List<Friend> friends(String query) throws IOException {
        pause();
        String keyword=query==null?"":query.strip().toLowerCase(Locale.ROOT);
        synchronized(this) {
            checkFailure();
            return friends.values().stream().filter(friend->keyword.isEmpty()?friend.added()
                    :friend.name().contains(keyword) || friend.handle().toLowerCase(Locale.ROOT).contains(keyword)).toList();
        }
    }

    @Override public Friend requestFriend(String id) throws IOException {
        return changeStatus(id,Friend.Status.NONE,Friend.Status.REQUESTED,"request already sent",null);
    }

    @Override public Friend confirmRequest(String id) throws IOException {
        return changeStatus(id,Friend.Status.INCOMING,Friend.Status.ADDED,"request not found","Confirmed");
    }

    @Override public Friend declineRequest(String id) throws IOException {
        return changeStatus(id,Friend.Status.INCOMING,Friend.Status.NONE,"request not found","Declined");
    }

    /** Null while the incoming request is unanswered. */
    public synchronized String requestResult(String id) {return requestResults.get(id);}

    /** Stands in for the other user approving, since the demo has only one signed-in account. */
    public synchronized void approveRequestForDemo(String id) {
        Friend friend=friends.get(id);
        if(friend==null || friend.status()!=Friend.Status.REQUESTED)return;
        friends.put(id,friend.withStatus(Friend.Status.ADDED));
        notices.add(new Notice("notice-added-"+id,Notice.Kind.FRIEND_ACCEPTED,id,friend.handle(),"",null,Notice.State.NONE));
    }

    /** Stands in for the friend accepting a swap the user asked for. */
    public synchronized void approveSwapForDemo(String id) {
        BookSwap swap=swaps.get(id);
        if(swap==null || !swap.waiting())return;
        swaps.put(id,swap.withStage(BookSwap.Stage.READING));
        notices.add(new Notice("notice-accepted-"+id,Notice.Kind.SWAP_ACCEPTED,swap.friendId(),swap.friendHandle(),
                swap.bookTitle(),id,Notice.State.NONE));
    }

    @Override public Friend removeFriend(String id) throws IOException {
        pause();
        synchronized(this) {
            checkFailure();
            Friend removed=requireFriend(id).withStatus(Friend.Status.NONE);
            friends.put(id,removed);
            // As on the server: a swap still waiting for them is off, and one being read with
            // them is over, its record kept to read.
            swaps.values().removeIf(swap->swap.friendId().equals(id) && swap.waiting());
            swaps.replaceAll((key,swap)->swap.friendId().equals(id)?swap.withStage(BookSwap.Stage.ENDED):swap);
            return removed;
        }
    }

    private Friend changeStatus(String id,Friend.Status expected,Friend.Status next,String error,String result) throws IOException {
        pause();
        synchronized(this) {
            checkFailure();
            Friend friend=requireFriend(id);
            if(friend.status()!=expected)throw new IOException(error);
            Friend changed=friend.withStatus(next);
            friends.put(id,changed);
            if(result!=null)requestResults.put(id,result);
            return changed;
        }
    }

    @Override public List<BookSpine> library(String friendId) throws IOException {
        pause();
        synchronized(this) {
            checkFailure();
            requireFriend(friendId);
            return libraries.get(friendId);
        }
    }

    /** The demo user has every book a friend has, so the friend's whole shelf can be asked for. */
    @Override public List<BookSpine> swappable(String friendId) throws IOException {return library(friendId);}

    @Override public List<BookSwap> swaps() throws IOException {
        pause();
        synchronized(this) {
            checkFailure();
            return List.copyOf(swaps.values());
        }
    }

    /** The swap waits until {@link #approveSwapForDemo} stands in for the friend accepting it. */
    @Override public BookSwap requestSwap(String friendId,BookSpine book) throws IOException {
        pause();
        synchronized(this) {
            checkFailure();
            Friend friend=requireFriend(friendId);
            String id="swap-"+friendId+"-"+book.id();
            if(swaps.containsKey(id))throw new IOException("already swapping this book");
            BookSwap swap=new BookSwap(id,friendId,friend.handle(),book.title(),"",book.image(),BookSwap.Stage.WAITING);
            addDemoSwap(swap,List.of());
            return swap;
        }
    }

    @Override public SwapRecord record(BookSwap swap) throws IOException {
        pause();
        synchronized(this) {
            checkFailure();
            BookSwap current=swaps.get(swap.id());
            if(current==null || current.waiting())throw new IOException("this swap is closed");
            // The memos of a demo record stay on this device; one kept from an ended swap takes no more.
            return new SwapRecord(new ReadingDocument(swap.id(),DEV_PASSAGE,null,current.ended()?Social.keptRecord():null),
                    comments.get(swap.id()),ME,"");
        }
    }

    @Override public List<Notice> notices() throws IOException {
        pause();
        synchronized(this) {
            checkFailure();
            List<Notice> latest=new ArrayList<>();
            // The friend request can also be answered on the friend screen; show what was answered there.
            for(Notice notice:notices)latest.add(0,notice.kind()==Notice.Kind.FRIEND_REQUEST?notice.withState(
                    answered(requestResults.get(notice.userId()))):notice);
            return latest;
        }
    }

    private static Notice.State answered(String result) {
        return result==null?Notice.State.PENDING:result.equals("Confirmed")?Notice.State.CONFIRMED:Notice.State.DECLINED;
    }

    @Override public Notice answer(Notice notice,boolean accept) throws IOException {
        if(notice.kind()==Notice.Kind.FRIEND_REQUEST) {
            if(accept)confirmRequest(notice.userId());else declineRequest(notice.userId());
            return notice.withState(accept?Notice.State.CONFIRMED:Notice.State.DECLINED);
        }
        pause();
        synchronized(this) {
            checkFailure();
            int at=notices.indexOf(notice);
            if(at<0 || notice.state()!=Notice.State.PENDING)throw new IOException("already answered");
            Notice answered=notice.withState(accept?Notice.State.CONFIRMED:Notice.State.DECLINED);
            notices.set(at,answered);
            if(accept)addDemoSwap(new BookSwap(notice.swapId(),notice.userId(),notice.handle(),notice.book(),"",
                    requestedBook.image(),BookSwap.Stage.READING),List.of());
            return answered;
        }
    }

    private Friend requireFriend(String id) throws IOException {
        Friend friend=friends.get(id);
        if(friend==null)throw new IOException("friend not found");
        return friend;
    }

    private void checkFailure() throws IOException {
        if(failNext) {failNext=false;throw new IOException("request failed");}
    }

    private void pause() throws IOException {
        try {Thread.sleep(latencyMillis);}
        catch(InterruptedException e) {Thread.currentThread().interrupt();throw new IOException("request canceled",e);}
    }
}
