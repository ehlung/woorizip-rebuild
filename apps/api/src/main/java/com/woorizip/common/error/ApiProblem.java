package com.woorizip.common.error;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.http.ProblemDetail;

/**
 * 모든 오류 응답의 형식. RFC 9457(Problem Details)에 앱이 쓰는 {@code code}를 더했다.
 *
 * <pre>
 * {
 *   "type": "about:blank",
 *   "title": "Bad Request",
 *   "status": 400,
 *   "detail": "입력값이 올바르지 않습니다.",
 *   "instance": "/families",
 *   "code": "VALIDATION_FAILED",
 *   "errors": [{ "field": "name", "message": "공백일 수 없습니다" }]
 * }
 * </pre>
 *
 * <p>Spring 문서의 방법대로 ProblemDetail을 상속해 필드를 더한다.
 * https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-ann-rest-exceptions.html
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiProblem extends ProblemDetail {

	private final String code;

	private final List<FieldError> errors;

	private ApiProblem(ProblemDetail source, ErrorCode code, List<FieldError> errors) {
		super(source);
		this.code = code.name();
		this.errors = errors;
	}

	public static ApiProblem of(ErrorCode code) {
		return of(code, code.message());
	}

	public static ApiProblem of(ErrorCode code, String detail) {
		return new ApiProblem(ProblemDetail.forStatusAndDetail(code.status(), detail), code, null);
	}

	/** Spring이 만든 ProblemDetail을 그대로 두고 코드만 더한다. */
	static ApiProblem from(ProblemDetail source, ErrorCode code) {
		return new ApiProblem(source, code, null);
	}

	static ApiProblem validationFailed(List<FieldError> errors) {
		ErrorCode code = ErrorCode.VALIDATION_FAILED;
		return new ApiProblem(ProblemDetail.forStatusAndDetail(code.status(), code.message()), code, errors);
	}

	public String getCode() {
		return this.code;
	}

	public List<FieldError> getErrors() {
		return this.errors;
	}

	/** 입력값 검증에 실패한 필드 하나. */
	public record FieldError(String field, String message) {
	}

}
