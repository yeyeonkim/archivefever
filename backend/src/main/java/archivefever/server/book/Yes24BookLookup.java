package archivefever.server.book;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriBuilder;

/**
 * Looks books up through the YES24 Open API (https://developers.yes24.com).
 *
 * The API wraps every answer as {success, errorCode, message, data}; a lookup returns
 * its single book in data.items, and a search its matches.
 */
public class Yes24BookLookup implements BookLookup {

	private static final Duration RATE_LIMIT_PAUSE = Duration.ofMillis(1200);

	/**
	 * How many matches a search asks the catalogue for. More than are shown, because the
	 * matches include what a shelf cannot hold: sets without an ISBN, e-books, records.
	 */
	private static final int SEARCH_PAGE = 40;

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
	public String source() {
		return "yes24";
	}

	@Override
	public Optional<Book> findByIsbn13(String isbn13) {
		List<Item> items = ask(uri -> uri.path("/goods/itemDetail")
				.queryParam("searchType", "ISBN13")
				.queryParam("query", isbn13)
				// detail=Y adds the page count, used to size generated spines.
				.queryParam("detail", "Y")
				.build());
		return items.isEmpty() ? Optional.empty() : Optional.of(book(isbn13, items.get(0)));
	}

	@Override
	public List<Book> search(String words) {
		List<Item> items = ask(uri -> uri.path("/goods/itemList")
				// Passed as a variable, so that it is encoded whatever it contains.
				.queryParam("query", "{words}")
				.queryParam("category", "ALL")
				.queryParam("pageSize", SEARCH_PAGE)
				.build(words));
		// The same book can be listed twice; the first, better match stands.
		Map<String, Book> found = new LinkedHashMap<>();
		for (Item item : items) {
			if (found.size() < SEARCH_RESULTS && Isbn13.isValid(item.isbn13()) && !"eBook".equalsIgnoreCase(item.goodsType())) {
				found.putIfAbsent(item.isbn13(), book(item.isbn13(), item));
			}
		}
		return List.copyOf(found.values());
	}

	/** The items of an answer; none when the catalogue says it has nothing (404). */
	private List<Item> ask(Function<UriBuilder, URI> address) {
		ResponseEntity<Envelope> response = request(address);
		if (response.getStatusCode().value() == 429) {
			// The basic key allows 10 calls a second; one short pause clears a burst.
			pause();
			response = request(address);
		}
		int status = response.getStatusCode().value();
		Envelope body = response.getBody();
		if (status == 404) {
			return List.of();
		}
		if (status == 401 || status == 403) {
			throw new LookupException(Failure.CONFIG, "YES24 rejected the API key: " + describe(body), null);
		}
		if (status != 200 || body == null || !body.success()) {
			throw new LookupException(Failure.LOOKUP_UNAVAILABLE, "YES24 answered " + status + ": " + describe(body), null);
		}
		return body.data() == null || body.data().items() == null ? List.of() : body.data().items();
	}

	private ResponseEntity<Envelope> request(Function<UriBuilder, URI> address) {
		try {
			return client.get()
					.uri(address)
					.retrieve()
					// Error answers carry the same envelope, so read them instead of throwing.
					.onStatus(HttpStatusCode::isError, (request, errorResponse) -> { })
					.toEntity(Envelope.class);
		}
		catch (RestClientException exception) {
			throw new LookupException(Failure.LOOKUP_UNAVAILABLE, "YES24 could not be reached", exception);
		}
	}

	private static Book book(String isbn13, Item item) {
		return new Book(isbn13, item.itemId(), item.title(), item.author(), item.publisher(),
				item.link(), item.cover(), item.sideCover(), item.pages());
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

	/** @param goodsType what kind of thing it is: a book, an e-book, a foreign book, a record */
	record Item(Long itemId, String title, String author, String publisher, String link, String cover,
			String sideCover, Integer pages, String isbn13, String goodsType) {
	}

}
