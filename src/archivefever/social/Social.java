package archivefever.social;

import archivefever.model.BookSpine;
import archivefever.model.BookSwap;
import archivefever.model.Friend;
import archivefever.model.Notice;
import archivefever.model.ReadingDocument;
import archivefever.model.SwapRecord;
import java.io.IOException;
import java.util.List;

/**
 * Friends, book swaps and notifications, wherever they are kept: on the server, or in
 * memory when the UI runs on demo data. Every call blocks, so call it off the event
 * thread; a failure's message is short enough to show as it is.
 */
public interface Social {
    /** The user's friends when the query is blank; otherwise the users the query finds. */
    List<Friend> friends(String query) throws IOException;

    Friend requestFriend(String id) throws IOException;

    Friend confirmRequest(String id) throws IOException;

    Friend declineRequest(String id) throws IOException;

    Friend removeFriend(String id) throws IOException;

    /** A friend's shelf. */
    List<BookSpine> library(String friendId) throws IOException;

    /** The friend's books a swap can be asked for: those the user has too. */
    List<BookSpine> swappable(String friendId) throws IOException;

    /**
     * The swaps the user is reading, those they asked for that still wait for an answer, and
     * those that ended when a friendship did, whose records are kept.
     */
    List<BookSwap> swaps() throws IOException;

    BookSwap requestSwap(String friendId,BookSpine book) throws IOException;

    /**
     * What the reading page of a swap shows: as it is now for one being read, and as it
     * stood when the friendship ended for one that is over. A waiting swap has none.
     */
    SwapRecord record(BookSwap swap) throws IOException;

    /** Newest first. */
    List<Notice> notices() throws IOException;

    /** Accepts or declines the request a notice is about; returns the notice as it reads afterwards. */
    Notice answer(Notice notice,boolean accept) throws IOException;

    /**
     * The note store of a record kept from a swap that ended with the friendship: the
     * record can be read, and takes no more memos.
     */
    static ReadingDocument.NoteStore keptRecord() {
        return new ReadingDocument.NoteStore() {
            @Override public void save(int sentence,String note) throws IOException {
                throw new IOException("this swap has ended");
            }
            @Override public String closed() {return "끝난 교환독서의 기록이에요. 메모는 더 쓸 수 없어요.";}
        };
    }
}
