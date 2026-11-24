package archivefever.server.auth;

/** Kakao Login, as far as this server needs it: who is the person that just came back with this code? */
public interface KakaoClient {

	/** @param id Kakao's member number for this person in this app; the same at every sign-in */
	record Member(long id, String nickname) {
	}

	/** Kakao could not be reached, or would not vouch for the code. */
	class KakaoException extends RuntimeException {

		public KakaoException(String message, Throwable cause) {
			super(message, cause);
		}

	}

	/** Whether a REST API key is configured; without one, Kakao sign-in is not offered. */
	boolean available();

	/**
	 * The address to send the browser to, where the person signs in at Kakao and agrees.
	 * Kakao then sends the browser to {@code redirectUri} with a code and the same state.
	 */
	String authorizeUrl(String redirectUri, String state);

	/**
	 * Exchanges the code for the member it stands for.
	 * @param redirectUri the same address the code was requested with
	 */
	Member member(String code, String redirectUri);

}
