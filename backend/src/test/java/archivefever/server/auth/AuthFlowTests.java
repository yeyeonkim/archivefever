package archivefever.server.auth;

import static org.assertj.core.api.Assertions.assertThat;

import archivefever.server.scan.TestPhotos;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

/** Signing in by name (no Kakao key configured), and what each user can then see. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AuthFlowTests {

	private static final ParameterizedTypeReference<Map<String, Object>> OBJECT = new ParameterizedTypeReference<>() {
	};

	private static final ParameterizedTypeReference<List<Map<String, Object>>> LIST = new ParameterizedTypeReference<>() {
	};

	@Value("${local.server.port}")
	private int port;

	@Test
	void nothingPersonalIsServedWithoutSigningIn() {
		RestClient nobody = SignedIn.anonymous(port);

		assertThat(status(nobody.get().uri("/api/me"))).isEqualTo(401);
		assertThat(status(nobody.get().uri("/api/library/books"))).isEqualTo(401);
		assertThat(status(nobody.get().uri("/api/library/books/9788936434120/passages"))).isEqualTo(401);
		assertThat(status(nobody.post().uri("/api/scans"))).isEqualTo(401);
		assertThat(status(nobody.get().uri("/api/scans/some-id"))).isEqualTo(401);
		assertThat(status(nobody.post().uri("/api/scans/some-id/confirm"))).isEqualTo(401);
		assertThat(nobody.get().uri("/api/me").retrieve().body(OBJECT)).containsEntry("error", "NOT_SIGNED_IN");

		// The page that asks people to sign in, and what it needs, are open to everyone.
		assertThat(status(nobody.get().uri("/"))).isEqualTo(200);
		assertThat(status(nobody.get().uri("/api/health"))).isEqualTo(200);
		assertThat(nobody.get().uri("/api/auth/options").retrieve().body(OBJECT))
				.containsEntry("kakao", false).containsEntry("development", true);
		// Book images are not personal: an unknown one is simply not found.
		assertThat(status(nobody.get().uri("/api/images/9791100000700/cover.png"))).isEqualTo(404);
	}

	@Test
	void signingInByNameStartsASessionUntilSigningOut() {
		ResponseEntity<Map<String, Object>> signedIn = SignedIn.anonymous(port).post().uri("/api/auth/dev")
				.contentType(MediaType.APPLICATION_JSON).body(Map.of("name", "  Mina_2 "))
				.retrieve().toEntity(OBJECT);

		assertThat(signedIn.getStatusCode().value()).isEqualTo(200);
		assertThat(signedIn.getBody()).containsEntry("id", "mina_2").containsEntry("nickname", "mina_2")
				.containsEntry("provider", "dev");
		String setCookie = signedIn.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
		assertThat(setCookie).startsWith("AF_SESSION=").contains("HttpOnly").contains("Path=/").contains("Max-Age=2592000");
		// Reached over plain HTTP, as on the local network: a Secure cookie would never come back.
		assertThat(setCookie).doesNotContain("Secure");
		String cookie = setCookie.substring(0, setCookie.indexOf(';'));

		RestClient mina = RestClient.builder().baseUrl("http://localhost:" + port)
				.defaultStatusHandler(status -> true, (request, response) -> { })
				.defaultHeader(HttpHeaders.COOKIE, cookie).build();
		assertThat(mina.get().uri("/api/me").retrieve().body(OBJECT)).containsEntry("id", "mina_2");

		ResponseEntity<Void> signedOut = mina.post().uri("/api/auth/logout").retrieve().toBodilessEntity();
		assertThat(signedOut.getStatusCode().value()).isEqualTo(204);
		assertThat(signedOut.getHeaders().getFirst(HttpHeaders.SET_COOKIE)).startsWith("AF_SESSION=;").contains("Max-Age=0");
		// The token itself is dead, not just forgotten by the browser.
		assertThat(status(mina.get().uri("/api/me"))).isEqualTo(401);
	}

	@Test
	void aMadeUpSessionIsNoSession() {
		RestClient forger = RestClient.builder().baseUrl("http://localhost:" + port)
				.defaultStatusHandler(status -> true, (request, response) -> { })
				.defaultHeader(HttpHeaders.COOKIE, "AF_SESSION=demo").build();

		assertThat(status(forger.get().uri("/api/me"))).isEqualTo(401);
		assertThat(status(forger.get().uri("/api/library/books"))).isEqualTo(401);
	}

	@Test
	void namesThatCannotBeUserIdsAreRefused() {
		for (String name : List.of("", "x", "two words", "../demo", "민아", "kakao123", "a".repeat(21))) {
			assertThat(SignedIn.anonymous(port).post().uri("/api/auth/dev")
					.contentType(MediaType.APPLICATION_JSON).body(Map.of("name", name))
					.retrieve().toBodilessEntity().getStatusCode().value()).as(name).isEqualTo(400);
		}
	}

	@Test
	void eachUserHasTheirOwnShelfScansAndPassages() {
		String isbn = "9791100000717";
		RestClient ara = SignedIn.client(port, "ara");
		RestClient bin = SignedIn.client(port, "bin");

		// Ara creates a scan; the phone app, which has no session, delivers the photo by the scan's id.
		String scan = (String) ara.post().uri("/api/scans").retrieve().body(OBJECT).get("id");
		assertThat(upload(SignedIn.anonymous(port), scan, isbn).getBody()).containsEntry("status", "RESOLVED");

		// Bin can neither look at Ara's scan nor confirm it into a shelf.
		assertThat(status(bin.get().uri("/api/scans/{id}", scan))).isEqualTo(404);
		assertThat(status(bin.post().uri("/api/scans/{id}/confirm", scan))).isEqualTo(404);

		assertThat(status(ara.post().uri("/api/scans/{id}/confirm", scan))).isEqualTo(200);
		assertThat(ara.post().uri("/api/library/books/{isbn}/passages", isbn)
				.contentType(MediaType.APPLICATION_JSON).body("{\"page\":3,\"text\":\"아라의 문장.\"}")
				.retrieve().toBodilessEntity().getStatusCode().value()).isEqualTo(201);

		assertThat(ara.get().uri("/api/library/books").retrieve().body(LIST))
				.extracting(book -> book.get("isbn13")).containsExactly(isbn);
		assertThat(bin.get().uri("/api/library/books").retrieve().body(LIST)).isEmpty();
		// The book is not on Bin's shelf, so its passages are not there for Bin either.
		assertThat(status(bin.get().uri("/api/library/books/{isbn}/passages", isbn))).isEqualTo(404);

		// Once Bin shelves the same book, Bin's passages start empty: Ara's are Ara's.
		String binScan = (String) bin.post().uri("/api/scans").retrieve().body(OBJECT).get("id");
		upload(SignedIn.anonymous(port), binScan, isbn);
		bin.post().uri("/api/scans/{id}/confirm", binScan).retrieve().toBodilessEntity();
		assertThat(bin.get().uri("/api/library/books/{isbn}/passages", isbn).retrieve().body(LIST)).isEmpty();
		assertThat(ara.get().uri("/api/library/books/{isbn}/passages", isbn).retrieve().body(LIST)).hasSize(1);
	}

	@Test
	void signingInAgainIsTheSameUser() {
		RestClient first = SignedIn.client(port, "cho");
		RestClient second = SignedIn.client(port, "cho");

		String scan = (String) first.post().uri("/api/scans").retrieve().body(OBJECT).get("id");

		// A second sign-in (another device, say) sees what the first one created.
		assertThat(second.get().uri("/api/scans/{id}", scan).retrieve().body(OBJECT)).containsEntry("status", "PENDING");
	}

	private static int status(RestClient.RequestHeadersSpec<?> request) {
		return request.retrieve().toBodilessEntity().getStatusCode().value();
	}

	private ResponseEntity<Map<String, Object>> upload(RestClient client, String scan, String isbn) {
		MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
		form.add("file", new ByteArrayResource(TestPhotos.png(TestPhotos.backCover(isbn))) {
			@Override
			public String getFilename() {
				return "photo.png";
			}
		});
		return client.post().uri("/api/scans/{id}/image", scan)
				.contentType(MediaType.MULTIPART_FORM_DATA).body(form).retrieve().toEntity(OBJECT);
	}

}
