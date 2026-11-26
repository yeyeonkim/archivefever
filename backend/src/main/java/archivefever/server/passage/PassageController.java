package archivefever.server.passage;

import archivefever.server.user.CurrentUser;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/library/books/{isbn13}/passages")
class PassageController {

	/** @param page optional; the page of the book the text was collected from */
	record NewPassage(Integer page, String text) {
	}

	/**
	 * How the reader marks one sentence of a passage from now on.
	 *
	 * @param sentence which sentence of the passage, counting from 0
	 * @param underlined left out means not underlined; likewise highlighted
	 * @param note the memo; blank or left out for none
	 */
	record NewMark(int sentence, Boolean underlined, Boolean highlighted, String note) {
	}

	private final PassageService passages;

	private final CurrentUser user;

	PassageController(PassageService passages, CurrentUser user) {
		this.passages = passages;
		this.user = user;
	}

	@GetMapping
	List<PassageService.PassageView> list(@PathVariable String isbn13) {
		return passages.list(user.id(), isbn13);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	PassageService.PassageView add(@PathVariable String isbn13, @RequestBody NewPassage passage) {
		return passages.add(user.id(), isbn13, passage.page(), passage.text());
	}

	/** Rewrites the text and the page of a passage; a page left out takes the page away. */
	@PutMapping("/{id}")
	PassageService.PassageView update(@PathVariable String isbn13, @PathVariable long id, @RequestBody NewPassage passage) {
		return passages.update(user.id(), isbn13, id, passage.page(), passage.text());
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void delete(@PathVariable String isbn13, @PathVariable long id) {
		passages.delete(user.id(), isbn13, id);
	}

	/** Sets the reader's underline, highlight and memo on a sentence; all off and blank removes them. */
	@PostMapping("/{id}/marks")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void mark(@PathVariable String isbn13, @PathVariable long id, @RequestBody NewMark mark) {
		passages.markOwn(user.id(), isbn13, id, mark.sentence(), Boolean.TRUE.equals(mark.underlined()),
				Boolean.TRUE.equals(mark.highlighted()), mark.note());
	}

}
