package archivefever.social;

import archivefever.api.ApiClient;
import archivefever.api.Json;
import archivefever.api.LibraryApi;
import archivefever.model.BookSpine;
import archivefever.model.BookSwap;
import archivefever.model.Friend;
import archivefever.model.Notice;
import archivefever.model.ReadingDocument;
import archivefever.model.SwapRecord;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Friends, book swaps and notifications on the server. Other users are named by their
 * handle; a {@link Friend}'s id is the handle without the "@".
 */
public final class ServerSocial implements Social {
    private static final String EMPTY_RECORD="아직 두 사람이 수집한 문장이 없어요.\n책장에서 책을 누르고 scan으로 문장을 모으면\n여기에 함께 보여요.";
    private final ApiClient client;
    private final LibraryApi library;
    /** Spines and covers by address: a picture of a book does not change, and each is a request. */
    private final Map<String,BufferedImage> pictures=new ConcurrentHashMap<>();

    public ServerSocial(ApiClient client,LibraryApi library) {this.client=client;this.library=library;}

    private interface Call<T> {T run() throws IOException;}

    /** Runs a request, putting a refusal or a failure into words the screens can show. */
    private static <T> T ask(Call<T> call) throws IOException {
        try {return call.run();}
        catch(ApiClient.Refused refused) {throw new IOException(reason(refused.code()),refused);}
        catch(IOException | RuntimeException failed) {throw new IOException("request failed",failed);}
    }

    private static String reason(String code) {
        return switch(code) {
            case "NOT_SIGNED_IN"->"please sign in again";
            case "USER_NOT_FOUND"->"user not found";
            case "SELF"->"that is you";
            case "ALREADY_FRIENDS"->"already friends";
            case "ALREADY_REQUESTED"->"request already sent";
            case "REQUEST_WAITING"->"they already asked you";
            case "NO_REQUEST"->"request not found";
            case "NOT_FRIENDS"->"not friends any more";
            case "NOT_COMMON","BOOK_MISSING"->"you both need this book";
            case "ALREADY_SWAPPING"->"already swapping this book";
            case "ALREADY_ANSWERED"->"already answered";
            case "NOT_ACTIVE","SWAP_NOT_FOUND"->"this swap is closed";
            default->"request failed";
        };
    }

    private static String encoded(String text) {return URLEncoder.encode(text,StandardCharsets.UTF_8);}

    @Override public List<Friend> friends(String query) throws IOException {
        String text=query==null?"":query.strip();
        return ask(()->{
            Object people=client.get(text.isEmpty()?"/api/friends":"/api/users?query="+encoded(text));
            List<Friend> friends=new ArrayList<>();
            for(Object person:Json.list(people))friends.add(friend(person));
            return friends;
        });
    }

    @Override public Friend requestFriend(String id) throws IOException {
        return ask(()->friend(client.post("/api/friends/"+encoded(id)+"/request")));
    }

    @Override public Friend confirmRequest(String id) throws IOException {
        return ask(()->friend(client.post("/api/friends/"+encoded(id)+"/accept")));
    }

    @Override public Friend declineRequest(String id) throws IOException {
        return ask(()->friend(client.post("/api/friends/"+encoded(id)+"/decline")));
    }

    @Override public Friend removeFriend(String id) throws IOException {
        return ask(()->friend(client.delete("/api/friends/"+encoded(id))));
    }

    @Override public List<BookSpine> library(String friendId) throws IOException {
        return ask(()->spines("/api/friends/"+encoded(friendId)+"/books"));
    }

    @Override public List<BookSpine> swappable(String friendId) throws IOException {
        return ask(()->spines("/api/swaps/candidates?friend="+encoded(friendId)));
    }

    @Override public List<BookSwap> swaps() throws IOException {
        return ask(()->{
            List<BookSwap> swaps=new ArrayList<>();
            for(Object swap:Json.list(client.get("/api/swaps")))swaps.add(swap(swap));
            return swaps;
        });
    }

    @Override public BookSwap requestSwap(String friendId,BookSpine book) throws IOException {
        return ask(()->swap(client.post("/api/swaps",
                "{\"friend\":"+Json.quote(friendId)+",\"isbn13\":"+Json.quote(book.id())+"}")));
    }

    @Override public List<Notice> notices() throws IOException {
        return ask(()->{
            List<Notice> notices=new ArrayList<>();
            for(Object notice:Json.list(client.get("/api/notifications")))notices.add(notice(notice));
            return notices;
        });
    }

    @Override public Notice answer(Notice notice,boolean accept) throws IOException {
        String answer=accept?"/accept":"/decline";
        ask(()->client.post(notice.kind()==Notice.Kind.SWAP_REQUEST?"/api/swaps/"+encoded(notice.swapId())+answer
                :"/api/friends/"+encoded(notice.userId())+answer));
        return notice.withState(accept?Notice.State.CONFIRMED:Notice.State.DECLINED);
    }

    @Override public SwapRecord record(BookSwap swap) throws IOException {
        return ask(()->record(swap.id(),swap.ended(),client.get("/api/swaps/"+encoded(swap.id())+"/records")));
    }

    /**
     * Lays both readers' passages out as one text, each under a line saying its page and
     * whose it is, and ties the memos to the sentences of that text.
     * @param kept whether this is the record of a swap that has ended, which takes no more memos
     */
    private SwapRecord record(String swapId,boolean kept,Object records) {
        String me="@"+Json.string(Json.field(records,"me"),"handle");
        StringBuilder text=new StringBuilder();
        // Where each passage's own words are in the text, to tell which passage a sentence is of.
        List<long[]> passages=new ArrayList<>();
        for(Object passage:Json.list(Json.field(records,"passages"))) {
            if(text.length()>0)text.append("\n\n");
            Object page=Json.field(passage,"page");
            text.append(page instanceof Number number?"p."+number.intValue()+" · ":"").append('@')
                    .append(Json.string(passage,"by")).append('\n');
            int start=text.length();
            text.append(Json.string(passage,"text"));
            passages.add(new long[]{((Number)Json.field(passage,"id")).longValue(),start,text.length()});
        }
        // A memo is kept by passage and by sentence within it; the page numbers sentences through the whole text.
        List<ReadingDocument.Sentence> sentences=ReadingDocument.sentencesOf(text.toString());
        long[] passageOf=new long[sentences.size()];
        int[] placeIn=new int[sentences.size()];
        Map<String,Integer> sentenceAt=new HashMap<>();
        for(int i=0;i<sentences.size();i++) {
            passageOf[i]=-1;
            for(long[] passage:passages) {
                if(sentences.get(i).start()<passage[1] || sentences.get(i).start()>=passage[2])continue;
                passageOf[i]=passage[0];
                placeIn[i]=i>0 && passageOf[i-1]==passage[0]?placeIn[i-1]+1:0;
                sentenceAt.put(passage[0]+"/"+placeIn[i],i);
            }
        }
        Map<Integer,String> mine=new HashMap<>();
        Map<Integer,List<String>> theirs=new TreeMap<>();
        for(Object note:Json.list(Json.field(records,"notes"))) {
            Integer at=sentenceAt.get(((Number)Json.field(note,"passageId")).longValue()+"/"+Json.integer(note,"sentence"));
            String words=Json.string(note,"text");
            // A memo whose sentence cannot be found any more is still shown, below the others.
            if(Boolean.TRUE.equals(Json.field(note,"mine")) && at!=null)mine.put(at,words);
            else theirs.computeIfAbsent(at==null?Integer.MAX_VALUE:at,key->new ArrayList<>())
                    .add("@"+Json.string(note,"by")+" : "+words);
        }
        List<String> comments=new ArrayList<>();
        theirs.values().forEach(comments::addAll);
        ReadingDocument.NoteStore store=kept?Social.keptRecord():(sentence,words)->{
            if(passageOf[sentence]<0)throw new IOException("no passage here");
            ask(()->{
                client.postForNothing("/api/swaps/"+encoded(swapId)+"/notes","{\"passageId\":"+passageOf[sentence]
                        +",\"sentence\":"+placeIn[sentence]+",\"text\":"+Json.quote(words)+"}");
                return null;
            });
        };
        return new SwapRecord(new ReadingDocument("swap-"+swapId,text.toString(),mine,store),comments,me,EMPTY_RECORD);
    }

    private List<BookSpine> spines(String path) throws IOException {
        List<BookSpine> spines=new ArrayList<>();
        for(LibraryApi.ShelfBook book:library.shelf(path))
            spines.add(new BookSpine(book.isbn13(),book.title(),picture(book.spineUrl()),
                    Math.max(1,Math.min(355,book.spineWidth()))));
        return spines;
    }

    private BufferedImage picture(String path) throws IOException {
        BufferedImage known=pictures.get(path);
        if(known!=null)return known;
        BufferedImage loaded=library.image(path);
        pictures.put(path,loaded);
        return loaded;
    }

    private static Friend friend(Object person) {
        String handle=Json.string(person,"handle");
        return new Friend(handle,Json.string(person,"name"),"@"+handle,Friend.Status.valueOf(Json.string(person,"status")));
    }

    private BookSwap swap(Object swap) throws IOException {
        Object partner=Json.field(swap,"partner"),book=Json.field(swap,"book");
        String handle=Json.string(partner,"handle");
        return new BookSwap(Long.toString(((Number)Json.field(swap,"id")).longValue()),handle,"@"+handle,
                Json.string(book,"title"),Json.string(book,"author"),picture(Json.string(book,"coverUrl")),
                switch(Json.string(swap,"status")) {
                    case "PENDING"->BookSwap.Stage.WAITING;
                    case "ENDED"->BookSwap.Stage.ENDED;
                    default->BookSwap.Stage.READING;
                });
    }

    private static Notice notice(Object notice) {
        Object from=Json.field(notice,"from"),book=Json.field(notice,"book"),swap=Json.field(notice,"swapId");
        String handle=Json.string(from,"handle"),state=Json.string(notice,"state");
        return new Notice(Long.toString(((Number)Json.field(notice,"id")).longValue()),
                Notice.Kind.valueOf(Json.string(notice,"kind")),handle,"@"+handle,
                book==null?"":Json.string(book,"title"),
                swap instanceof Number number?Long.toString(number.longValue()):null,
                state==null?Notice.State.NONE:switch(state) {
                    case "ACCEPTED"->Notice.State.CONFIRMED;
                    case "DECLINED"->Notice.State.DECLINED;
                    default->Notice.State.PENDING;
                });
    }
}
