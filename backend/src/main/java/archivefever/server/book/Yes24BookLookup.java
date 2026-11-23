package archivefever.server.book;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Looks books up through the YES24 Open API (https://developers.yes24.com).
 *
 * The API wraps every answer as {success, errorCode, message, data}; a lookup returns
 * its single book in data.items.
 */
public class Yes24BookLookup implements BookLookup {

	private static final Duration RATE_LIMIT_PAUSE = Duration.ofMillis(1200);

	private final RestClient client;

	public Yes24BookLookup(String baseUrl, String apiKey) {
		JdkClientHttpRequestFactory requests = new JdkClientHttpRequestFactory(
				HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
		requests.setReadTimeout(Duration.ofSeconds(8));
		this.client = RestClient.builder()
				.baseUrl(baseUrl)
				.defaultHeader("X-Api-Key", apiKey)
				.requestFactory(requests)
				.build();
	}

	@Override
	public Optional<Book> findByIsbn13(String isbn13) {
		ResponseEntity<Envelope> response = request(isbn13);
		if (response.getStatusCode().value() == 429) {
			// The basic key allows 10 calls a second; one short pause clears a burst.
			pause();
			response = request(isbn13);
		}
		int status = response.getStatusCode().value();
		Envelope body = response.getBody();
		if (status == 404) {
			return Optional.empty();
		}
		if (status == 401 || status == 403) {
			throw new LookupException(Failure.CONFIG, "YES24 rejected the API key: " + describe(body), null);
		}
		if (status != 200 || body == null || !body.success()) {
			throw new LookupException(Failure.LOOKUP_UNAVAILABLE, "YES24 answered " + status + ": " + describe(body), null);
		}
		if (body.data() == null || body.data().items() == null || body.data().items().isEmpty()) {
			return Optional.empty();
		}
		Item item = body.data().items().get(0);
		return Optional.of(new Book(isbn13, item.itemId(), item.title(), item.author(), item.publisher(),
				item.link(), item.cover(), item.sideCover(), item.pages()));
	}

	private ResponseEntity<Envelope> request(String isbn13) {
		try {
			return client.get()
					.uri(uri -> uri.path("/goods/itemDetail")
							.queryParam("searchType", "ISBN13")
							.queryParam("query", isbn13)
							// detail=Y adds the page count, used to size generated spines.
							.queryParam("detail", "Y")
							.build())
					.retrieve()
					// Error answers carry the same envelope, so read them instead of throwing.
					.onStatus(HttpStatusCode::isError, (request, errorResponse) -> { })
					.toEntity(Envelope.class);
		}
		catch (RestClientException exception) {
			throw new LookupException(Failure.LOOKUP_UNAVAILABLE, "YES24 could not be reached", exception);
		}
	}

	private static void pause() {
		try {
			Thread.sleep(RATE_LIMIT_PAUSE);
		}
		catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new LookupException(Failure.LOOKUP_UNAVAILABLE, "Interrupted while waiting for YES24", exception);
		}
	}

	private static String describe(Envelope body) {
		return body == null ? "no body" : body.errorCode() + " " + body.message();
	}

	record Envelope(boolean success, String errorCode, String message, Data data) {
	}

	record Data(List<Item> items) {
	}

	record Item(Long itemId, String title, String author, String publisher, String link, String cover,
			String sideCover, Integer pages) {
	}

}
