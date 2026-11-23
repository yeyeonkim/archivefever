package archivefever.server.auth;

import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

/** HTTP clients for tests: one that has signed in by name, and one that has not signed in. */
public final class SignedIn {

	private SignedIn() {
	}

	/** A client that carries the session of the named development user. */
	public static RestClient client(int port, String name) {
		return builder(port).defaultHeader(HttpHeaders.COOKIE, cookie(port, name)).build();
	}

	public static RestClient anonymous(int port) {
		return builder(port).build();
	}

	/** Signs in by name and returns the session cookie as a browser would send it back. */
	public static String cookie(int port, String name) {
		ResponseEntity<Void> response = anonymous(port).post().uri("/api/auth/dev")
				.contentType(MediaType.APPLICATION_JSON).body(Map.of("name", name))
				.retrieve().toBodilessEntity();
		List<String> cookies = response.getHeaders().get(HttpHeaders.SET_COOKIE);
		if (response.getStatusCode().value() != 200 || cookies == null) {
			throw new AssertionError("Sign-in as " + name + " answered " + response.getStatusCode());
		}
		return cookies.stream().filter(cookie -> cookie.startsWith(SessionFilter.COOKIE + "="))
				.map(cookie -> cookie.substring(0, cookie.indexOf(';')))
				.findFirst().orElseThrow(() -> new AssertionError("No session cookie in " + cookies));
	}

	private static RestClient.Builder builder(int port) {
		return RestClient.builder()
				.baseUrl("http://localhost:" + port)
				// Statuses are asserted by the tests rather than thrown.
				.defaultStatusHandler(status -> true, (request, response) -> { });
	}

}
