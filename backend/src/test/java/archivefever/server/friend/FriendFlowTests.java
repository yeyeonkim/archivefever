package archivefever.server.friend;

import static org.assertj.core.api.Assertions.assertThat;

import archivefever.server.auth.SignedIn;
import archivefever.server.book.BookService;
import archivefever.server.library.LibraryService;
import archivefever.server.user.UserService;
import archivefever.server.user.UserService.UserView;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;

/** Finding people, becoming friends and seeing a friend's shelf, over HTTP. Each test has users of its own. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class FriendFlowTests {

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

	@Autowired
	private UserService users;

	@Test
	void friendsAreMadeByAskingAndAccepting() {
		RestClient ann = SignedIn.client(port, "ann_f1");
		RestClient bob = SignedIn.client(port, "bob_f1");

		assertThat(search(ann, "bob_f1")).containsExactly(person("bob_f1", "NONE"));
		ResponseEntity<Map<String, Object>> asked = post(ann, "/api/friends/bob_f1/request");
		assertThat(asked.getStatusCode().value()).isEqualTo(200);
		assertThat(asked.getBody()).isEqualTo(person("bob_f1", "REQUESTED"));

		// Asking makes nobody a friend yet; each sees the request from their own side.
		assertThat(friends(ann)).isEmpty();
		assertThat(friends(bob)).isEmpty();
		assertThat(search(ann, "bob_f1")).containsExactly(person("bob_f1", "REQUESTED"));
		assertThat(search(bob, "ann_f1")).containsExactly(person("ann_f1", "INCOMING"));
		assertThat(notifications(bob)).singleElement().satisfies(notification -> {
			assertThat(notification).containsEntry("kind", "FRIEND_REQUEST").containsEntry("state", "PENDING");
			assertThat(notification.get("from")).isEqualTo(Map.of("handle", "ann_f1", "name", "ann_f1"));
		});
		assertThat(notifications(ann)).isEmpty();

		assertThat(post(bob, "/api/friends/ann_f1/accept").getBody()).isEqualTo(person("ann_f1", "ADDED"));

		assertThat(friends(ann)).containsExactly(person("bob_f1", "ADDED"));
		assertThat(friends(bob)).containsExactly(person("ann_f1", "ADDED"));
		// The request now shows its answer, and whoever asked is told.
		assertThat(notifications(bob)).singleElement()
				.satisfies(notification -> assertThat(notification).containsEntry("state", "ACCEPTED"));
		assertThat(notifications(ann)).singleElement().satisfies(notification -> {
			assertThat(notification).containsEntry("kind", "FRIEND_ACCEPTED").containsEntry("state", null);
			assertThat(notification.get("from")).isEqualTo(Map.of("handle", "bob_f1", "name", "bob_f1"));
		});
	}

	@Test
	void aDeclinedRequestMakesNoFriendsAndMayBeSentAgain() {
		RestClient ann = SignedIn.client(port, "ann_f2");
		RestClient bob = SignedIn.client(port, "bob_f2");
		post(ann, "/api/friends/bob_f2/request");

		assertThat(post(bob, "/api/friends/ann_f2/decline").getBody()).isEqualTo(person("ann_f2", "NONE"));

		assertThat(friends(ann)).isEmpty();
		assertThat(friends(bob)).isEmpty();
		assertThat(search(ann, "bob_f2")).containsExactly(person("bob_f2", "NONE"));
		assertThat(notifications(bob)).singleElement()
				.satisfies(notification -> assertThat(notification).containsEntry("state", "DECLINED"));
		assertThat(notifications(ann)).singleElement()
				.satisfies(notification -> assertThat(notification).containsEntry("kind", "FRIEND_DECLINED"));

		assertThat(post(ann, "/api/friends/bob_f2/request").getStatusCode().value()).isEqualTo(200);
		// Newest first: the new request waits, the old one keeps its answer.
		assertThat(notifications(bob)).extracting(notification -> notification.get("state"))
				.containsExactly("PENDING", "DECLINED");
	}

	@Test
	void requestsThatMakeNoSenseAreRefused() {
		RestClient ann = SignedIn.client(port, "ann_f3");
		RestClient bob = SignedIn.client(port, "bob_f3");

		assertThat(refusal(post(ann, "/api/friends/ann_f3/request"))).isEqualTo("400 SELF");
		assertThat(refusal(post(ann, "/api/friends/nobody_f3/request"))).isEqualTo("404 USER_NOT_FOUND");
		// Nothing to answer, and nobody to remove, before anyone has asked.
		assertThat(refusal(post(bob, "/api/friends/ann_f3/accept"))).isEqualTo("404 NO_REQUEST");
		assertThat(refusal(post(bob, "/api/friends/ann_f3/decline"))).isEqualTo("404 NO_REQUEST");
		assertThat(refusal(remove(ann, "bob_f3"))).isEqualTo("404 NOT_FRIENDS");

		post(ann, "/api/friends/bob_f3/request");
		assertThat(refusal(post(ann, "/api/friends/bob_f3/request"))).isEqualTo("409 ALREADY_REQUESTED");
		assertThat(refusal(post(bob, "/api/friends/ann_f3/request"))).isEqualTo("409 REQUEST_WAITING");
		// The one who asked cannot answer for the other.
		assertThat(refusal(post(ann, "/api/friends/bob_f3/accept"))).isEqualTo("404 NO_REQUEST");
		// A request that is still waiting is not a friendship to end.
		assertThat(refusal(remove(bob, "ann_f3"))).isEqualTo("404 NOT_FRIENDS");

		post(bob, "/api/friends/ann_f3/accept");
		assertThat(refusal(post(ann, "/api/friends/bob_f3/request"))).isEqualTo("409 ALREADY_FRIENDS");
		assertThat(refusal(post(bob, "/api/friends/ann_f3/accept"))).isEqualTo("404 NO_REQUEST");
		assertThat(friends(ann)).hasSize(1);
	}

	@Test
	void aShelfIsShownToFriendsOnly() {
		RestClient ann = SignedIn.client(port, "ann_f4");
		RestClient bob = SignedIn.client(port, "bob_f4");
		String isbn = "9791100000106";
		assertThat(books.find(isbn)).isPresent();
		library.add("bob_f4", isbn);

		assertThat(refusal(ann.get().uri("/api/friends/bob_f4/books").retrieve().toEntity(OBJECT)))
				.isEqualTo("403 NOT_FRIENDS");
		post(ann, "/api/friends/bob_f4/request");
		// Asked, not yet accepted: still closed.
		assertThat(refusal(ann.get().uri("/api/friends/bob_f4/books").retrieve().toEntity(OBJECT)))
				.isEqualTo("403 NOT_FRIENDS");
		post(bob, "/api/friends/ann_f4/accept");

		List<Map<String, Object>> shelf = ann.get().uri("/api/friends/bob_f4/books").retrieve().body(LIST);
		assertThat(shelf).singleElement().satisfies(book -> assertThat(book).containsEntry("isbn13", isbn)
				.containsEntry("spineUrl", "/api/images/" + isbn + "/spine.png").containsKeys("title", "spineWidth"));
		assertThat(bob.get().uri("/api/friends/ann_f4/books").retrieve().body(LIST)).isEmpty();

		assertThat(remove(bob, "ann_f4").getBody()).isEqualTo(person("ann_f4", "NONE"));
		assertThat(friends(ann)).isEmpty();
		assertThat(refusal(ann.get().uri("/api/friends/bob_f4/books").retrieve().toEntity(OBJECT)))
				.isEqualTo("403 NOT_FRIENDS");
		assertThat(refusal(ann.get().uri("/api/friends/nobody_f4/books").retrieve().toEntity(OBJECT)))
				.isEqualTo("404 USER_NOT_FOUND");
	}

	@Test
	void peopleAreFoundByNameOrHandleAndNeverByTheirId() {
		RestClient ann = SignedIn.client(port, "ann_f5");
		SignedIn.client(port, "bob_f5");
		SignedIn.client(port, "bobby_f5");
		// A Kakao user: found by nickname, shown by a handle the server made up.
		UserView kakao = users.signInWithKakao(5550005L, "책읽는 밥_f5");
		assertThat(kakao.handle()).matches("reader\\d{4,}");
		assertThat(users.signInWithKakao(5550005L, "책읽는 밥_f5").handle()).isEqualTo(kakao.handle());

		assertThat(search(ann, "f5")).extracting(person -> person.get("handle"))
				.containsExactlyInAnyOrder("bob_f5", "bobby_f5", kakao.handle());
		assertThat(search(ann, "BOB_F5")).extracting(person -> person.get("handle")).containsExactly("bob_f5");
		assertThat(search(ann, "@bob")).extracting(person -> person.get("handle")).contains("bob_f5", "bobby_f5");
		assertThat(search(ann, "책읽는 밥_f5")).singleElement().satisfies(person -> {
			assertThat(person).isEqualTo(Map.of("handle", kakao.handle(), "name", "책읽는 밥_f5", "status", "NONE"));
			// Above all not the id, which holds the Kakao member number.
			assertThat(person.toString()).doesNotContain("5550005");
		});
		// "@" looks in handles only, and the nickname is not one.
		assertThat(search(ann, "@책읽는")).isEmpty();
		// The wildcards of the database are just characters here.
		assertThat(search(ann, "%")).isEmpty();
		assertThat(search(ann, "")).isEmpty();
		assertThat(search(ann, "   ")).isEmpty();
		assertThat(search(ann, "@")).isEmpty();

		// By handle the Kakao user can be asked like anyone else.
		assertThat(post(ann, "/api/friends/" + kakao.handle() + "/request").getStatusCode().value()).isEqualTo(200);
		assertThat(search(ann, "@" + kakao.handle())).singleElement()
				.satisfies(person -> assertThat(person).containsEntry("status", "REQUESTED"));
	}

	@Test
	void aUserIsToldTheirOwnHandle() {
		RestClient ann = SignedIn.client(port, "ann_f6");

		assertThat(ann.get().uri("/api/me").retrieve().body(OBJECT)).containsEntry("handle", "ann_f6");
	}

	@Test
	void friendsAndNotificationsNeedASession() {
		RestClient nobody = SignedIn.anonymous(port);

		for (String path : List.of("/api/users?query=a", "/api/friends", "/api/friends/someone/books", "/api/notifications",
				"/api/swaps")) {
			assertThat(nobody.get().uri(path).retrieve().toBodilessEntity().getStatusCode().value()).as(path).isEqualTo(401);
		}
		assertThat(post(nobody, "/api/friends/someone/request").getStatusCode().value()).isEqualTo(401);
	}

	private static Map<String, Object> person(String name, String status) {
		return Map.of("handle", name, "name", name, "status", status);
	}

	private List<Map<String, Object>> search(RestClient client, String query) {
		return client.get().uri("/api/users?query={query}", query).retrieve().body(LIST);
	}

	private List<Map<String, Object>> friends(RestClient client) {
		return client.get().uri("/api/friends").retrieve().body(LIST);
	}

	private List<Map<String, Object>> notifications(RestClient client) {
		return client.get().uri("/api/notifications").retrieve().body(LIST);
	}

	private ResponseEntity<Map<String, Object>> post(RestClient client, String path) {
		return client.post().uri(path).retrieve().toEntity(OBJECT);
	}

	private ResponseEntity<Map<String, Object>> remove(RestClient client, String handle) {
		return client.delete().uri("/api/friends/{handle}", handle).retrieve().toEntity(OBJECT);
	}

	/** Status and error code of a refused request, as "404 NO_REQUEST". */
	private static String refusal(ResponseEntity<Map<String, Object>> response) {
		return response.getStatusCode().value() + " " + (response.getBody() == null ? null : response.getBody().get("error"));
	}

}
