package archivefever.server.auth;

import archivefever.server.user.CurrentUser;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Finds out who a request comes from, and turns away requests for someone's data that
 * come from no one.
 */
@Component
class SessionFilter extends OncePerRequestFilter {

	static final String COOKIE = "AF_SESSION";

	/**
	 * The phone app delivers a barcode photo to a scan, or calls the scan off, without the
	 * web view's cookie. The scan's random id, which only the signed-in client that
	 * created it was told, is its permission.
	 */
	private static final Pattern SCAN_DELIVERY = Pattern.compile("/api/scans/[^/]+/(image|cancel)");

	private final SessionService sessions;

	SessionFilter(SessionService sessions) {
		this.sessions = sessions;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		Optional<String> user = sessions.userOf(token(request));
		user.ifPresent(id -> request.setAttribute(CurrentUser.ATTRIBUTE, id));
		if (user.isEmpty() && needsUser(request.getMethod(), request.getRequestURI())) {
			response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
			response.setContentType(MediaType.APPLICATION_JSON_VALUE);
			response.getWriter().write("{\"error\":\"NOT_SIGNED_IN\"}");
			return;
		}
		chain.doFilter(request, response);
	}

	/** Pages, the Swing client, book images and signing in are open; everything else under /api is someone's. */
	static boolean needsUser(String method, String path) {
		if (!path.startsWith("/api/")) {
			return false;
		}
		if (path.equals("/api/health") || path.startsWith("/api/images/") || path.startsWith("/api/auth/")) {
			return false;
		}
		return !(method.equals("POST") && SCAN_DELIVERY.matcher(path).matches());
	}

	static String token(HttpServletRequest request) {
		Cookie[] cookies = request.getCookies();
		if (cookies != null) {
			for (Cookie cookie : cookies) {
				if (cookie.getName().equals(COOKIE)) {
					return cookie.getValue();
				}
			}
		}
		return null;
	}

}
