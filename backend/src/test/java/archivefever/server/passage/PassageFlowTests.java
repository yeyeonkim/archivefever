package archivefever.server.passage;

import static org.assertj.core.api.Assertions.assertThat;

import archivefever.server.auth.SignedIn;
import archivefever.server.book.BookService;
import archivefever.server.library.LibraryService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;

/** Collecting sentences from a book on the shelf, correcting and marking them, over HTTP. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class PassageFlowTests {

	private static final ParameterizedTypeReference<Map<String, Object>> OBJECT = new ParameterizedTypeReference<>() {
	};

	private static final ParameterizedTypeReference<List<Map<String, Object>>> LIST = new ParameterizedTypeReference<>() {
	};

	@Value("${local.server.port}")
	private int port;

	@Autowired
	private BookService books;

	@Autowired
	private LibraryService library;

	private RestClient client;

	@BeforeEach
	void createClient() {
		// Signed in as the user the shelf in these tests belongs to.
		client = SignedIn.client(port, "demo");
	}

	@Test
	void collectedPassagesComeBackInReadingOrder() {
		String isbn = shelved("9791100000106");

		ResponseEntity<Map<String, Object>> saved = add(isbn, "{\"page\":120,\"text\":\"뒤쪽 문장.\"}");
		assertThat(saved.getStatusCode().value()).isEqualTo(201);
		assertThat(saved.getBody()).containsEntry("page", 120).containsEntry("text", "뒤쪽 문장.").containsKey("id");
		add(isbn, "{\"text\":\"쪽수 없는 문장.\"}");
		add(isbn, "{\"page\":7,\"text\":\"앞쪽 문장.\"}");
		add(isbn, "{\"page\":120,\"text\":\"같은 쪽의 다음 문장.\"}");

		List<Map<String, Object>> passages = list(isbn).getBody();
		assertThat(passages).extracting(passage -> passage.get("text"))
				.containsExactly("앞쪽 문장.", "뒤쪽 문장.", "같은 쪽의 다음 문장.", "쪽수 없는 문장.");
		assertThat(passages).extracting(passage -> passage.get("page")).containsExactly(7, 120, 120, null);
	}

	@Test
	void recognisedTextIsStoredAsOneTidyParagraph() {
		String isbn = shelved("9791100000113");

		assertThat(add(isbn, "{\"page\":3,\"text\":\"  첫 줄\\n둘째   줄\\r\\n셋째 줄  \"}").getBody())
				.containsEntry("text", "첫 줄 둘째 줄 셋째 줄");
	}

	@Test
	void passagesBelongToOneBook() {
		String first = shelved("9791100000120");
		String second = shelved("9791100000137");

		add(first, "{\"page\":1,\"text\":\"첫 책의 문장.\"}");

		assertThat(list(first).getBody()).hasSize(1);
		assertThat(list(second).getBody()).isEmpty();
	}

	@Test
	void aPassageNeedsTextAndASensiblePage() {
		String isbn = shelved("9791100000144");

		assertThat(add(isbn, "{\"page\":1,\"text\":\"   \"}").getStatusCode().value()).isEqualTo(400);
		assertThat(add(isbn, "{\"page\":1}").getStatusCode().value()).isEqualTo(400);
		assertThat(add(isbn, "{\"page\":0,\"text\":\"문장.\"}").getStatusCode().value()).isEqualTo(400);
		assertThat(add(isbn, "{\"page\":10000,\"text\":\"문장.\"}").getStatusCode().value()).isEqualTo(400);
		assertThat(add(isbn, "{\"page\":1,\"text\":\"" + "가".repeat(PassageService.MAX_LENGTH + 1) + "\"}")
				.getStatusCode().value()).isEqualTo(400);
		assertThat(add(isbn, "not json").getStatusCode().value()).isEqualTo(400);
		assertThat(list(isbn).getBody()).isEmpty();

		assertThat(add(isbn, "{\"page\":9999,\"text\":\"" + "가".repeat(PassageService.MAX_LENGTH) + "\"}")
				.getStatusCode().value()).isEqualTo(201);
	}

	@Test
	void aBookThatIsNotOnTheShelfHasNoPassages() {
		String isbn = "9791100000151";
		// Known to the catalogue, but never added to the shelf.
		assertThat(books.find(isbn)).isPresent();

		assertThat(add(isbn, "{\"page\":1,\"text\":\"문장.\"}").getStatusCode().value()).isEqualTo(404);
		assertThat(client.get().uri("/api/library/books/{isbn}/passages", isbn).retrieve().toBodilessEntity()
				.getStatusCode().value()).isEqualTo(404);
	}

	@Test
	void aPassageCanBeCorrectedAndMovedToAnotherPage() {
		String isbn = shelved("9791100000168");
		long first = id(add(isbn, "{\"page\":10,\"text\":\"잘못 읽은 문장.\"}"));
		long second = id(add(isbn, "{\"page\":20,\"text\":\"그대로 둘 문장.\"}"));
		mark(isbn, first, "{\"sentence\":0,\"underlined\":true,\"note\":\"고치기 전에 쓴 메모\"}");

		ResponseEntity<Map<String, Object>> corrected = update(isbn, first, "{\"page\":30,\"text\":\"  바로잡은\\n문장.  \"}");

		assertThat(corrected.getStatusCode().value()).isEqualTo(200);
		assertThat(corrected.getBody()).containsEntry("id", (int) first).containsEntry("page", 30).containsEntry("text", "바로잡은 문장.");
		// Its marks stay with it, and the new page puts it after the other passage.
		List<Map<String, Object>> passages = list(isbn).getBody();
		assertThat(passages).extracting(passage -> passage.get("text")).containsExactly("그대로 둘 문장.", "바로잡은 문장.");
		assertThat(passages.get(1).get("marks")).isEqualTo(List.of(Map.of("sentence", 0, "underlined", true,
				"highlighted", false, "note", "고치기 전에 쓴 메모")));
		// A page left out takes the page away.
		assertThat(update(isbn, first, "{\"text\":\"쪽수를 뺀 문장.\"}").getBody()).containsEntry("page", null);

		// What a new passage may not be, a corrected one may not be either; a refused correction changes nothing.
		assertThat(update(isbn, second, "{\"page\":1,\"text\":\"  \"}").getStatusCode().value()).isEqualTo(400);
		assertThat(update(isbn, second, "{\"page\":0,\"text\":\"문장.\"}").getStatusCode().value()).isEqualTo(400);
		assertThat(update(isbn, second, "{\"page\":1,\"text\":\"" + "가".repeat(PassageService.MAX_LENGTH + 1) + "\"}")
				.getStatusCode().value()).isEqualTo(400);
		assertThat(list(isbn).getBody()).extracting(passage -> passage.get("text")).contains("그대로 둘 문장.");
	}

	@Test
	void aDeletedPassageTakesItsMarksWithIt() {
		String isbn = shelved("9791100000175");
		long gone = id(add(isbn, "{\"page\":1,\"text\":\"지울 문장.\"}"));
		long kept = id(add(isbn, "{\"page\":2,\"text\":\"남길 문장.\"}"));
		mark(isbn, gone, "{\"sentence\":0,\"highlighted\":true,\"note\":\"지울 문장의 메모\"}");
		mark(isbn, kept, "{\"sentence\":0,\"underlined\":true}");

		assertThat(client.delete().uri("/api/library/books/{isbn}/passages/{id}", isbn, gone).retrieve().toBodilessEntity()
				.getStatusCode().value()).isEqualTo(204);

		assertThat(list(isbn).getBody()).singleElement().satisfies(passage -> assertThat(passage)
				.containsEntry("text", "남길 문장.")
				.containsEntry("marks", List.of(Map.of("sentence", 0, "underlined", true, "highlighted", false, "note", ""))));
		// Gone is gone: it cannot be deleted, corrected or marked again.
		assertThat(client.delete().uri("/api/library/books/{isbn}/passages/{id}", isbn, gone).retrieve().toBodilessEntity()
				.getStatusCode().value()).isEqualTo(404);
		assertThat(update(isbn, gone, "{\"text\":\"되살린 문장.\"}").getStatusCode().value()).isEqualTo(404);
		assertThat(mark(isbn, gone, "{\"sentence\":0,\"underlined\":true}").getStatusCode().value()).isEqualTo(404);
	}

	@Test
	void sentencesAreUnderlinedHighlightedAndAnnotatedOneByOne() {
		String isbn = shelved("9791100000182");
		long passage = id(add(isbn, "{\"page\":5,\"text\":\"첫 문장. 둘째 문장. 셋째 문장.\"}"));

		assertThat(mark(isbn, passage, "{\"sentence\":2,\"underlined\":true}").getStatusCode().value()).isEqualTo(204);
		mark(isbn, passage, "{\"sentence\":0,\"highlighted\":true,\"note\":\"  첫 문장의 메모  \"}");
		assertThat(marks(isbn)).containsExactly(
				Map.of("sentence", 0, "underlined", false, "highlighted", true, "note", "첫 문장의 메모"),
				Map.of("sentence", 2, "underlined", true, "highlighted", false, "note", ""));

		// Each request says how the sentence is marked from now on: the highlight goes, the memo is rewritten.
		mark(isbn, passage, "{\"sentence\":0,\"underlined\":true,\"note\":\"다시 쓴 메모\"}");
		assertThat(marks(isbn).get(0)).isEqualTo(Map.of("sentence", 0, "underlined", true, "highlighted", false, "note", "다시 쓴 메모"));
		// With nothing left on a sentence, its mark is gone.
		mark(isbn, passage, "{\"sentence\":2}");
		mark(isbn, passage, "{\"sentence\":0,\"note\":\"   \"}");
		assertThat(marks(isbn)).isEmpty();

		assertThat(mark(isbn, passage, "{\"sentence\":-1,\"underlined\":true}").getStatusCode().value()).isEqualTo(400);
		assertThat(mark(isbn, passage, "{\"sentence\":0,\"note\":\"" + "가".repeat(PassageService.MAX_NOTE_LENGTH + 1) + "\"}")
				.getStatusCode().value()).isEqualTo(400);
		assertThat(marks(isbn)).isEmpty();
	}

	@Test
	void anotherReadersPassagesCannotBeTouched() {
		String isbn = shelved("9791100000199");
		long mine = id(add(isbn, "{\"page\":1,\"text\":\"내 문장.\"}"));
		mark(isbn, mine, "{\"sentence\":0,\"note\":\"내 메모\"}");
		RestClient other = SignedIn.client(port, "passage_other");
		library.add("passage_other", isbn);

		// With the same book on their own shelf, another reader still cannot reach this passage.
		assertThat(other.put().uri("/api/library/books/{isbn}/passages/{id}", isbn, mine)
				.contentType(MediaType.APPLICATION_JSON).body("{\"text\":\"남이 고친 문장.\"}")
				.retrieve().toBodilessEntity().getStatusCode().value()).isEqualTo(404);
		assertThat(other.delete().uri("/api/library/books/{isbn}/passages/{id}", isbn, mine).retrieve().toBodilessEntity()
				.getStatusCode().value()).isEqualTo(404);
		assertThat(other.post().uri("/api/library/books/{isbn}/passages/{id}/marks", isbn, mine)
				.contentType(MediaType.APPLICATION_JSON).body("{\"sentence\":0,\"underlined\":true}")
				.retrieve().toBodilessEntity().getStatusCode().value()).isEqualTo(404);
		assertThat(other.get().uri("/api/library/books/{isbn}/passages", isbn).retrieve().body(LIST)).isEmpty();
		// Nor by naming the passage under a book it is not from.
		String otherBook = shelved("9791100000144");
		assertThat(update(otherBook, mine, "{\"text\":\"다른 책 아래에서 고친 문장.\"}").getStatusCode().value()).isEqualTo(404);

		assertThat(list(isbn).getBody()).singleElement().satisfies(passage -> assertThat(passage)
				.containsEntry("text", "내 문장.")
				.containsEntry("marks", List.of(Map.of("sentence", 0, "underlined", false, "highlighted", false, "note", "내 메모"))));
	}

	/** Puts a stub book on the demo user's shelf, as a confirmed scan would. */
	private String shelved(String isbn) {
		assertThat(books.find(isbn)).isPresent();
		library.add("demo", isbn);
		return isbn;
	}

	private ResponseEntity<Map<String, Object>> add(String isbn, String json) {
		return client.post().uri("/api/library/books/{isbn}/passages", isbn)
				.contentType(MediaType.APPLICATION_JSON).body(json)
				.retrieve().toEntity(OBJECT);
	}

	private ResponseEntity<List<Map<String, Object>>> list(String isbn) {
		return client.get().uri("/api/library/books/{isbn}/passages", isbn).retrieve().toEntity(LIST);
	}

	private ResponseEntity<Map<String, Object>> update(String isbn, long id, String json) {
		return client.put().uri("/api/library/books/{isbn}/passages/{id}", isbn, id)
				.contentType(MediaType.APPLICATION_JSON).body(json)
				.retrieve().toEntity(OBJECT);
	}

	private ResponseEntity<Void> mark(String isbn, long id, String json) {
		return client.post().uri("/api/library/books/{isbn}/passages/{id}/marks", isbn, id)
				.contentType(MediaType.APPLICATION_JSON).body(json)
				.retrieve().toBodilessEntity();
	}

	/** The reader's marks on the one passage of a book. */
	@SuppressWarnings("unchecked")
	private List<Map<String, Object>> marks(String isbn) {
		return (List<Map<String, Object>>) list(isbn).getBody().get(0).get("marks");
	}

	private static long id(ResponseEntity<Map<String, Object>> saved) {
		assertThat(saved.getStatusCode().value()).isEqualTo(201);
		return ((Number) saved.getBody().get("id")).longValue();
	}

}
