package archivefever.server.image;

import java.time.Duration;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
class ImageController {

	private final BookImageService images;

	ImageController(BookImageService images) {
		this.images = images;
	}

	@GetMapping("/api/images/{isbn13}/cover.png")
	ResponseEntity<byte[]> cover(@PathVariable String isbn13) {
		return respond(isbn13, BookImageService.Kind.COVER);
	}

	@GetMapping("/api/images/{isbn13}/spine.png")
	ResponseEntity<byte[]> spine(@PathVariable String isbn13) {
		return respond(isbn13, BookImageService.Kind.SPINE);
	}

	private ResponseEntity<byte[]> respond(String isbn13, BookImageService.Kind kind) {
		return images.png(isbn13, kind)
				.map(png -> ResponseEntity.ok()
						.contentType(MediaType.IMAGE_PNG)
						.cacheControl(CacheControl.maxAge(Duration.ofHours(1)))
						.body(png))
				.orElseGet(() -> ResponseEntity.notFound().build());
	}

}
