package archivefever.server.auth;

import archivefever.server.user.CurrentUser;
import archivefever.server.user.UserService;
import archivefever.server.user.UserService.UserView;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * Signing in and out. A sign-in ends with the session cookie being set; from then on the
 * browser sends it with every request and {@link SessionFilter} recognises the user.
 */
@RestController
class AuthController {

	/** What the sign-in page should offer. */
	record Options(boolean kakao, boolean development) {
	}

	record DevelopmentSignIn(String name) {
	}

	private static final Logger log = LoggerFactory.getLogger(AuthController.class);

	/** Remembers, across the trip to Kakao and back, which sign-in attempt the answer belongs to. */
	private static final String STATE_COOKIE = "AF_KAKAO_STATE";

	private static final String KAKAO_PATH = "/auth/kakao";

	private final KakaoClient kakao;

	private final UserService users;

	private final SessionService sessions;

	private final CurrentUser currentUser;

	private final boolean developmentSignIn;

	/** @param developmentSetting "true" or "false"; left blank, signing in by name is offered only while there is no Kakao key */
	AuthController(KakaoClient kakao, UserService users, SessionService sessions, CurrentUser currentUser,
			@Value("${archivefever.auth.dev-login}") String developmentSetting) {
		this.kakao = kakao;
		this.users = users;
		this.sessions = sessions;
		this.currentUser = currentUser;
		this.developmentSignIn = developmentSetting.isBlank() ? !kakao.available()
				: Boolean.parseBoolean(developmentSetting.strip());
	}

	@GetMapping("/api/auth/options")
	Options options() {
		return new Options(kakao.available(), developmentSignIn);
	}

	/** The signed-in user. Without a session the filter has already answered 401. */
	@GetMapping("/api/me")
	ResponseEntity<UserView> me() {
		return users.find(currentUser.id()).map(ResponseEntity::ok)
				.orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
	}

	@PostMapping("/api/auth/dev")
	ResponseEntity<UserView> signInForDevelopment(HttpServletRequest request, @RequestBody DevelopmentSignIn signIn) {
		if (!developmentSignIn) {
			return ResponseEntity.notFound().build();
		}
		UserView user = users.signInForDevelopment(signIn.name());
		return ResponseEntity.ok()
				.header(HttpHeaders.SET_COOKIE, sessionCookie(request, sessions.start(user.id()))).body(user);
	}

	@PostMapping("/api/auth/logout")
	ResponseEntity<Void> signOut(HttpServletRequest request) {
		sessions.end(SessionFilter.token(request));
		return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, expired(SessionFilter.COOKIE, "/")).build();
	}

	/** Sends the browser to Kakao to sign in. */
	@GetMapping(KAKAO_PATH + "/login")
	ResponseEntity<Void> kakaoSignIn(HttpServletRequest request) {
		if (!kakao.available()) {
			return redirect("/?login=unavailable");
		}
		String state = UUID.randomUUID().toString();
		ResponseCookie remember = ResponseCookie.from(STATE_COOKIE, state)
				.httpOnly(true).path(KAKAO_PATH).maxAge(Duration.ofMinutes(10)).sameSite("Lax").build();
		return ResponseEntity.status(HttpStatus.FOUND)
				.header(HttpHeaders.SET_COOKIE, remember.toString())
				.location(URI.create(kakao.authorizeUrl(callbackAddress(request), state)))
				.build();
	}

	/** Where Kakao sends the browser back to, with a code if the person signed in and agreed. */
	@GetMapping(KAKAO_PATH + "/callback")
	ResponseEntity<Void> kakaoCallback(HttpServletRequest request,
			@RequestParam(required = false) String code, @RequestParam(required = false) String state,
			@RequestParam(required = false) String error) {
		String expected = cookie(request, STATE_COOKIE);
		String forget = expired(STATE_COOKIE, KAKAO_PATH);
		if (code == null || state == null || expected == null || !same(state, expected)) {
			// Declined at Kakao (error=access_denied), or an answer to a sign-in this browser never started.
			log.info("Kakao sign-in not completed: {}", error == null ? "no code or unknown state" : error);
			return ResponseEntity.status(HttpStatus.FOUND).header(HttpHeaders.SET_COOKIE, forget)
					.location(URI.create("/?login=failed")).build();
		}
		try {
			KakaoClient.Member member = kakao.member(code, callbackAddress(request));
			UserView user = users.signInWithKakao(member.id(), member.nickname());
			return ResponseEntity.status(HttpStatus.FOUND)
					.header(HttpHeaders.SET_COOKIE, forget)
					.header(HttpHeaders.SET_COOKIE, sessionCookie(request, sessions.start(user.id())))
					.location(URI.create("/")).build();
		}
		catch (KakaoClient.KakaoException exception) {
			log.warn("Kakao sign-in failed: {}", exception.getMessage());
			return ResponseEntity.status(HttpStatus.FOUND).header(HttpHeaders.SET_COOKIE, forget)
					.location(URI.create("/?login=failed")).build();
		}
	}

	/**
	 * This server's callback address as the browser reaches it. It must be registered as a
	 * Redirect URI in the Kakao console exactly as it comes out here.
	 */
	private static String callbackAddress(HttpServletRequest request) {
		return ServletUriComponentsBuilder.fromRequestUri(request)
				.replacePath(KAKAO_PATH + "/callback").replaceQuery(null).build().toUriString();
	}

	private String sessionCookie(HttpServletRequest request, String token) {
		// Secure only when the browser came over HTTPS (through a tunnel): on the local
		// network the server is reached over plain HTTP, where a Secure cookie would be dropped.
		return ResponseCookie.from(SessionFilter.COOKIE, token).httpOnly(true).secure(request.isSecure())
				.path("/").maxAge(sessions.timeToLive()).sameSite("Lax").build().toString();
	}

	private static String expired(String name, String path) {
		return ResponseCookie.from(name, "").httpOnly(true).path(path).maxAge(Duration.ZERO).sameSite("Lax").build().toString();
	}

	private static ResponseEntity<Void> redirect(String location) {
		return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(location)).build();
	}

	private static String cookie(HttpServletRequest request, String name) {
		Cookie[] cookies = request.getCookies();
		if (cookies != null) {
			for (Cookie cookie : cookies) {
				if (cookie.getName().equals(name)) {
					return cookie.getValue();
				}
			}
		}
		return null;
	}

	private static boolean same(String a, String b) {
		return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
	}

}
