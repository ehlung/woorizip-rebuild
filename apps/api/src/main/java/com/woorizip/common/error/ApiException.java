package com.woorizip.common.error;

/** 서비스 코드가 오류 코드와 함께 던지는 예외. {@link GlobalExceptionHandler}가 오류 응답으로 바꾼다. */
public class ApiException extends RuntimeException {

	private final ErrorCode code;

	public ApiException(ErrorCode code) {
		this(code, code.message());
	}

	public ApiException(ErrorCode code, String detail) {
		super(detail);
		this.code = code;
	}

	public ErrorCode code() {
		return this.code;
	}

}
