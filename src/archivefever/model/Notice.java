package archivefever.model;

/**
 * A line of the notification list: a request someone sent the user, or news of how a
 * request of the user's was answered.
 * @param userId how the other user is named in requests to answer them
 * @param handle the other user as shown, with the "@"
 * @param book title of the book of a swap; empty for friend requests
 * @param swapId the swap to accept or decline; null for friend requests
 */
public record Notice(String id,Kind kind,String userId,String handle,String book,String swapId,State state) {
    public enum Kind {FRIEND_REQUEST,FRIEND_ACCEPTED,FRIEND_DECLINED,SWAP_REQUEST,SWAP_ACCEPTED,SWAP_DECLINED}

    /** Of a request sent to the user: waiting for their answer, or how they answered. NONE for news. */
    public enum State {NONE,PENDING,CONFIRMED,DECLINED}

    /** Whether this is something for the user to answer, now or earlier. */
    public boolean isRequest() {return kind==Kind.FRIEND_REQUEST || kind==Kind.SWAP_REQUEST;}

    public Notice withState(State next) {return new Notice(id,kind,userId,handle,book,swapId,next);}

    /** What the line says after the handle. */
    public String message() {
        return switch(kind) {
            case FRIEND_REQUEST->" sent you a friend request.";
            case FRIEND_ACCEPTED->" accepted your friend request.";
            case FRIEND_DECLINED->" declined your friend request.";
            case SWAP_REQUEST->" sent you a Book swap request.";
            case SWAP_ACCEPTED->" accepted your Book swap request.";
            case SWAP_DECLINED->" declined your Book swap request.";
        };
    }
}
