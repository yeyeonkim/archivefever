package archivefever.server.web;

import java.nio.file.Path;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Serves the Swing client JAR built by the Gradle task {@code swingWeb}.
 *
 * The browser JVM reads the JAR with HTTP range requests, which Spring's resource
 * handling answers natively. The JAR and the REST API share one origin, so the
 * Swing client needs no CORS setup.
 */
@Configuration
class SwingWebConfig implements WebMvcConfigurer {

	private final String location;

	SwingWebConfig(@Value("${archivefever.swing-dir}") String swingDir) {
		String uri = Path.of(swingDir).toAbsolutePath().normalize().toUri().toString();
		// A directory location must end with a slash, even before the directory exists.
		this.location = uri.endsWith("/") ? uri : uri + "/";
	}

	@Override
	public void addResourceHandlers(ResourceHandlerRegistry registry) {
		// manifest.json names the current JAR, so it must never be served stale. The pattern
		// needs a wildcard: the part it matches is what gets looked up in the directory.
		registry.addResourceHandler("/swing/*.json")
				.addResourceLocations(location)
				.setCacheControl(CacheControl.noStore());
		// JAR names contain a content hash, so each one can be cached for good.
		registry.addResourceHandler("/swing/**")
				.addResourceLocations(location)
				.setCacheControl(CacheControl.maxAge(Duration.ofDays(30)).immutable());
	}

}
