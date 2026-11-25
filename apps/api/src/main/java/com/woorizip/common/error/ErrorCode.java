package com.woorizip.common.error;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

/**
 * 앱이 처리 방식을 정할 때 쓰는 오류 코드 (03-architecture 11장).
 *
 * <p>앱은 메시지가 아니라 이 코드로 화면 문구와 처리(다시 시도, 로그인 화면 이동 등)를 정한다.
 * 기능을 만들면서 필요한 코드를 더한다.
 */
public enum ErrorCode {

	BAD_REQUEST(HttpStatus.BAD_REQUEST, "요청 형식이 올바르지 않습니다."),
	VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "입력값이 올바르지 않습니다."),
	UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."),
	FORBIDDEN(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
	NOT_FOUND(HttpStatus.NOT_FOUND, "찾을 수 없습니다."),
	METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 요청 방식입니다."),
	UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "지원하지 않는 요청 형식입니다."),
	INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버에서 오류가 발생했습니다.");

	private final HttpStatus status;

	private final String message;

	ErrorCode(HttpStatus status, String message) {
		this.status = status;
		this.message = message;
	}

	public HttpStatus status() {
		return this.status;
	}

	public String message() {
		return this.message;
	}

	/** Spring이 직접 만드는 오류(없는 주소, 잘못된 요청 방식 등)에 붙일 코드를 상태 코드로 고른다. */
	static ErrorCode fromStatus(HttpStatusCode status) {
		return switch (status.value()) {
			case 401 -> UNAUTHORIZED;
			case 403 -> FORBIDDEN;
			case 404 -> NOT_FOUND;
			case 405 -> METHOD_NOT_ALLOWED;
			case 415 -> UNSUPPORTED_MEDIA_TYPE;
			default -> status.is5xxServerError() ? INTERNAL_ERROR : BAD_REQUEST;
		};
	}

}
