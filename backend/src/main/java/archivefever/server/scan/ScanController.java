package archivefever.server.scan;

import archivefever.server.library.LibraryService;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/scans")
class ScanController {

	private final ScanService scans;

	ScanController(ScanService scans) {
		this.scans = scans;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	ScanService.ScanView create() {
		return scans.create();
	}

	@GetMapping("/{id}")
	ScanService.ScanView get(@PathVariable String id) {
		return scans.get(id);
	}

	/** The photo is processed before this returns, so the answer already carries the outcome. */
	@PostMapping("/{id}/image")
	ScanService.ScanView image(@PathVariable String id, @RequestParam("file") MultipartFile file) throws IOException {
		return scans.attachPhoto(id, file.getBytes());
	}

	@PostMapping("/{id}/cancel")
	ScanService.ScanView cancel(@PathVariable String id) {
		return scans.cancel(id);
	}

	@PostMapping("/{id}/confirm")
	LibraryService.ShelfBook confirm(@PathVariable String id) {
		return scans.confirm(id);
	}

}
