package archivefever.mock;

import archivefever.model.BookSpine;
import archivefever.model.BookSwap;
import archivefever.model.Friend;
import archivefever.ui.theme.AppTheme;
import java.io.IOException;
import java.util.*;

/** Development-only in-memory data. Replace these calls when the REST API exists. */
public final class DemoSocial {
    /** The demo user whose friend request appears in the notification popup. */
    public static final String REQUESTER_ID="yeyeon";
    private static final String DEV_PASSAGE="개발용 본문입니다. 실제 책 내용은 서버 연동 후 표시됩니다. 필기구로 이 문장에 밑줄과 메모를 남겨 보세요.";
    private final long latencyMillis;
    private final Map<String,Friend> friends=new LinkedHashMap<>();
    private final Map<String,List<BookSpine>> libraries=new HashMap<>();
    private final Map<String,BookSwap> swaps=new LinkedHashMap<>();
    private final Map<String,String> requestResults=new HashMap<>();
    private boolean failNext;

    public DemoSocial(long latencyMillis,List<BookSpine> books) {
        this.latencyMillis=latencyMillis;
        addDemoFriend("kimyeyeon","@jinlims2",Friend.Status.ADDED,slice(books,0,3));
        addDemoFriend("seominji","@mminzza",Friend.Status.ADDED,slice(books,3,6));
        addDemoFriend("munsujin","@sujin05",Friend.Status.ADDED,slice(books,1,4));
        addDemoFriend("parkgwanjun","@gwanjun02",Friend.Status.ADDED,List.of());
        addDemoFriend("leehaneul","@haneul",Friend.Status.NONE,slice(books,0,2));
        addDemoFriend("choidohyun","@dohyun",Friend.Status.NONE,List.of());
        addDemoFriend(REQUESTER_ID,"@yeyeon",Friend.Status.INCOMING,slice(books,2,5));
        swaps.put("swap-archive-fever",new BookSwap("swap-archive-fever","kimyeyeon","@jinlim","Archive Fever",
                "jacques Derrda",AppTheme.image("images/social/cover-archive-fever.png"),DEV_PASSAGE,List.of()));
        swaps.put("swap-human-acts",new BookSwap("swap-human-acts","munsujin","@sujin","소년이 온다","한강",
                AppTheme.image("images/social/cover-human-acts.png"),DEV_PASSAGE,
                List.of("@jinlim : 마침표만으로 작가의 의도를 선명하게 표현할 수 있다는게 인상깊었어.")));
        swaps.put("swap-stranger",new BookSwap("swap-stranger","seominji","@mminzza","이방인","알베르 카뮈",
                AppTheme.image("images/social/cover-stranger.png"),DEV_PASSAGE,List.of()));
    }

    private static List<BookSpine> slice(List<BookSpine> books,int from,int to) {
        return books.subList(Math.min(from,books.size()),Math.min(to,books.size()));
    }

    private void addDemoFriend(String id,String handle,Friend.Status status,List<BookSpine> books) {
        friends.put(id,new Friend(id,id,handle,status));
        libraries.put(id,List.copyOf(books));
    }

    public synchronized void failNextRequest() {failNext=true;}

    public List<Friend> friends(String query) throws IOException {
        pause();
        String keyword=query==null?"":query.strip().toLowerCase(Locale.ROOT);
        synchronized(this) {
            checkFailure();
            return friends.values().stream().filter(friend->keyword.isEmpty()?friend.added()
                    :friend.name().contains(keyword) || friend.handle().toLowerCase(Locale.ROOT).contains(keyword)).toList();
        }
    }

    public Friend requestFriend(String id) throws IOException {
        return changeStatus(id,Friend.Status.NONE,Friend.Status.REQUESTED,"request already sent",null);
    }

    public Friend confirmRequest(String id) throws IOException {
        return changeStatus(id,Friend.Status.INCOMING,Friend.Status.ADDED,"request not found","Confirmed");
    }

    public Friend declineRequest(String id) throws IOException {
        return changeStatus(id,Friend.Status.INCOMING,Friend.Status.NONE,"request not found","Declined");
    }

    /** Null while the incoming request is unanswered. */
    public synchronized String requestResult(String id) {return requestResults.get(id);}

    /** Stands in for the other user approving, since the demo has only one signed-in account. */
    public synchronized void approveRequestForDemo(String id) {
        Friend friend=friends.get(id);
        if(friend!=null && friend.status()==Friend.Status.REQUESTED)friends.put(id,friend.withStatus(Friend.Status.ADDED));
    }

    public Friend removeFriend(String id) throws IOException {
        pause();
        synchronized(this) {
            checkFailure();
            Friend removed=requireFriend(id).withStatus(Friend.Status.NONE);
            friends.put(id,removed);
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

    public List<BookSpine> library(String friendId) throws IOException {
        pause();
        synchronized(this) {
            checkFailure();
            requireFriend(friendId);
            return libraries.get(friendId);
        }
    }

    public List<BookSwap> swaps() throws IOException {
        pause();
        synchronized(this) {
            checkFailure();
            return List.copyOf(swaps.values());
        }
    }

    public BookSwap requestSwap(String friendId,BookSpine book) throws IOException {
        pause();
        synchronized(this) {
            checkFailure();
            Friend friend=requireFriend(friendId);
            String id="swap-"+friendId+"-"+book.id();
            if(swaps.containsKey(id))throw new IOException("already swapping this book");
            BookSwap swap=new BookSwap(id,friendId,friend.handle(),book.title(),"",book.image(),DEV_PASSAGE,List.of());
            swaps.put(id,swap);
            return swap;
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
