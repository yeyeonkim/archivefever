package archivefever.server.book;

import static org.assertj.core.api.Assertions.assertThat;

import archivefever.server.image.BookImageService;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * A server first run without a YES24 key caches stub books. Once the key is added, those
 * rows must give way to the real catalogue instead of being served forever.
 */
@SpringBootTest
@ActiveProfiles("test")
class BookSourceChangeTests {

	/** A lookup whose source and answer the test can change, like adding the API key later. */
	static class SwitchableLookup implements BookLookup {

		String source;

		String title;

		int calls;

		@Override
		public String source() {
			return source;
		}

		@Override
		public Optional<Book> findByIsbn13(String isbn13) {
			calls++;
			return Optional.ofNullable(title)
					.map(found -> new Book(isbn13, null, found, "저자", "출판사", "https://www.yes24.com/", null, null, 200));
		}

	}

	@TestConfiguration
	static class Config {

		@Bean
		@Primary
		SwitchableLookup switchableLookup() {
			return new SwitchableLookup();
		}

	}

	@DynamicPropertySource
	static void freshDataDirectory(DynamicPropertyRegistry registry) throws IOException {
		// Stored pictures from an earlier run would hide whether this run redrew them.
		String directory = Files.createTempDirectory("book-source-test").toString();
		registry.add("archivefever.data-dir", () -> directory);
	}

	@Autowired
	private BookService books;

	@Autowired
	private BookImageService images;

	@Autowired
	private SwitchableLookup lookup;

	@BeforeEach
	void startWithoutAKey() {
		lookup.source = "stub";
		lookup.title = "스텁 도서";
		lookup.calls = 0;
	}

	@Test
	void aBookCachedFromAnotherSourceIsLookedUpAgain() {
		String isbn = "9791100000038";
		assertThat(books.find(isbn)).map(Book::title).hasValue("스텁 도서");
		byte[] stubCover = images.png(isbn, BookImageService.Kind.COVER).orElseThrow();
		assertThat(books.find(isbn)).isPresent();
		assertThat(lookup.calls).as("the second request is answered from the cache").isEqualTo(1);

		lookup.source = "yes24";
		lookup.title = "소년이 온다";

		assertThat(books.find(isbn)).map(Book::title).hasValue("소년이 온다");
		assertThat(books.cached(isbn)).map(Book::title).hasValue("소년이 온다");
		assertThat(images.png(isbn, BookImageService.Kind.COVER).orElseThrow())
				.as("the stored cover is redrawn from the new data").isNotEqualTo(stubCover);
		books.find(isbn);
		assertThat(lookup.calls).as("the replaced row is cached like any other").isEqualTo(2);
	}

	@Test
	void aStaleBookTheNewSourceDoesNotListIsKeptAsItWas() {
		String isbn = "9791100000045";
		assertThat(books.find(isbn)).isPresent();

		lookup.source = "yes24";
		lookup.title = null;

		assertThat(books.find(isbn)).isEmpty();
		// It may be on someone's shelf, which still needs something to show.
		assertThat(books.cached(isbn)).map(Book::title).hasValue("스텁 도서");
	}

}
