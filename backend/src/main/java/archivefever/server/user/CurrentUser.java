package archivefever.server.user;

import org.springframework.stereotype.Component;

/**
 * Who is making the request. There is no login yet, so every request belongs to one
 * demo user; Kakao login will replace this single place.
 */
@Component
public class CurrentUser {

	public String id() {
		return "demo";
	}

}
