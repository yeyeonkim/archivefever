package archivefever.server.auth;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Kakao Login over its REST API (https://developers.kakao.com/docs/latest/ko/kakaologin/rest-api):
 * the browser is sent to kauth.kakao.com to sign in, comes back with a code, the code is
 * exchanged for an access token, and the token is used once to ask who the member is.
 */
class HttpKakaoClient implements KakaoClient {

	private static final ParameterizedTypeReference<Map<String, Object>> OBJECT = new ParameterizedTypeReference<>() {
	};

	private final String authUrl;

	private final String apiUrl;

	private final String restApiKey;

	private final String clientSecret;

	private final RestClient client;

	/** @param clientSecret blank unless the app's key has "Client Secret" switched on in the Kakao console */
	HttpKakaoClient(String authUrl, String apiUrl, String restApiKey, String clientSecret) {
		this.authUrl = authUrl;
		this.apiUrl = apiUrl;
		this.restApiKey = restApiKey;
		this.clientSecret = clientSecret;
		JdkClientHttpRequestFactory requests = new JdkClientHttpRequestFactory(
				HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
		requests.setReadTimeout(Duration.ofSeconds(8));
		this.client = RestClient.builder().requestFactory(requests).build();
	}

	@Override
	public boolean available() {
		return !restApiKey.isBlank();
	}

	@Override
	public String authorizeUrl(String redirectUri, String state) {
		return UriComponentsBuilder.fromUriString(authUrl).path("/oauth/authorize")
				.queryParam("response_type", "code")
				.queryParam("client_id", restApiKey)
				.queryParam("redirect_uri", redirectUri)
				.queryParam("state", state)
				.encode().build().toUriString();
	}

	@Override
	public Member member(String code, String redirectUri) {
		MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
		form.add("grant_type", "authorization_code");
		form.add("client_id", restApiKey);
		form.add("redirect_uri", redirectUri);
		form.add("code", code);
		if (!clientSecret.isBlank()) {
			form.add("client_secret", clientSecret);
		}
		Map<String, Object> token = call("the token", () -> client.post().uri(authUrl + "/oauth/token")
				.contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form)
				.retrieve().onStatus(HttpStatusCode::isError, (request, response) -> { }).toEntity(OBJECT));
		if (!(token.get("access_token") instanceof String accessToken)) {
			throw new KakaoException("Kakao returned no access token", null);
		}
		Map<String, Object> me = call("the member", () -> client.get().uri(apiUrl + "/v2/user/me")
				.headers(headers -> headers.setBearerAuth(accessToken))
				.retrieve().onStatus(HttpStatusCode::isError, (request, response) -> { }).toEntity(OBJECT));
		if (!(me.get("id") instanceof Number id)) {
			throw new KakaoException("Kakao returned no member number", null);
		}
		return new Member(id.longValue(), nickname(me));
	}

	/** The nickname is only there if the app asks for it and the member agreed; it may be null. */
	private static String nickname(Map<String, Object> me) {
		if (me.get("kakao_account") instanceof Map<?, ?> account && account.get("profile") instanceof Map<?, ?> profile
				&& profile.get("nickname") instanceof String nickname) {
			return nickname;
		}
		if (me.get("properties") instanceof Map<?, ?> properties && properties.get("nickname") instanceof String nickname) {
			return nickname;
		}
		return null;
	}

	private interface Request {

		ResponseEntity<Map<String, Object>> send();

	}

	private static Map<String, Object> call(String what, Request request) {
		ResponseEntity<Map<String, Object>> response;
		try {
			response = request.send();
		}
		catch (RestClientException exception) {
			throw new KakaoException("Kakao could not be reached for " + what, exception);
		}
		Map<String, Object> body = response.getBody();
		if (!response.getStatusCode().is2xxSuccessful() || body == null) {
			// Kakao explains itself in error / error_description (token) or msg / code (API).
			String reason = body == null ? "no body"
					: String.valueOf(body.getOrDefault("error_description", body.getOrDefault("msg", body.get("error"))));
			throw new KakaoException("Kakao refused " + what + " (" + response.getStatusCode().value() + "): " + reason, null);
		}
		return body;
	}

}
