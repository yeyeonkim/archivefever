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

/** Collecting sentences from a book on the shelf, over HTTP. */
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

}
