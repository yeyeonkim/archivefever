package archivefever.server.friend;

/** Two users are no longer friends: whatever they shared as friends stops being shared. */
public record FriendshipEnded(String userId, String otherId) {
}
