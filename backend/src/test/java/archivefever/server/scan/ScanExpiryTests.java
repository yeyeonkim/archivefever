package archivefever.server.scan;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;

/** A scan nobody sends a photo for must not wait forever. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "archivefever.scan.ttl=0s")
@ActiveProfiles("test")
class ScanExpiryTests {

	private static final ParameterizedTypeReference<Map<String, Object>> OBJECT = new ParameterizedTypeReference<>() {
	};

	@Value("${local.server.port}")
	private int port;

	@Test
	void aPendingScanExpires() throws Exception {
		RestClient client = RestClient.builder().baseUrl("http://localhost:" + port)
				.defaultStatusHandler(status -> true, (request, response) -> { }).build();
		String id = (String) client.post().uri("/api/scans").retrieve().body(OBJECT).get("id");
		Thread.sleep(30);

		assertThat(client.get().uri("/api/scans/{id}", id).retrieve().body(OBJECT)).containsEntry("status", "EXPIRED");
		assertThat(client.post().uri("/api/scans/{id}/cancel", id).retrieve().body(OBJECT))
				.containsEntry("status", "EXPIRED");
	}

}
