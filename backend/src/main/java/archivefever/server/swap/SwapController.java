package archivefever.server.swap;

import archivefever.server.library.LibraryService;
import archivefever.server.user.CurrentUser;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/swaps")
class SwapController {

	/** @param friend the friend's handle */
	record NewSwap(String friend, String isbn13) {
	}

	/** @param text blank to take the memo away */
	record NewNote(long passageId, int sentence, String text) {
	}

	private final SwapService swaps;

	private final CurrentUser user;

	SwapController(SwapService swaps, CurrentUser user) {
		this.swaps = swaps;
		this.user = user;
	}

	@GetMapping
	List<SwapService.SwapView> list() {
		return swaps.list(user.id());
	}

	/** The books a swap with this friend can be asked for: those both have. */
	@GetMapping("/candidates")
	List<LibraryService.ShelfBook> candidates(@RequestParam String friend) {
		return swaps.candidates(user.id(), friend);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	SwapService.SwapView request(@RequestBody NewSwap swap) {
		return swaps.request(user.id(), swap.friend(), swap.isbn13());
	}

	@PostMapping("/{id}/accept")
	SwapService.SwapView accept(@PathVariable long id) {
		return swaps.accept(user.id(), id);
	}

	@PostMapping("/{id}/decline")
	SwapService.SwapView decline(@PathVariable long id) {
		return swaps.decline(user.id(), id);
	}

	@GetMapping("/{id}/records")
	SwapService.Records records(@PathVariable long id) {
		return swaps.records(user.id(), id);
	}

	/** Writes, rewrites or (with blank text) removes the user's memo on a sentence. */
	@PostMapping("/{id}/notes")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void writeNote(@PathVariable long id, @RequestBody NewNote note) {
		swaps.writeNote(user.id(), id, note.passageId(), note.sentence(), note.text());
	}

}
