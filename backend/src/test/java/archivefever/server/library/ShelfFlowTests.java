package archivefever.server.library;

import static org.assertj.core.api.Assertions.assertThat;

import archivefever.server.auth.SignedIn;
import archivefever.server.book.StubBookLookup;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;

/**
 * Finding a book by its title, putting it on the shelf and taking a book off again, over
 * HTTP. The catalogue is the stub, which finds the same three books for any words.
 * Each test has users of its own.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ShelfFlowTests {

	private static final ParameterizedTypeReference<Map<String, Object>> OBJECT = new ParameterizedTypeReference<>() {
	};

	private static final ParameterizedTypeReference<List<Map<String, Object>>> LIST = new ParameterizedTypeReference<>() {
	};

	private static final String FIRST = StubBookLookup.SEARCHED_ISBNS.get(0);

	private static final String SECOND = StubBookLookup.SEARCHED_ISBNS.get(1);

	@Value("${local.server.port}")
	private int port;

	@Test
	void aBookFoundByItsTitleGoesOnTheShelf() {
		RestClient reader = SignedIn.client(port, "shelf_a1");

		List<Map<String, Object>> found = search(reader, "  스텁  ");
		assertThat(found).extracting(book -> book.get("isbn13")).containsExactlyElementsOf(StubBookLookup.SEARCHED_ISBNS);
		assertThat(found.get(0)).isEqualTo(Map.of("isbn13", FIRST, "title", "스텁 도서 " + FIRST, "author", "YES24 키 미설정",
				"publisher", "stub", "link", "https://www.yes24.com/", "onShelf", false));

		// The book the reader picked, with its cover, to look at before adding it.
		assertThat(reader.get().uri("/api/books/{isbn}", FIRST).retrieve().body(OBJECT))
				.containsEntry("title", "스텁 도서 " + FIRST).containsEntry("link", "https://www.yes24.com/")
				.containsEntry("coverUrl", "/api/images/" + FIRST + "/cover.png");
		assertThat(reader.get().uri("/api/images/{isbn}/cover.png", FIRST).retrieve().toBodilessEntity()
				.getStatusCode().value()).isEqualTo(200);

		ResponseEntity<Map<String, Object>> added = add(reader, FIRST);
		assertThat(added.getStatusCode().value()).isEqualTo(201);
		assertThat(added.getBody()).containsEntry("isbn13", FIRST).containsEntry("spineUrl", "/api/images/" + FIRST + "/spine.png");
		assertThat(shelf(reader)).extracting(book -> book.get("isbn13")).containsExactly(FIRST);
		// Adding it again changes nothing, and a search now says which book is there already.
		assertThat(add(reader, FIRST).getStatusCode().value()).isEqualTo(201);
		assertThat(shelf(reader)).hasSize(1);
		assertThat(search(reader, "스텁")).extracting(book -> book.get("onShelf")).containsExactly(true, false, false);
		// It is this reader's shelf only.
		assertThat(search(SignedIn.client(port, "shelf_a2"), "스텁")).extracting(book -> book.get("onShelf"))
				.containsExactly(false, false, false);
	}

	@Test
	void aSearchForNothingFindsNothing() {
		RestClient reader = SignedIn.client(port, "shelf_b1");

		assertThat(search(reader, StubBookLookup.UNKNOWN_WORDS)).isEmpty();
		assertThat(search(reader, "")).isEmpty();
		assertThat(search(reader, "   ")).isEmpty();
		assertThat(reader.get().uri("/api/books").retrieve().body(LIST)).isEmpty();
	}

	@Test
	void onlyARealBookGoesOnTheShelf() {
		RestClient reader = SignedIn.client(port, "shelf_c1");

		assertThat(refusal(add(reader, "9791100000206"))).as("wrong check digit").isEqualTo("400 INVALID_ISBN");
		assertThat(refusal(add(reader, "소년이 온다"))).isEqualTo("400 INVALID_ISBN");
		assertThat(refusal(add(reader, null))).isEqualTo("400 INVALID_ISBN");
		assertThat(refusal(add(reader, StubBookLookup.UNKNOWN_ISBN))).isEqualTo("404 BOOK_NOT_FOUND");
		assertThat(refusal(reader.get().uri("/api/books/{isbn}", StubBookLookup.UNKNOWN_ISBN).retrieve().toEntity(OBJECT)))
				.isEqualTo("404 BOOK_NOT_FOUND");
		assertThat(refusal(reader.get().uri("/api/books/{isbn}", "123").retrieve().toEntity(OBJECT))).isEqualTo("400 INVALID_ISBN");
		assertThat(shelf(reader)).isEmpty();
	}

	@Test
	void aBookTakenOffTheShelfTakesItsRecordsWithIt() {
		RestClient reader = SignedIn.client(port, "shelf_d1");
		RestClient other = SignedIn.client(port, "shelf_d2");
		add(reader, FIRST);
		add(reader, SECOND);
		add(other, FIRST);
		long passage = collect(reader, FIRST, "지울 책의 문장.");
		reader.post().uri("/api/library/books/{isbn}/passages/{id}/marks", FIRST, passage)
				.contentType(MediaType.APPLICATION_JSON).body("{\"sentence\":0,\"underlined\":true,\"note\":\"지울 책의 메모\"}")
				.retrieve().toBodilessEntity();
		collect(reader, SECOND, "남길 책의 문장.");
		collect(other, FIRST, "다른 사람의 문장.");

		assertThat(remove(reader, FIRST).getStatusCode().value()).isEqualTo(204);

		assertThat(shelf(reader)).extracting(book -> book.get("isbn13")).containsExactly(SECOND);
		assertThat(passages(reader, SECOND)).extracting(found -> found.get("text")).containsExactly("남길 책의 문장.");
		// Someone else's copy of the same book, and what they collected from it, are untouched.
		assertThat(shelf(other)).hasSize(1);
		assertThat(passages(other, FIRST)).extracting(found -> found.get("text")).containsExactly("다른 사람의 문장.");
		// Off the shelf, the book has no passages; back on it, it starts empty.
		assertThat(reader.get().uri("/api/library/books/{isbn}/passages", FIRST).retrieve().toBodilessEntity()
				.getStatusCode().value()).isEqualTo(404);
		add(reader, FIRST);
		assertThat(passages(reader, FIRST)).isEmpty();

		assertThat(refusal(remove(reader, "9791100000236"))).isEqualTo("404 NOT_ON_SHELF");
		assertThat(remove(SignedIn.anonymous(port), FIRST).getStatusCode().value()).isEqualTo(401);
	}

	@Test
	void aBookBeingReadWithAFriendStaysOnTheShelf() {
		RestClient ann = SignedIn.client(port, "shelf_e1");
		RestClient bob = SignedIn.client(port, "shelf_e2");
		ann.post().uri("/api/friends/shelf_e2/request").retrieve().toBodilessEntity();
		bob.post().uri("/api/friends/shelf_e1/accept").retrieve().toBodilessEntity();
		for (RestClient reader : List.of(ann, bob)) {
			add(reader, FIRST);
			add(reader, SECOND);
		}
		collect(ann, FIRST, "앤의 문장.");
		long reading = swap(ann, "shelf_e2", FIRST);
		long waiting = swap(ann, "shelf_e2", SECOND);

		// A request that only waits does not hold the book; taking it away means the request cannot be accepted.
		assertThat(remove(bob, SECOND).getStatusCode().value()).isEqualTo(204);
		assertThat(refusal(bob.post().uri("/api/swaps/{id}/accept", waiting).retrieve().toEntity(OBJECT))).isEqualTo("409 BOOK_MISSING");

		// Once they are reading it together, neither can take the book off their shelf.
		bob.post().uri("/api/swaps/{id}/accept", reading).retrieve().toBodilessEntity();
		assertThat(refusal(remove(ann, FIRST))).isEqualTo("409 SWAPPING");
		assertThat(refusal(remove(bob, FIRST))).isEqualTo("409 SWAPPING");
		assertThat(shelf(ann)).extracting(book -> book.get("isbn13")).contains(FIRST);
		assertThat(passages(ann, FIRST)).hasSize(1);

		// When the friendship has ended the book is free again; removing it takes the passages out of the record that was kept.
		ann.delete().uri("/api/friends/shelf_e2").retrieve().toBodilessEntity();
		assertThat(remove(ann, FIRST).getStatusCode().value()).isEqualTo(204);
		Map<String, Object> kept = bob.get().uri("/api/swaps/{id}/records", reading).retrieve().body(OBJECT);
		assertThat((List<?>) kept.get("passages")).isEmpty();
	}

	private List<Map<String, Object>> search(RestClient client, String words) {
		return client.get().uri("/api/books?query={words}", words).retrieve().body(LIST);
	}

	private List<Map<String, Object>> shelf(RestClient client) {
		return client.get().uri("/api/library/books").retrieve().body(LIST);
	}

	private List<Map<String, Object>> passages(RestClient client, String isbn) {
		return client.get().uri("/api/library/books/{isbn}/passages", isbn).retrieve().body(LIST);
	}

	private ResponseEntity<Map<String, Object>> add(RestClient client, String isbn) {
		Map<String, Object> body = new java.util.HashMap<>();
		body.put("isbn13", isbn);
		return client.post().uri("/api/library/books").contentType(MediaType.APPLICATION_JSON).body(body).retrieve().toEntity(OBJECT);
	}

	private ResponseEntity<Map<String, Object>> remove(RestClient client, String isbn) {
		return client.delete().uri("/api/library/books/{isbn}", isbn).retrieve().toEntity(OBJECT);
	}

	/** Collects a passage and returns its id. */
	private long collect(RestClient client, String isbn, String text) {
		ResponseEntity<Map<String, Object>> saved = client.post().uri("/api/library/books/{isbn}/passages", isbn)
				.contentType(MediaType.APPLICATION_JSON).body(Map.of("page", 1, "text", text)).retrieve().toEntity(OBJECT);
		assertThat(saved.getStatusCode().value()).isEqualTo(201);
		return ((Number) saved.getBody().get("id")).longValue();
	}

	/** Asks a friend for a swap and returns its id. */
	private long swap(RestClient client, String friend, String isbn) {
		ResponseEntity<Map<String, Object>> asked = client.post().uri("/api/swaps").contentType(MediaType.APPLICATION_JSON)
				.body(Map.of("friend", friend, "isbn13", isbn)).retrieve().toEntity(OBJECT);
		assertThat(asked.getStatusCode().value()).isEqualTo(201);
		return ((Number) asked.getBody().get("id")).longValue();
	}

	/** Status and error code of a refused request, as "409 SWAPPING". */
	private static String refusal(ResponseEntity<Map<String, Object>> response) {
		return response.getStatusCode().value() + " " + (response.getBody() == null ? null : response.getBody().get("error"));
	}

}
