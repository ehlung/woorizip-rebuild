package com.woorizip.common.error;

import java.util.List;

/** 서비스 코드가 오류 코드와 함께 던지는 예외. {@link GlobalExceptionHandler}가 오류 응답으로 바꾼다. */
public class ApiException extends RuntimeException {

	private final ErrorCode code;

	private final List<ApiProblem.FieldError> errors;

	public ApiException(ErrorCode code) {
		this(code, code.message());
	}

	public ApiException(ErrorCode code, String detail) {
		super(detail);
		this.code = code;
		this.errors = List.of();
	}

	private ApiException(List<ApiProblem.FieldError> errors) {
		super(ErrorCode.VALIDATION_FAILED.message());
		this.code = ErrorCode.VALIDATION_FAILED;
		this.errors = List.copyOf(errors);
	}

	/** 서비스 코드에서 입력값을 검사하다 실패했을 때. 응답 형식은 Bean Validation 실패와 같다. */
	public static ApiException validationFailed(List<ApiProblem.FieldError> errors) {
		return new ApiException(errors);
	}

	public ErrorCode code() {
		return this.code;
	}

	List<ApiProblem.FieldError> errors() {
		return this.errors;
	}

}
