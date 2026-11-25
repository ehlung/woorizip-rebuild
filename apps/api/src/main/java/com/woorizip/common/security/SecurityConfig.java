package com.woorizip.common.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * 요청 인증 규칙 (ADR 0009).
 *
 * <p>기본은 모든 요청에 access token을 요구한다. 로그인 없이 쓰는 주소(가입, 로그인, 토큰 재발급,
 * 비밀번호 재설정)는 그 기능을 만드는 PR에서 여기에 하나씩 연다.
 */
@Configuration(proxyBeanMethods = false)
class SecurityConfig {

	@Bean
	SecurityFilterChain apiSecurity(HttpSecurity http, ProblemDetailSecurityHandler errors) throws Exception {
		http
			// 앱은 쿠키가 아니라 Authorization 헤더로 토큰을 보낸다. Spring Security 문서는 브라우저가 아닌
			// 클라이언트만 쓰는 서비스라면 CSRF 보호를 끄는 쪽을 권한다. 2단계 웹에서 쿠키를 쓰게 되면 다시 정한다.
			.csrf(AbstractHttpConfigurer::disable)
			.httpBasic(AbstractHttpConfigurer::disable)
			.formLogin(AbstractHttpConfigurer::disable)
			.logout(AbstractHttpConfigurer::disable)
			.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.authorizeHttpRequests(requests -> requests
				.requestMatchers("/actuator/health", "/actuator/health/**")
				.permitAll()
				// OpenAPI 명세. springdoc.api-docs.enabled가 false인 환경(운영)에서는 주소 자체가 없다.
				.requestMatchers("/v3/api-docs", "/v3/api-docs/**")
				.permitAll()
				.anyRequest()
				.authenticated())
			.oauth2ResourceServer(resourceServer -> resourceServer.jwt(Customizer.withDefaults())
				.authenticationEntryPoint(errors)
				.accessDeniedHandler(errors))
			.exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(errors).accessDeniedHandler(errors));
		return http.build();
	}

}
