package com.woorizip.common.error;

import java.time.Duration;
import java.util.List;

/** 서비스 코드가 오류 코드와 함께 던지는 예외. {@link GlobalExceptionHandler}가 오류 응답으로 바꾼다. */
public class ApiException extends RuntimeException {

	private final ErrorCode code;

	private final List<ApiProblem.FieldError> errors;

	private final Duration retryAfter;

	public ApiException(ErrorCode code) {
		this(code, code.message());
	}

	public ApiException(ErrorCode code, String detail) {
		this(code, detail, List.of(), null);
	}

	private ApiException(ErrorCode code, String detail, List<ApiProblem.FieldError> errors, Duration retryAfter) {
		super(detail);
		this.code = code;
		this.errors = List.copyOf(errors);
		this.retryAfter = retryAfter;
	}

	/** 서비스 코드에서 입력값을 검사하다 실패했을 때. 응답 형식은 Bean Validation 실패와 같다. */
	public static ApiException validationFailed(List<ApiProblem.FieldError> errors) {
		return new ApiException(ErrorCode.VALIDATION_FAILED, ErrorCode.VALIDATION_FAILED.message(), errors, null);
	}

	/** 기다렸다가 다시 시도해야 할 때. 응답의 {@code Retry-After} 헤더에 남은 시간(초)을 담는다(RFC 6585 4절). */
	public static ApiException retryAfter(ErrorCode code, Duration wait) {
		return new ApiException(code, code.message(), List.of(), wait);
	}

	public ErrorCode code() {
		return this.code;
	}

	List<ApiProblem.FieldError> errors() {
		return this.errors;
	}

	Duration retryAfter() {
		return this.retryAfter;
	}

}
