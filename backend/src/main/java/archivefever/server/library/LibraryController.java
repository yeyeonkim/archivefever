package archivefever.server.library;

import archivefever.server.user.CurrentUser;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class LibraryController {

	private final LibraryService library;

	private final CurrentUser user;

	LibraryController(LibraryService library, CurrentUser user) {
		this.library = library;
		this.user = user;
	}

	@GetMapping("/api/library/books")
	List<LibraryService.ShelfBook> books() {
		return library.list(user.id());
	}

}
