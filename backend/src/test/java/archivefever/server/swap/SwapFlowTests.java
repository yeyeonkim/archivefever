package archivefever.server.swap;

import static org.assertj.core.api.Assertions.assertThat;

import archivefever.server.auth.SignedIn;
import archivefever.server.book.BookService;
import archivefever.server.library.LibraryService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;

/** Reading a book together with a friend, over HTTP. Each test has users of its own. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class SwapFlowTests {

	private static final ParameterizedTypeReference<Map<String, Object>> OBJECT = new ParameterizedTypeReference<>() {
	};

	private static final ParameterizedTypeReference<List<Map<String, Object>>> LIST = new ParameterizedTypeReference<>() {
	};

	private static final String BOOK = "9791100000106";

	private static final String OTHER_BOOK = "9791100000113";

	@Value("${local.server.port}")
	private int port;

	@Autowired
	private BookService books;

	@Autowired
	private LibraryService library;

	@Test
	void anAcceptedSwapSharesWhatBothCollectedAndWrote() {
		RestClient ann = friendsWith("ann_s1", "bob_s1");
		RestClient bob = SignedIn.client(port, "bob_s1");
		shelve("ann_s1", BOOK);
		shelve("bob_s1", BOOK);
		shelve("bob_s1", OTHER_BOOK);
		long annPassage = collect(ann, BOOK, 40, "앤이 모은 문장.");
		long bobPassage = collect(bob, BOOK, 12, "밥이 모은 첫 문장. 그리고 둘째 문장.");

		// Only a book both have can be read together.
		assertThat(ann.get().uri("/api/swaps/candidates?friend=bob_s1").retrieve().body(LIST))
				.extracting(book -> book.get("isbn13")).containsExactly(BOOK);

		ResponseEntity<Map<String, Object>> asked = request(ann, "bob_s1", BOOK);
		assertThat(asked.getStatusCode().value()).isEqualTo(201);
		assertThat(asked.getBody()).containsEntry("status", "PENDING").containsEntry("requestedByMe", true)
				.containsEntry("partner", Map.of("handle", "bob_s1", "name", "bob_s1"));
		assertThat(asked.getBody().get("book")).isEqualTo(Map.of("isbn13", BOOK, "title", "스텁 도서 " + BOOK,
				"author", "YES24 키 미설정", "coverUrl", "/api/images/" + BOOK + "/cover.png"));
		long swap = ((Number) asked.getBody().get("id")).longValue();

		// Waiting: the one who asked sees it waiting, the one asked is told, and nothing is shared.
		assertThat(swaps(ann)).singleElement().satisfies(listed -> assertThat(listed).containsEntry("status", "PENDING"));
		assertThat(swaps(bob)).isEmpty();
		assertThat(notifications(bob).get(0)).containsEntry("kind", "SWAP_REQUEST").containsEntry("state", "PENDING")
				.containsEntry("swapId", (int) swap)
				.containsEntry("book", Map.of("isbn13", BOOK, "title", "스텁 도서 " + BOOK))
				.containsEntry("from", Map.of("handle", "ann_s1", "name", "ann_s1"));
		assertThat(refusal(records(ann, swap))).isEqualTo("409 NOT_ACTIVE");
		assertThat(refusal(records(bob, swap))).isEqualTo("409 NOT_ACTIVE");
		assertThat(note(ann, swap, bobPassage, 0, "아직은 안 되는 메모").getStatusCode().value()).isEqualTo(409);

		ResponseEntity<Map<String, Object>> accepted = bob.post().uri("/api/swaps/{id}/accept", swap).retrieve().toEntity(OBJECT);
		assertThat(accepted.getBody()).containsEntry("status", "ACTIVE").containsEntry("requestedByMe", false)
				.containsEntry("partner", Map.of("handle", "ann_s1", "name", "ann_s1"));
		assertThat(swaps(ann)).singleElement().satisfies(listed -> assertThat(listed).containsEntry("status", "ACTIVE"));
		assertThat(swaps(bob)).singleElement().satisfies(listed -> assertThat(listed).containsEntry("status", "ACTIVE"));
		assertThat(notifications(bob).get(0)).containsEntry("state", "ACCEPTED");
		assertThat(notifications(ann).get(0)).containsEntry("kind", "SWAP_ACCEPTED").containsEntry("swapId", (int) swap);

		// Both readers' passages, in reading order, each marked with whose it is.
		Map<String, Object> seenByAnn = records(ann, swap).getBody();
		assertThat(seenByAnn).containsEntry("me", Map.of("handle", "ann_s1", "name", "ann_s1"))
				.containsEntry("partner", Map.of("handle", "bob_s1", "name", "bob_s1"));
		assertThat(list(seenByAnn, "passages")).extracting(passage -> passage.get("text"), passage -> passage.get("by"),
				passage -> passage.get("mine"), passage -> passage.get("page"))
				.containsExactly(org.assertj.core.groups.Tuple.tuple("밥이 모은 첫 문장. 그리고 둘째 문장.", "bob_s1", false, 12),
						org.assertj.core.groups.Tuple.tuple("앤이 모은 문장.", "ann_s1", true, 40));
		assertThat(list(seenByAnn, "notes")).isEmpty();

		// Each writes on the other's passage and on their own; each sees both, knowing whose.
		assertThat(note(ann, swap, bobPassage, 1, "  둘째 문장이 좋았어.  ").getStatusCode().value()).isEqualTo(204);
		assertThat(note(bob, swap, bobPassage, 1, "나도.").getStatusCode().value()).isEqualTo(204);
		assertThat(note(bob, swap, annPassage, 0, "이건 몇 쪽이야?").getStatusCode().value()).isEqualTo(204);
		assertThat(list(records(bob, swap).getBody(), "notes")).extracting(note -> note.get("text"), note -> note.get("by"),
				note -> note.get("mine"), note -> note.get("sentence"))
				.containsExactlyInAnyOrder(org.assertj.core.groups.Tuple.tuple("둘째 문장이 좋았어.", "ann_s1", false, 1),
						org.assertj.core.groups.Tuple.tuple("나도.", "bob_s1", true, 1),
						org.assertj.core.groups.Tuple.tuple("이건 몇 쪽이야?", "bob_s1", true, 0));

		// Writing again replaces one's own memo, and blank text takes it away; the other's stays.
		note(ann, swap, bobPassage, 1, "다시 읽어도 좋네.");
		note(bob, swap, annPassage, 0, "   ");
		assertThat(list(records(ann, swap).getBody(), "notes")).extracting(note -> note.get("text"), note -> note.get("mine"))
				.containsExactlyInAnyOrder(org.assertj.core.groups.Tuple.tuple("다시 읽어도 좋네.", true),
						org.assertj.core.groups.Tuple.tuple("나도.", false));

		// A passage collected later is there the next time the records are read.
		collect(ann, BOOK, 3, "나중에 모은 문장.");
		assertThat(list(records(bob, swap).getBody(), "passages")).extracting(passage -> passage.get("text"))
				.containsExactly("나중에 모은 문장.", "밥이 모은 첫 문장. 그리고 둘째 문장.", "앤이 모은 문장.");
	}

	@Test
	void aSwapNeedsAFriendABookBothHaveAndNoSwapOfItAlready() {
		RestClient ann = friendsWith("ann_s2", "bob_s2");
		RestClient bob = SignedIn.client(port, "bob_s2");
		RestClient carl = SignedIn.client(port, "carl_s2");
		shelve("ann_s2", BOOK);
		shelve("carl_s2", BOOK);

		assertThat(refusal(request(ann, "carl_s2", BOOK))).isEqualTo("403 NOT_FRIENDS");
		assertThat(refusal(request(ann, "nobody_s2", BOOK))).isEqualTo("404 USER_NOT_FOUND");
		assertThat(refusal(ann.get().uri("/api/swaps/candidates?friend=carl_s2").retrieve().toEntity(OBJECT)))
				.isEqualTo("403 NOT_FRIENDS");
		// Bob does not have the book yet; nor does it help that only he has another.
		assertThat(refusal(request(ann, "bob_s2", BOOK))).isEqualTo("409 NOT_COMMON");
		shelve("bob_s2", OTHER_BOOK);
		assertThat(refusal(request(bob, "ann_s2", OTHER_BOOK))).isEqualTo("409 NOT_COMMON");
		assertThat(ann.get().uri("/api/swaps/candidates?friend=bob_s2").retrieve().body(LIST)).isEmpty();
		assertThat(request(ann, "bob_s2", null).getStatusCode().value()).isEqualTo(409);
		assertThat(swaps(ann)).isEmpty();
		assertThat(notifications(bob)).noneMatch(notification -> "SWAP_REQUEST".equals(notification.get("kind")));

		shelve("bob_s2", BOOK);
		long swap = id(request(ann, "bob_s2", BOOK));
		// One swap per pair and book, whichever of the two asks, waiting or accepted.
		assertThat(refusal(request(ann, "bob_s2", BOOK))).isEqualTo("409 ALREADY_SWAPPING");
		assertThat(refusal(request(bob, "ann_s2", BOOK))).isEqualTo("409 ALREADY_SWAPPING");
		bob.post().uri("/api/swaps/{id}/accept", swap).retrieve().toBodilessEntity();
		assertThat(refusal(request(bob, "ann_s2", BOOK))).isEqualTo("409 ALREADY_SWAPPING");
		// Another book of theirs is another swap.
		shelve("ann_s2", OTHER_BOOK);
		assertThat(request(bob, "ann_s2", OTHER_BOOK).getStatusCode().value()).isEqualTo(201);
		assertThat(carl.get().uri("/api/swaps").retrieve().body(LIST)).isEmpty();
	}

	@Test
	void onlyTheOneAskedAnswersAndOnlyOnce() {
		RestClient ann = friendsWith("ann_s3", "bob_s3");
		RestClient bob = SignedIn.client(port, "bob_s3");
		RestClient carl = SignedIn.client(port, "carl_s3");
		shelve("ann_s3", BOOK);
		shelve("bob_s3", BOOK);
		long swap = id(request(ann, "bob_s3", BOOK));

		assertThat(refusal(answer(ann, swap, "accept"))).isEqualTo("403 NOT_YOURS_TO_ANSWER");
		assertThat(refusal(answer(ann, swap, "decline"))).isEqualTo("403 NOT_YOURS_TO_ANSWER");
		// To anyone else the swap does not exist.
		assertThat(refusal(answer(carl, swap, "accept"))).isEqualTo("404 SWAP_NOT_FOUND");
		assertThat(refusal(answer(bob, 987654321L, "accept"))).isEqualTo("404 SWAP_NOT_FOUND");

		assertThat(answer(bob, swap, "decline").getBody()).containsEntry("status", "DECLINED");
		assertThat(refusal(answer(bob, swap, "accept"))).isEqualTo("409 ALREADY_ANSWERED");
		assertThat(refusal(answer(bob, swap, "decline"))).isEqualTo("409 ALREADY_ANSWERED");

		// Declined: nothing is shared, the one who asked is told, and may ask again.
		assertThat(swaps(ann)).isEmpty();
		assertThat(swaps(bob)).isEmpty();
		assertThat(refusal(records(ann, swap))).isEqualTo("409 NOT_ACTIVE");
		assertThat(notifications(bob).get(0)).containsEntry("kind", "SWAP_REQUEST").containsEntry("state", "DECLINED");
		assertThat(notifications(ann).get(0)).containsEntry("kind", "SWAP_DECLINED");
		assertThat(request(ann, "bob_s3", BOOK).getStatusCode().value()).isEqualTo(201);
	}

	@Test
	void theRecordsOfASwapAreForItsTwoReadersOnly() {
		RestClient ann = friendsWith("ann_s4", "bob_s4");
		RestClient bob = SignedIn.client(port, "bob_s4");
		RestClient carl = friendsWith("carl_s4", "ann_s4");
		for (String reader : List.of("ann_s4", "bob_s4", "carl_s4")) {
			shelve(reader, BOOK);
		}
		shelve("ann_s4", OTHER_BOOK);
		long bobPassage = collect(bob, BOOK, 5, "밥의 문장.");
		long carlPassage = collect(carl, BOOK, 6, "칼의 문장.");
		long annOtherBook = collect(ann, OTHER_BOOK, 7, "다른 책의 문장.");
		long swap = id(request(ann, "bob_s4", BOOK));
		answer(bob, swap, "accept");

		// Carl is Ann's friend and has the book, and still is no part of this swap.
		assertThat(refusal(records(carl, swap))).isEqualTo("404 SWAP_NOT_FOUND");
		assertThat(refusal(note(carl, swap, bobPassage, 0, "끼어드는 메모"))).isEqualTo("404 SWAP_NOT_FOUND");
		assertThat(SignedIn.anonymous(port).get().uri("/api/swaps/{id}/records", swap).retrieve().toBodilessEntity()
				.getStatusCode().value()).isEqualTo(401);

		// The swap reaches the two readers' passages of this book, and no further.
		assertThat(refusal(note(ann, swap, carlPassage, 0, "칼의 문장에 쓰는 메모"))).isEqualTo("404 PASSAGE_NOT_FOUND");
		assertThat(refusal(note(ann, swap, annOtherBook, 0, "다른 책에 쓰는 메모"))).isEqualTo("404 PASSAGE_NOT_FOUND");
		assertThat(refusal(note(ann, swap, 987654321L, 0, "없는 문장에 쓰는 메모"))).isEqualTo("404 PASSAGE_NOT_FOUND");
		assertThat(note(ann, swap, bobPassage, -1, "메모").getStatusCode().value()).isEqualTo(400);
		assertThat(note(ann, swap, bobPassage, 0, "가".repeat(1001)).getStatusCode().value()).isEqualTo(400);
		assertThat(note(ann, swap, bobPassage, 0, "가".repeat(1000)).getStatusCode().value()).isEqualTo(204);

		// What Ann and Carl share in a swap of their own does not show Bob's passage or the memo on it.
		long withCarl = id(request(carl, "ann_s4", BOOK));
		answer(ann, withCarl, "accept");
		Map<String, Object> seenByCarl = records(carl, withCarl).getBody();
		assertThat(list(seenByCarl, "passages")).extracting(passage -> passage.get("text")).containsExactly("칼의 문장.");
		assertThat(list(seenByCarl, "notes")).isEmpty();
	}

	@Test
	void endingTheFriendshipKeepsWhatWasSharedAndSharesNothingNewer() {
		RestClient ann = friendsWith("ann_s5", "bob_s5");
		RestClient bob = SignedIn.client(port, "bob_s5");
		RestClient carl = friendsWith("carl_s5", "ann_s5");
		for (String reader : List.of("ann_s5", "bob_s5")) {
			shelve(reader, BOOK);
			shelve(reader, OTHER_BOOK);
		}
		shelve("carl_s5", BOOK);
		long annPassage = collect(ann, BOOK, 2, "앤의 문장.");
		long bobPassage = collect(bob, BOOK, 1, "밥의 문장.");
		long reading = id(request(ann, "bob_s5", BOOK));
		answer(bob, reading, "accept");
		note(ann, reading, bobPassage, 0, "친구일 때 쓴 메모");
		note(ann, reading, annPassage, 0, "내 문장에 쓴 메모");
		long waiting = id(request(ann, "bob_s5", OTHER_BOOK));
		assertThat(notifications(bob)).anyMatch(notification -> "PENDING".equals(notification.get("state")));

		assertThat(ann.delete().uri("/api/friends/bob_s5").retrieve().toBodilessEntity().getStatusCode().value()).isEqualTo(200);

		// The swap they were reading is over, and its record stays with both, as it stood.
		for (RestClient reader : List.of(ann, bob)) {
			assertThat(swaps(reader)).filteredOn(swap -> ((Number) swap.get("id")).longValue() == reading).singleElement()
					.satisfies(swap -> assertThat(swap).containsEntry("status", "ENDED"));
			Map<String, Object> kept = records(reader, reading).getBody();
			assertThat(list(kept, "passages")).extracting(passage -> passage.get("text")).containsExactly("밥의 문장.", "앤의 문장.");
			assertThat(list(kept, "notes")).extracting(note -> note.get("text"))
					.containsExactlyInAnyOrder("친구일 때 쓴 메모", "내 문장에 쓴 메모");
		}
		assertThat(swaps(bob)).hasSize(1);
		// Nothing more is written on it.
		assertThat(refusal(note(ann, reading, bobPassage, 0, "이제는 안 되는 메모"))).isEqualTo("409 NOT_ACTIVE");
		assertThat(refusal(note(bob, reading, bobPassage, 0, "밥에게도 안 되는 메모"))).isEqualTo("409 NOT_ACTIVE");
		// And nothing newer shows in it: not what is collected afterwards, nor a memo its writer has changed since.
		collect(bob, BOOK, 3, "헤어진 뒤에 밥이 모은 문장.");
		long withCarl = id(request(carl, "ann_s5", BOOK));
		answer(ann, withCarl, "accept");
		note(ann, withCarl, annPassage, 0, "헤어진 뒤에 고친 메모");
		for (RestClient reader : List.of(ann, bob)) {
			Map<String, Object> kept = records(reader, reading).getBody();
			assertThat(list(kept, "passages")).extracting(passage -> passage.get("text")).containsExactly("밥의 문장.", "앤의 문장.");
			assertThat(list(kept, "notes")).extracting(note -> note.get("text")).containsExactly("친구일 때 쓴 메모");
		}
		// The shelves are closed to each other again.
		assertThat(refusal(ann.get().uri("/api/friends/bob_s5/books").retrieve().toEntity(OBJECT))).isEqualTo("403 NOT_FRIENDS");

		// The request that was still waiting had shared nothing: it is simply off.
		assertThat(refusal(answer(bob, waiting, "accept"))).isEqualTo("409 ALREADY_ANSWERED");
		assertThat(refusal(records(ann, waiting))).isEqualTo("409 NOT_ACTIVE");
		assertThat(notifications(bob)).noneMatch(notification -> "PENDING".equals(notification.get("state")));
		assertThat(swaps(bob)).extracting(swap -> swap.get("status")).containsExactly("ENDED");

		// Friends again, they ask again. While that waits, the record kept from before is what they have.
		ann.post().uri("/api/friends/bob_s5/request").retrieve().toBodilessEntity();
		bob.post().uri("/api/friends/ann_s5/accept").retrieve().toBodilessEntity();
		long again = id(request(bob, "ann_s5", BOOK));
		assertThat(swaps(bob)).extracting(swap -> swap.get("status")).containsExactly("ENDED", "PENDING");
		// Once they are reading again, the new swap shows everything, in place of the old record.
		answer(ann, again, "accept");
		assertThat(swaps(bob)).singleElement().satisfies(swap -> assertThat(swap).containsEntry("status", "ACTIVE")
				.containsEntry("id", (int) again));
		assertThat(swaps(ann)).extracting(swap -> swap.get("status")).containsExactly("ACTIVE", "ACTIVE");
		Map<String, Object> together = records(bob, again).getBody();
		assertThat(list(together, "passages")).extracting(passage -> passage.get("text"))
				.containsExactly("밥의 문장.", "앤의 문장.", "헤어진 뒤에 밥이 모은 문장.");
		assertThat(list(together, "notes")).extracting(note -> note.get("text"))
				.containsExactlyInAnyOrder("친구일 때 쓴 메모", "헤어진 뒤에 고친 메모");
	}

	/** Signs both in and makes them friends; returns the first one's client. */
	private RestClient friendsWith(String name, String friend) {
		RestClient client = SignedIn.client(port, name);
		RestClient other = SignedIn.client(port, friend);
		assertThat(client.post().uri("/api/friends/{handle}/request", friend).retrieve().toBodilessEntity()
				.getStatusCode().value()).isEqualTo(200);
		assertThat(other.post().uri("/api/friends/{handle}/accept", name).retrieve().toBodilessEntity()
				.getStatusCode().value()).isEqualTo(200);
		return client;
	}

	/** Puts a stub book on a user's shelf, as a confirmed scan would. */
	private void shelve(String userId, String isbn) {
		assertThat(books.find(isbn)).isPresent();
		library.add(userId, isbn);
	}

	/** Collects a passage and returns its id. */
	private long collect(RestClient client, String isbn, int page, String text) {
		ResponseEntity<Map<String, Object>> saved = client.post().uri("/api/library/books/{isbn}/passages", isbn)
				.contentType(MediaType.APPLICATION_JSON).body(Map.of("page", page, "text", text))
				.retrieve().toEntity(OBJECT);
		assertThat(saved.getStatusCode().value()).isEqualTo(201);
		return ((Number) saved.getBody().get("id")).longValue();
	}

	private ResponseEntity<Map<String, Object>> request(RestClient client, String friend, String isbn) {
		Map<String, Object> body = new java.util.HashMap<>();
		body.put("friend", friend);
		body.put("isbn13", isbn);
		return client.post().uri("/api/swaps").contentType(MediaType.APPLICATION_JSON).body(body).retrieve().toEntity(OBJECT);
	}

	private ResponseEntity<Map<String, Object>> answer(RestClient client, long swap, String answer) {
		return client.post().uri("/api/swaps/{id}/" + answer, swap).retrieve().toEntity(OBJECT);
	}

	private ResponseEntity<Map<String, Object>> records(RestClient client, long swap) {
		return client.get().uri("/api/swaps/{id}/records", swap).retrieve().toEntity(OBJECT);
	}

	private ResponseEntity<Map<String, Object>> note(RestClient client, long swap, long passage, int sentence, String text) {
		return client.post().uri("/api/swaps/{id}/notes", swap).contentType(MediaType.APPLICATION_JSON)
				.body(Map.of("passageId", passage, "sentence", sentence, "text", text)).retrieve().toEntity(OBJECT);
	}

	private List<Map<String, Object>> swaps(RestClient client) {
		return client.get().uri("/api/swaps").retrieve().body(LIST);
	}

	private List<Map<String, Object>> notifications(RestClient client) {
		return client.get().uri("/api/notifications").retrieve().body(LIST);
	}

	private static long id(ResponseEntity<Map<String, Object>> response) {
		assertThat(response.getStatusCode().value()).isEqualTo(201);
		return ((Number) response.getBody().get("id")).longValue();
	}

	@SuppressWarnings("unchecked")
	private static List<Map<String, Object>> list(Map<String, Object> body, String field) {
		return (List<Map<String, Object>>) body.get(field);
	}

	/** Status and error code of a refused request, as "409 NOT_ACTIVE". */
	private static String refusal(ResponseEntity<Map<String, Object>> response) {
		return response.getStatusCode().value() + " " + (response.getBody() == null ? null : response.getBody().get("error"));
	}

}
