package archivefever.server.scan;

import static org.assertj.core.api.Assertions.assertThat;

import archivefever.server.auth.SignedIn;
import archivefever.server.book.StubBookLookup;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

/** The whole book registration flow over HTTP, with stub catalogue data. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ScanFlowTests {

	private static final ParameterizedTypeReference<Map<String, Object>> OBJECT = new ParameterizedTypeReference<>() {
	};

	private static final ParameterizedTypeReference<List<Map<String, Object>>> LIST = new ParameterizedTypeReference<>() {
	};

	@Value("${local.server.port}")
	private int port;

	private RestClient client;

	@BeforeEach
	void createClient() {
		// Signed in as the user the shelf in these tests belongs to.
		client = SignedIn.client(port, "demo");
	}

	@Test
	void aBarcodePhotoBecomesABookOnTheShelf() throws Exception {
		String isbn = "9788936434120";
		ResponseEntity<Map<String, Object>> created = client.post().uri("/api/scans").retrieve().toEntity(OBJECT);
		assertThat(created.getStatusCode().value()).isEqualTo(201);
		assertThat(created.getBody()).containsEntry("status", "PENDING");
		String id = (String) created.getBody().get("id");

		Map<String, Object> resolved = upload(id, TestPhotos.png(TestPhotos.backCover(isbn))).getBody();
		assertThat(resolved).containsEntry("status", "RESOLVED");
		Map<?, ?> book = (Map<?, ?>) resolved.get("book");
		assertThat(book.get("isbn13")).isEqualTo(isbn);
		assertThat(book.get("title")).isEqualTo("스텁 도서 " + isbn);
		assertThat(book.get("link")).isEqualTo("https://www.yes24.com/");
		assertThat(book.get("coverUrl")).isEqualTo("/api/images/" + isbn + "/cover.png");

		// The Swing client polls this while the phone uploads.
		assertThat(client.get().uri("/api/scans/{id}", id).retrieve().body(OBJECT)).isEqualTo(resolved);

		ResponseEntity<Map<String, Object>> confirmed = post("/api/scans/{id}/confirm", id);
		assertThat(confirmed.getStatusCode().value()).isEqualTo(200);
		assertThat(confirmed.getBody()).containsEntry("isbn13", isbn)
				.containsEntry("spineUrl", "/api/images/" + isbn + "/spine.png");
		int spineWidth = (Integer) confirmed.getBody().get("spineWidth");
		assertThat(spineWidth).isBetween(14, 60);

		// Confirming twice must not put the book on the shelf twice.
		assertThat(post("/api/scans/{id}/confirm", id).getStatusCode().value()).isEqualTo(200);
		List<Map<String, Object>> shelf = client.get().uri("/api/library/books").retrieve().body(LIST);
		assertThat(shelf).filteredOn(entry -> isbn.equals(entry.get("isbn13"))).hasSize(1);
		assertThat(client.get().uri("/api/scans/{id}", id).retrieve().body(OBJECT)).containsEntry("status", "CONFIRMED");

		BufferedImage spine = image("/api/images/" + isbn + "/spine.png");
		assertThat(spine.getHeight()).isEqualTo(344);
		assertThat(Math.round(spine.getWidth() / 2f)).isEqualTo(spineWidth);
		assertThat(image("/api/images/" + isbn + "/cover.png").getHeight()).isEqualTo(400);
	}

	@Test
	void aPhotoWithoutABarcodeEndsTheScan() {
		String id = newScan();

		assertThat(upload(id, TestPhotos.png(TestPhotos.blank())).getBody())
				.containsEntry("status", "NO_BARCODE").containsEntry("book", null);
		// The outcome is final; another photo needs a new scan.
		assertThat(upload(id, TestPhotos.png(TestPhotos.blank())).getStatusCode().value()).isEqualTo(409);
		assertThat(post("/api/scans/{id}/confirm", id).getStatusCode().value()).isEqualTo(409);
	}

	@Test
	void aFileThatIsNotAPictureIsReportedAsUnreadable() {
		String id = newScan();

		assertThat(upload(id, "not a picture".getBytes()).getBody())
				.containsEntry("status", "NO_BARCODE").containsEntry("reason", "UNREADABLE_IMAGE");
	}

	@Test
	void anIsbnTheCatalogueDoesNotListIsNotFound() {
		String id = newScan();

		assertThat(upload(id, TestPhotos.png(TestPhotos.backCover(StubBookLookup.UNKNOWN_ISBN))).getBody())
				.containsEntry("status", "NOT_FOUND").containsEntry("book", null);
		assertThat(post("/api/scans/{id}/confirm", id).getStatusCode().value()).isEqualTo(409);
	}

	@Test
	void aCancelledScanRefusesAPhoto() {
		String id = newScan();

		assertThat(post("/api/scans/{id}/confirm", id).getStatusCode().value()).isEqualTo(409);
		assertThat(post("/api/scans/{id}/cancel", id).getBody()).containsEntry("status", "CANCELLED");
		assertThat(upload(id, TestPhotos.png(TestPhotos.backCover("9788936434120"))).getStatusCode().value()).isEqualTo(409);
	}

	@Test
	void unknownScansAndImagesAreNotFound() {
		assertThat(client.get().uri("/api/scans/nope").retrieve().toBodilessEntity().getStatusCode().value()).isEqualTo(404);
		assertThat(post("/api/scans/{id}/cancel", "nope").getStatusCode().value()).isEqualTo(404);
		// A valid ISBN nobody has looked up, and a value that is not an ISBN at all.
		assertThat(client.get().uri("/api/images/9791100000007/spine.png").retrieve().toBodilessEntity()
				.getStatusCode().value()).isEqualTo(404);
		assertThat(client.get().uri("/api/images/abc/cover.png").retrieve().toBodilessEntity()
				.getStatusCode().value()).isEqualTo(404);
	}

	private String newScan() {
		return (String) client.post().uri("/api/scans").retrieve().body(OBJECT).get("id");
	}

	private ResponseEntity<Map<String, Object>> post(String uri, String id) {
		return client.post().uri(uri, id).retrieve().toEntity(OBJECT);
	}

	private ResponseEntity<Map<String, Object>> upload(String id, byte[] photo) {
		MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
		form.add("file", new ByteArrayResource(photo) {
			@Override
			public String getFilename() {
				return "photo.png";
			}
		});
		return client.post().uri("/api/scans/{id}/image", id)
				.contentType(MediaType.MULTIPART_FORM_DATA).body(form)
				.retrieve().toEntity(OBJECT);
	}

	private BufferedImage image(String path) throws Exception {
		ResponseEntity<byte[]> response = client.get().uri(path).retrieve().toEntity(byte[].class);
		assertThat(response.getStatusCode().value()).isEqualTo(200);
		assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.IMAGE_PNG);
		return ImageIO.read(new ByteArrayInputStream(response.getBody()));
	}

}
