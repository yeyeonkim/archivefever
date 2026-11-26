package archivefever.server.library;

import archivefever.server.user.CurrentUser;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
class LibraryController {

	record NewBook(String isbn13) {
	}

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

	/** Puts a book found by searching on the shelf. A scanned book gets there by confirming its scan. */
	@PostMapping("/api/library/books")
	@ResponseStatus(HttpStatus.CREATED)
	LibraryService.ShelfBook add(@RequestBody NewBook book) {
		return library.addByIsbn(user.id(), book.isbn13());
	}

	@DeleteMapping("/api/library/books/{isbn13}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void remove(@PathVariable String isbn13) {
		library.remove(user.id(), isbn13);
	}

	/** Looks for books to add by words of their title or author. */
	@GetMapping("/api/books")
	List<LibraryService.FoundBook> search(@RequestParam(defaultValue = "") String query) {
		return library.search(user.id(), query);
	}

	/** One book, with its cover, for the user to confirm before adding it. */
	@GetMapping("/api/books/{isbn13}")
	LibraryService.BookCard book(@PathVariable String isbn13) {
		return library.card(isbn13);
	}

}
