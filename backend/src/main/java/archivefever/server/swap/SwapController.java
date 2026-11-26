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

	/**
	 * How the user marks one sentence of a passage from now on.
	 *
	 * @param underlined left out means not underlined; likewise highlighted
	 * @param text the memo; blank or left out for none
	 */
	record NewNote(long passageId, int sentence, Boolean underlined, Boolean highlighted, String text) {
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

	/** Sets the user's underline, highlight and memo on a sentence; all off and blank removes them. */
	@PostMapping("/{id}/notes")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void mark(@PathVariable long id, @RequestBody NewNote note) {
		swaps.mark(user.id(), id, note.passageId(), note.sentence(), Boolean.TRUE.equals(note.underlined()),
				Boolean.TRUE.equals(note.highlighted()), note.text());
	}

}
