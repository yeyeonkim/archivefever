package archivefever.server.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** The browser JVM depends on these exact responses to find and read the Swing client JAR. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class SwingWebConfigTests {

	private static final byte[] JAR_BYTES = "0123456789abcdefghijklmnopqrstuvwxyz".getBytes();

	@Value("${local.server.port}")
	private int port;

	private final HttpClient client = HttpClient.newHttpClient();

	@DynamicPropertySource
	static void swingDirectory(DynamicPropertyRegistry registry) throws IOException {
		Path directory = Files.createTempDirectory("swing-web-test");
		Files.writeString(directory.resolve("manifest.json"), "{\"jar\":\"client-abc123.jar\"}");
		Files.write(directory.resolve("client-abc123.jar"), JAR_BYTES);
		registry.add("archivefever.swing-dir", directory::toString);
	}

	@Test
	void manifestIsServedAndNeverCached() throws Exception {
		HttpResponse<String> response = get("/swing/manifest.json", null, HttpResponse.BodyHandlers.ofString());

		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(response.body()).contains("client-abc123.jar");
		assertThat(response.headers().firstValue("Cache-Control")).hasValue("no-store");
	}

	@Test
	void jarAnswersByteRangeRequests() throws Exception {
		HttpResponse<byte[]> response = get("/swing/client-abc123.jar", "bytes=10-15", HttpResponse.BodyHandlers.ofByteArray());

		assertThat(response.statusCode()).isEqualTo(206);
		assertThat(new String(response.body())).isEqualTo("abcdef");
		assertThat(response.headers().firstValue("Content-Range")).hasValue("bytes 10-15/" + JAR_BYTES.length);
		assertThat(response.headers().firstValue("Cache-Control").orElse("")).contains("immutable");
	}

	@Test
	void hostPageIsServedAtTheRoot() throws Exception {
		HttpResponse<String> response = get("/", null, HttpResponse.BodyHandlers.ofString());

		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(response.body()).contains("host.js");
	}

	private <T> HttpResponse<T> get(String path, String range, HttpResponse.BodyHandler<T> handler) throws Exception {
		HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
		if (range != null) {
			request.header("Range", range);
		}
		return client.send(request.build(), handler);
	}

}
