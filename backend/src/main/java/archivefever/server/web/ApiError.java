package archivefever.server.web;

import org.springframework.http.HttpStatus;

/**
 * A request the server understood and turns down. The code goes to the client as
 * {@code {"error": code}}, so that it can say what went wrong in its own words.
 */
public class ApiError extends RuntimeException {

	private final HttpStatus status;

	private final String code;

	public ApiError(HttpStatus status, String code) {
		super(code);
		this.status = status;
		this.code = code;
	}

	public static ApiError badRequest(String code) {
		return new ApiError(HttpStatus.BAD_REQUEST, code);
	}

	public static ApiError forbidden(String code) {
		return new ApiError(HttpStatus.FORBIDDEN, code);
	}

	public static ApiError notFound(String code) {
		return new ApiError(HttpStatus.NOT_FOUND, code);
	}

	public static ApiError conflict(String code) {
		return new ApiError(HttpStatus.CONFLICT, code);
	}

	public HttpStatus status() {
		return status;
	}

	public String code() {
		return code;
	}

}
