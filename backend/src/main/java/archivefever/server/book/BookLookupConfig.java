package archivefever.server.book;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class BookLookupConfig {

	private static final Logger log = LoggerFactory.getLogger(BookLookupConfig.class);

	@Bean
	BookLookup bookLookup(@Value("${archivefever.yes24.enabled}") boolean enabled,
			@Value("${archivefever.yes24.base-url}") String baseUrl,
			@Value("${archivefever.yes24.api-key}") String apiKey) {
		if (enabled && !apiKey.isBlank()) {
			log.info("Book lookups use the YES24 Open API at {}", baseUrl);
			return new Yes24BookLookup(baseUrl, apiKey.strip());
		}
		log.warn("No YES24 API key configured: book lookups return stub books");
		return new StubBookLookup();
	}

}
