package com.woorizip.common.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * Spring Security 필터에서 생긴 401, 403을 다른 오류와 같은 형식(ApiProblem)으로 보낸다.
 *
 * <p>필터는 컨트롤러보다 앞에서 돌기 때문에 {@code @RestControllerAdvice}가 직접 받지 못한다.
 * 그래서 예외를 Spring MVC의 예외 처리기로 넘겨 {@code GlobalExceptionHandler}가 응답을 만들게 한다.
 */
@Component
class ProblemDetailSecurityHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

	private final HandlerExceptionResolver resolver;

	ProblemDetailSecurityHandler(@Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver) {
		this.resolver = resolver;
	}

	@Override
	public void commence(HttpServletRequest request, HttpServletResponse response,
			AuthenticationException exception) {
		// 401 응답에는 어떤 방식으로 인증해야 하는지 알리는 헤더를 붙인다 (RFC 6750 3장).
		response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
		this.resolver.resolveException(request, response, null, exception);
	}

	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException exception) {
		this.resolver.resolveException(request, response, null, exception);
	}

}
