package com.woorizip.common.openapi;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 명세의 공통 정보 (ADR 0006).
 *
 * <p>모든 API는 기본으로 access token(Bearer)을 요구한다고 적는다. 로그인 없이 쓰는 API는
 * 컨트롤러에서 이 요구를 빼도록 표시한다.
 */
@Configuration(proxyBeanMethods = false)
class OpenApiConfig {

	static final String BEARER = "bearer";

	@Bean
	OpenAPI openApi() {
		return new OpenAPI().info(new Info().title("우리.zip API").version("v0"))
			.components(new Components().addSecuritySchemes(BEARER,
					new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
			.addSecurityItem(new SecurityRequirement().addList(BEARER));
	}

}
