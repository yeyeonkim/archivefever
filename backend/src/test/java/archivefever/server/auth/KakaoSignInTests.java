package archivefever.server.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Signing in with Kakao, against a stand-in for Kakao that answers as its REST API does.
 * The browser's part (following redirects, keeping cookies) is played by hand.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = { "archivefever.kakao.rest-api-key=test-rest-key", "archivefever.kakao.client-secret=test-secret" })
@ActiveProfiles("test")
class KakaoSignInTests {

	/** What the stand-in Kakao was asked, in order. */
	private static final List<String> asked = new CopyOnWriteArrayList<>();

	/** The nickname the stand-in reports for member 4242. */
	private static volatile String nickname = "미나";

	private static HttpServer kakao;

	@Value("${local.server.port}")
	private int port;

	private final HttpClient browser = HttpClient.newHttpClient();

	@DynamicPropertySource
	static void standInForKakao(DynamicPropertyRegistry registry) throws IOException {
		kakao = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		kakao.createContext("/oauth/token", exchange -> {
			String form = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
			asked.add("token " + URLDecoder.decode(form, StandardCharsets.UTF_8));
			if (form.contains("code=good-code")) {
				answer(exchange, 200, "{\"token_type\":\"bearer\",\"access_token\":\"access-123\",\"expires_in\":21599,"
						+ "\"refresh_token\":\"refresh-456\",\"refresh_token_expires_in\":5183999}");
			}
			else {
				answer(exchange, 400, "{\"error\":\"invalid_grant\",\"error_description\":\"authorization code not found\","
						+ "\"error_code\":\"KOE320\"}");
			}
		});
		kakao.createContext("/v2/user/me", exchange -> {
			asked.add("me " + exchange.getRequestHeaders().getFirst("Authorization"));
			answer(exchange, 200, "{\"id\":4242,\"connected_at\":\"2026-11-24T00:00:00Z\","
					+ "\"properties\":{\"nickname\":\"old\"},"
					+ "\"kakao_account\":{\"profile_nickname_needs_agreement\":false,\"profile\":{\"nickname\":\"" + nickname + "\"}}}");
		});
		kakao.start();
		String address = "http://127.0.0.1:" + kakao.getAddress().getPort();
		registry.add("archivefever.kakao.auth-url", () -> address);
		registry.add("archivefever.kakao.api-url", () -> address);
	}

	@AfterAll
	static void stop() {
		kakao.stop(0);
	}

	@BeforeEach
	void reset() {
		asked.clear();
		nickname = "미나";
	}

	@Test
	void theSignInPageOffersKakaoAndNotSigningInByName() throws Exception {
		assertThat(get("/api/auth/options", null).body()).contains("\"kakao\":true").contains("\"development\":false");

		HttpResponse<String> byName = browser.send(HttpRequest.newBuilder(uri("/api/auth/dev"))
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString("{\"name\":\"mina\"}")).build(), HttpResponse.BodyHandlers.ofString());
		assertThat(byName.statusCode()).isEqualTo(404);
	}

	@Test
	void comingBackFromKakaoWithACodeSignsThePersonIn() throws Exception {
		String callback = "http://localhost:" + port + "/auth/kakao/callback";

		HttpResponse<String> start = get("/auth/kakao/login", null);
		assertThat(start.statusCode()).isEqualTo(302);
		URI toKakao = URI.create(start.headers().firstValue("Location").orElseThrow());
		Map<String, String> query = query(toKakao);
		assertThat(toKakao.getPath()).isEqualTo("/oauth/authorize");
		assertThat(toKakao.getPort()).isEqualTo(kakao.getAddress().getPort());
		assertThat(query).containsEntry("response_type", "code").containsEntry("client_id", "test-rest-key")
				.containsEntry("redirect_uri", callback);
		String state = query.get("state");
		String stateCookie = cookie(start, "AF_KAKAO_STATE");
		assertThat(stateCookie).isEqualTo("AF_KAKAO_STATE=" + state);

		HttpResponse<String> back = get("/auth/kakao/callback?code=good-code&state=" + state, stateCookie);
		assertThat(back.statusCode()).isEqualTo(302);
		assertThat(back.headers().firstValue("Location")).hasValue("/");
		String session = cookie(back, "AF_SESSION");

		// The code was exchanged with this app's key and secret, and the token used to ask who it is.
		assertThat(asked).containsExactly(
				"token grant_type=authorization_code&client_id=test-rest-key&redirect_uri=" + callback
						+ "&code=good-code&client_secret=test-secret",
				"me Bearer access-123");
		assertThat(get("/api/me", session).body())
				.contains("\"id\":\"kakao4242\"").contains("\"nickname\":\"미나\"").contains("\"provider\":\"kakao\"");
	}

	@Test
	void theSameKakaoMemberIsTheSameUserNextTime() throws Exception {
		String first = signIn();
		nickname = "새 이름";
		String second = signIn();

		assertThat(second).isNotEqualTo(first);
		assertThat(get("/api/me", second).body()).contains("\"id\":\"kakao4242\"").contains("\"nickname\":\"새 이름\"");
		// The earlier sign-in is still good, and is the same person.
		assertThat(get("/api/me", first).body()).contains("\"id\":\"kakao4242\"").contains("\"nickname\":\"새 이름\"");
	}

	@Test
	void anAnswerThatDoesNotBelongToThisBrowsersSignInIsRefused() throws Exception {
		HttpResponse<String> start = get("/auth/kakao/login", null);
		String stateCookie = cookie(start, "AF_KAKAO_STATE");

		for (String attempt : List.of(
				"/auth/kakao/callback?code=good-code&state=someone-elses",        // forged or stale state
				"/auth/kakao/callback?code=good-code",                              // no state at all
				"/auth/kakao/callback?error=access_denied&state=" + stateCookie.substring(15))) {   // declined at Kakao
			HttpResponse<String> back = get(attempt, stateCookie);
			assertThat(back.statusCode()).as(attempt).isEqualTo(302);
			assertThat(back.headers().firstValue("Location")).as(attempt).hasValue("/?login=failed");
			assertThat(back.headers().allValues("Set-Cookie")).as(attempt).noneMatch(value -> value.startsWith("AF_SESSION="));
		}
		// Without the cookie from starting the sign-in, even the right state is nobody's.
		HttpResponse<String> noCookie = get("/auth/kakao/callback?code=good-code&state=" + stateCookie.substring(15), null);
		assertThat(noCookie.headers().firstValue("Location")).hasValue("/?login=failed");
		assertThat(asked).isEmpty();
	}

	@Test
	void aCodeKakaoDoesNotAcceptSignsNobodyIn() throws Exception {
		HttpResponse<String> start = get("/auth/kakao/login", null);
		String stateCookie = cookie(start, "AF_KAKAO_STATE");

		HttpResponse<String> back = get("/auth/kakao/callback?code=used-up&state=" + stateCookie.substring(15), stateCookie);

		assertThat(back.headers().firstValue("Location")).hasValue("/?login=failed");
		assertThat(back.headers().allValues("Set-Cookie")).noneMatch(value -> value.startsWith("AF_SESSION="));
		assertThat(asked).hasSize(1);
	}

	/** Goes through the whole round trip and returns the session cookie. */
	private String signIn() throws Exception {
		HttpResponse<String> start = get("/auth/kakao/login", null);
		String stateCookie = cookie(start, "AF_KAKAO_STATE");
		return cookie(get("/auth/kakao/callback?code=good-code&state=" + stateCookie.substring(15), stateCookie), "AF_SESSION");
	}

	private URI uri(String path) {
		return URI.create("http://localhost:" + port + path);
	}

	/** A request that, like the stand-in browser it is, follows no redirects. */
	private HttpResponse<String> get(String path, String cookie) throws Exception {
		HttpRequest.Builder request = HttpRequest.newBuilder(uri(path)).GET();
		if (cookie != null) {
			request.header("Cookie", cookie);
		}
		return browser.send(request.build(), HttpResponse.BodyHandlers.ofString());
	}

	/** The "name=value" part of the cookie the response sets. */
	private static String cookie(HttpResponse<String> response, String name) {
		return response.headers().allValues("Set-Cookie").stream()
				.filter(value -> value.startsWith(name + "=") && !value.startsWith(name + "=;"))
				.map(value -> value.substring(0, value.indexOf(';')))
				.findFirst().orElseThrow(() -> new AssertionError("No " + name + " in " + response.headers().allValues("Set-Cookie")));
	}

	private static Map<String, String> query(URI uri) {
		return Stream.of(uri.getRawQuery().split("&")).map(pair -> pair.split("=", 2))
				.collect(Collectors.toMap(pair -> pair[0], pair -> URLDecoder.decode(pair[1], StandardCharsets.UTF_8)));
	}

	private static void answer(HttpExchange exchange, int status, String json) throws IOException {
		byte[] body = json.getBytes(StandardCharsets.UTF_8);
		exchange.getResponseHeaders().add("Content-Type", "application/json;charset=UTF-8");
		exchange.sendResponseHeaders(status, body.length);
		exchange.getResponseBody().write(body);
		exchange.close();
	}

}
