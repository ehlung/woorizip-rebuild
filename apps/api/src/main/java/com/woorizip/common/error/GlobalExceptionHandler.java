package com.woorizip.common.error;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * 모든 오류를 {@link ApiProblem} 형식으로 바꾼다.
 *
 * <p>Spring MVC가 던지는 예외(없는 주소, 잘못된 요청 방식 등)는 부모 클래스가 ProblemDetail을 만들고,
 * 여기서 오류 코드를 더한다. 인증 실패(401)와 권한 없음(403)은 Spring Security 필터에서 생기므로
 * {@code common.security}의 처리기가 이 클래스로 넘긴다.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	@ExceptionHandler(ApiException.class)
	ResponseEntity<ApiProblem> handleApiException(ApiException ex) {
		if (!ex.errors().isEmpty()) {
			return respond(ApiProblem.validationFailed(ex.errors()));
		}
		return respond(ApiProblem.of(ex.code(), ex.getMessage()));
	}

	@ExceptionHandler(AuthenticationException.class)
	ResponseEntity<ApiProblem> handleAuthentication(AuthenticationException ex) {
		return respond(ApiProblem.of(ErrorCode.UNAUTHORIZED));
	}

	@ExceptionHandler(AccessDeniedException.class)
	ResponseEntity<ApiProblem> handleAccessDenied(AccessDeniedException ex) {
		return respond(ApiProblem.of(ErrorCode.FORBIDDEN));
	}

	/** 예상하지 못한 오류. 내부 메시지는 응답에 담지 않고 로그에만 남긴다. */
	@ExceptionHandler(Exception.class)
	ResponseEntity<ApiProblem> handleUnexpected(Exception ex) {
		log.error("처리하지 못한 예외", ex);
		return respond(ApiProblem.of(ErrorCode.INTERNAL_ERROR));
	}

	@Override
	protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		List<ApiProblem.FieldError> errors = ex.getBindingResult()
			.getFieldErrors()
			.stream()
			.map(error -> new ApiProblem.FieldError(error.getField(), error.getDefaultMessage()))
			.toList();
		return handleExceptionInternal(ex, ApiProblem.validationFailed(errors), headers, status, request);
	}

	@Override
	protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
			HttpStatusCode statusCode, WebRequest request) {
		// 없는 주소(404), 잘못된 요청 방식(405) 같은 Spring 예외는 body 없이 오고, 예외 안의 ProblemDetail을 쓴다.
		if (body == null && ex instanceof ErrorResponse errorResponse) {
			body = errorResponse.getBody();
		}
		if (body instanceof ProblemDetail problem && !(body instanceof ApiProblem)) {
			body = ApiProblem.from(problem, ErrorCode.fromStatus(statusCode));
		}
		return super.handleExceptionInternal(ex, body, headers, statusCode, request);
	}

	private static ResponseEntity<ApiProblem> respond(ApiProblem problem) {
		return ResponseEntity.status(problem.getStatus()).body(problem);
	}

}
