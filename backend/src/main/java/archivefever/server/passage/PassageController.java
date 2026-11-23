package archivefever.server.passage;

import archivefever.server.user.CurrentUser;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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

}
