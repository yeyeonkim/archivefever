package archivefever.server.book;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
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
 * Exercises the client against a local HTTP server that answers in the shape documented at
 * https://developers.yes24.com/api-doc/goods-item-detail. The success body is hand-written
 * from that documentation; replace it with a saved real answer once an API key is available.
 */
class Yes24BookLookupTests {

	private static final String ISBN = "9788936434120";

	private static final String FOUND = """
			{
			  "success": true,
			  "message": null,
			  "errorCode": null,
			  "data": {
			    "meta": {"apiTitle": "상품 상세", "version": "v1", "query": "9788936434120"},
			    "currentPage": 1,
			    "pageSize": 1,
			    "totalCount": 1,
			    "items": [
			      {
			        "itemId": 13137546,
			        "title": "소년이 온다",
			        "subTitle": "한강 장편소설",
			        "author": "한강",
			        "publisher": "창비",
			        "isbn10": "8936434128",
			        "isbn13": "9788936434120",
			        "publishDate": "2014-05-19",
			        "cover": "https://image.yes24.com/goods/13137546/L",
			        "sideCover": "https://image.yes24.com/goods/13137546/SIDE/L",
			        "backCover": "https://image.yes24.com/goods/13137546/BACK/L",
			        "link": "https://www.yes24.com/Product/Goods/13137546",
			        "mobileLink": "https://m.yes24.com/Goods/Detail/13137546",
			        "pages": 216,
			        "contentDetail": {"bookIntroduction": null}
			      }
			    ]
			  }
			}
			""";

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

		assertThat(book).hasValue(new Book(ISBN, 13137546L, "소년이 온다", "한강", "창비",
				"https://www.yes24.com/Product/Goods/13137546", "https://image.yes24.com/goods/13137546/L",
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

	private static String error(String code, String message) {
		return "{\"success\":false,\"errorCode\":\"" + code + "\",\"message\":\"" + message + "\",\"data\":null}";
	}

}
