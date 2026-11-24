package archivefever.server.user;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

/**
 * Who is making the request: the user whose session came with it. The session filter
 * puts the user's id on the request before any controller runs.
 */
@Component
public class CurrentUser {

	@ResponseStatus(HttpStatus.UNAUTHORIZED)
	public static class NotSignedIn extends RuntimeException {

		NotSignedIn() {
			super("Not signed in");
		}

	}

	/** Name of the request attribute holding the signed-in user's id. */
	public static final String ATTRIBUTE = CurrentUser.class.getName() + ".id";

	/** The signed-in user's id; requests without a session do not get this far for protected paths. */
	public String id() {
		RequestAttributes request = RequestContextHolder.getRequestAttributes();
		Object id = request == null ? null : request.getAttribute(ATTRIBUTE, RequestAttributes.SCOPE_REQUEST);
		if (id == null) {
			throw new NotSignedIn();
		}
		return (String) id;
	}

}
