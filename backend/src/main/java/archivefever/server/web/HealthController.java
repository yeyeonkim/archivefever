package archivefever.server.web;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Lets the mobile app check that it has the right server address before opening the web view. */
@RestController
class HealthController {

	@GetMapping("/api/health")
	Map<String, String> health() {
		return Map.of("status", "ok");
	}

}
