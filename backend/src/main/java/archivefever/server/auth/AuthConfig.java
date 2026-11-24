package archivefever.server.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class AuthConfig {

	private static final Logger log = LoggerFactory.getLogger(AuthConfig.class);

	@Bean
	KakaoClient kakaoClient(@Value("${archivefever.kakao.auth-url}") String authUrl,
			@Value("${archivefever.kakao.api-url}") String apiUrl,
			@Value("${archivefever.kakao.rest-api-key}") String restApiKey,
			@Value("${archivefever.kakao.client-secret}") String clientSecret) {
		KakaoClient kakao = new HttpKakaoClient(authUrl, apiUrl, restApiKey.strip(), clientSecret.strip());
		if (kakao.available()) {
			log.info("Sign-in uses Kakao Login");
		}
		else {
			log.warn("No Kakao REST API key configured: sign-in is by name only (development sign-in)");
		}
		return kakao;
	}

}
