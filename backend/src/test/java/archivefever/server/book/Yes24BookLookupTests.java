package archivefever.server.book;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Exercises the client against a local HTTP server that answers as the YES24 Open API does
 * (https://developers.yes24.com/api-doc/goods-item-detail and .../goods-item-list).
 */
class Yes24BookLookupTests {

	private static final String ISBN = "9788936434120";

	/** A real answer for this ISBN, saved from the API with the publisher's descriptive text removed. */
	private static final String FOUND = fixture("/yes24/item-detail.json");

	/**
	 * A real answer to a search for "소년이 온다" over every category, saved from the API with ten
	 * of its items and, of each, only what a listing needs.
	 */
	private static final String SEARCHED = fixture("/yes24/item-list.json");

	private record Answer(int status, String body) {
	}

	private final Deque<Answer> answers = new ArrayDeque<>();

	private final List<String> requests = new ArrayList<>();

	private final List<String> keys = new ArrayList<>();

	private HttpServer server;

	private Yes24BookLookup lookup;

	@BeforeEach
	void startServer() throws IOException {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/", exchange -> {
			requests.add(exchange.getRequestURI().toString());
			keys.add(exchange.getRequestHeaders().getFirst("X-Api-Key"));
			Answer answer = answers.removeFirst();
			byte[] body = answer.body().getBytes(StandardCharsets.UTF_8);
			exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
			exchange.sendResponseHeaders(answer.status(), body.length);
			exchange.getResponseBody().write(body);
			exchange.close();
		});
		server.start();
		lookup = new Yes24BookLookup("http://127.0.0.1:" + server.getAddress().getPort() + "/v1", "test-key");
	}

	@AfterEach
	void stopServer() {
		server.stop(0);
	}

	@Test
	void mapsTheBookAndSendsTheKey() {
		answers.add(new Answer(200, FOUND));

		Optional<Book> book = lookup.findByIsbn13(ISBN);

		assertThat(book).hasValue(new Book(ISBN, 13137546L, "소년이 온다", "한강 저", "창비",
				"https://www.yes24.com/product/goods/13137546", "https://image.yes24.com/goods/13137546/L",
				"https://image.yes24.com/goods/13137546/SIDE/L", 216));
		assertThat(requests).containsExactly("/v1/goods/itemDetail?searchType=ISBN13&query=" + ISBN + "&detail=Y");
		assertThat(keys).containsExactly("test-key");
	}

	@Test
	void anUnknownIsbnIsEmptyNotAnError() {
		answers.add(new Answer(404, error("GOODS_002", "ISBN13에 해당하는 상품을 찾을 수 없습니다.")));

		assertThat(lookup.findByIsbn13(ISBN)).isEmpty();
	}

	@Test
	void aRejectedKeyIsAConfigurationFailure() {
		answers.add(new Answer(401, error("AUTH_002", "유효하지 않은 API Key")));

		assertThatThrownBy(() -> lookup.findByIsbn13(ISBN))
				.isInstanceOfSatisfying(BookLookup.LookupException.class,
						exception -> assertThat(exception.failure()).isEqualTo(BookLookup.Failure.CONFIG))
				.hasMessageContaining("AUTH_002");
	}

	@Test
	void aBurstLimitIsRetriedOnce() {
		answers.add(new Answer(429, error("RATE_001", "초당 호출 한도 초과")));
		answers.add(new Answer(200, FOUND));

		assertThat(lookup.findByIsbn13(ISBN)).isPresent();
		assertThat(requests).hasSize(2);
	}

	@Test
	void anExhaustedLimitIsReportedAsUnavailable() {
		answers.add(new Answer(429, error("RATE_002", "일일 호출 한도 초과")));
		answers.add(new Answer(429, error("RATE_002", "일일 호출 한도 초과")));

		assertThatThrownBy(() -> lookup.findByIsbn13(ISBN))
				.isInstanceOfSatisfying(BookLookup.LookupException.class,
						exception -> assertThat(exception.failure()).isEqualTo(BookLookup.Failure.LOOKUP_UNAVAILABLE));
	}

	@Test
	void aServerErrorIsReportedAsUnavailable() {
		answers.add(new Answer(503, error("UPSTREAM_UNAVAILABLE", "일시적 오류")));

		assertThatThrownBy(() -> lookup.findByIsbn13(ISBN))
				.isInstanceOfSatisfying(BookLookup.LookupException.class,
						exception -> assertThat(exception.failure()).isEqualTo(BookLookup.Failure.LOOKUP_UNAVAILABLE));
	}

	@Test
	void anUnreachableServerIsReportedAsUnavailable() {
		server.stop(0);

		assertThatThrownBy(() -> lookup.findByIsbn13(ISBN))
				.isInstanceOfSatisfying(BookLookup.LookupException.class,
						exception -> assertThat(exception.failure()).isEqualTo(BookLookup.Failure.LOOKUP_UNAVAILABLE));
	}

	@Test
	void aSearchFindsThePaperBooksThatHaveAnIsbn() {
		answers.add(new Answer(200, SEARCHED));

		List<Book> found = lookup.search("소년이 온다");

		// Ten matches: the sets, which have no ISBN, and the e-books are left out; the foreign editions stay.
		assertThat(found).extracting(Book::isbn13)
				.containsExactly("9788936434120", "9791170876045", "9781101906743", "9781846275975");
		// A listing does not say how many pages a book has; looking the book up by ISBN does.
		assertThat(found.get(0)).isEqualTo(new Book("9788936434120", 13137546L, "소년이 온다", "한강 저", "창비",
				"https://www.yes24.com/product/goods/13137546", "https://image.yes24.com/goods/13137546/L",
				"https://image.yes24.com/goods/13137546/SIDE/L", null));
		assertThat(requests).singleElement().satisfies(request -> assertThat(URLDecoder.decode(request, StandardCharsets.UTF_8))
				.isEqualTo("/v1/goods/itemList?query=소년이 온다&category=ALL&pageSize=40"));
		assertThat(keys).containsExactly("test-key");
	}

	@Test
	void theWordsOfASearchAreSentAsOneValueWhateverTheyContain() {
		answers.add(new Answer(404, error("SEARCH_001", "검색 결과가 없습니다.")));

		lookup.search("1+1 & more=?");

		assertThat(requests).containsExactly("/v1/goods/itemList?query=1%2B1%20%26%20more%3D%3F&category=ALL&pageSize=40");
	}

	@Test
	void aSearchWithNoMatchIsEmptyNotAnError() {
		answers.add(new Answer(404, error("SEARCH_001", "검색 결과가 없습니다.")));

		assertThat(lookup.search("없는 책")).isEmpty();
	}

	@Test
	void aBookListedTwiceIsFoundOnce() {
		answers.add(new Answer(200, "{\"success\":true,\"data\":{\"items\":["
				+ "{\"itemId\":1,\"title\":\"먼저 나온 것\",\"isbn13\":\"9788936434120\",\"goodsType\":\"도서\"},"
				+ "{\"itemId\":2,\"title\":\"같은 책\",\"isbn13\":\"9788936434120\",\"goodsType\":\"도서\"},"
				+ "{\"itemId\":3,\"title\":\"ISBN이 틀린 것\",\"isbn13\":\"9788936434121\",\"goodsType\":\"도서\"}]}}"));

		assertThat(lookup.search("소년")).extracting(Book::title).containsExactly("먼저 나온 것");
	}

	@Test
	void aSearchFailsTheWayALookupDoes() {
		answers.add(new Answer(401, error("AUTH_002", "유효하지 않은 API Key")));
		assertThatThrownBy(() -> lookup.search("소년"))
				.isInstanceOfSatisfying(BookLookup.LookupException.class,
						exception -> assertThat(exception.failure()).isEqualTo(BookLookup.Failure.CONFIG));

		answers.add(new Answer(503, error("UPSTREAM_UNAVAILABLE", "일시적 오류")));
		assertThatThrownBy(() -> lookup.search("소년"))
				.isInstanceOfSatisfying(BookLookup.LookupException.class,
						exception -> assertThat(exception.failure()).isEqualTo(BookLookup.Failure.LOOKUP_UNAVAILABLE));
	}

	private static String fixture(String path) {
		try (InputStream in = Yes24BookLookupTests.class.getResourceAsStream(path)) {
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
		catch (IOException exception) {
			throw new UncheckedIOException(exception);
		}
	}

	private static String error(String code, String message) {
		return "{\"success\":false,\"errorCode\":\"" + code + "\",\"message\":\"" + message + "\",\"data\":null}";
	}

}
