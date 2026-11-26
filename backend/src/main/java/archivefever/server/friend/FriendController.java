package archivefever.server.friend;

import archivefever.server.library.LibraryService;
import archivefever.server.user.CurrentUser;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Other users are named by their handle here, never by their id. */
@RestController
class FriendController {

	private final FriendService friends;

	private final LibraryService library;

	private final CurrentUser user;

	FriendController(FriendService friends, LibraryService library, CurrentUser user) {
		this.friends = friends;
		this.library = library;
		this.user = user;
	}

	/** Looks for people to befriend by name or handle. */
	@GetMapping("/api/users")
	List<FriendService.Person> search(@RequestParam(defaultValue = "") String query) {
		return friends.search(user.id(), query);
	}

	@GetMapping("/api/friends")
	List<FriendService.Person> friends() {
		return friends.friends(user.id());
	}

	@PostMapping("/api/friends/{handle}/request")
	FriendService.Person request(@PathVariable String handle) {
		return friends.request(user.id(), handle);
	}

	@PostMapping("/api/friends/{handle}/accept")
	FriendService.Person accept(@PathVariable String handle) {
		return friends.accept(user.id(), handle);
	}

	@PostMapping("/api/friends/{handle}/decline")
	FriendService.Person decline(@PathVariable String handle) {
		return friends.decline(user.id(), handle);
	}

	@DeleteMapping("/api/friends/{handle}")
	FriendService.Person remove(@PathVariable String handle) {
		return friends.remove(user.id(), handle);
	}

	/** A friend's shelf. */
	@GetMapping("/api/friends/{handle}/books")
	List<LibraryService.ShelfBook> books(@PathVariable String handle) {
		return library.list(friends.requireFriend(user.id(), handle).id());
	}

}
