package com.woorizip.common.openapi;

import java.util.Arrays;
import java.util.List;

import com.woorizip.common.error.ErrorCode;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 명세의 공통 정보 (ADR 0006).
 *
 * <p>모든 API는 기본으로 access token(Bearer)을 요구한다고 적는다. 로그인 없이 쓰는 API는
 * 컨트롤러에서 이 요구를 빼도록 표시한다.
 *
 * <p>모든 API의 오류 응답은 {@code ApiProblem} 형식이라고 적는다. 앱용 클라이언트가 오류 코드({@code code})를
 * 타입으로 알 수 있게 하려는 것이다.
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

	/** 모든 API에 오류 응답(default)을 붙인다. 형식은 common.error.ApiProblem과 같다. */
	@Bean
	OpenApiCustomizer errorResponses() {
		return openApi -> {
			Schema<?> fieldError = new ObjectSchema().addProperty("field", new StringSchema())
				.addProperty("message", new StringSchema())
				.required(List.of("field", "message"));
			Schema<?> problem = new ObjectSchema().addProperty("type", new StringSchema())
				.addProperty("title", new StringSchema())
				.addProperty("status", new IntegerSchema())
				.addProperty("detail", new StringSchema())
				.addProperty("instance", new StringSchema())
				.addProperty("code",
						new StringSchema()._enum(Arrays.stream(ErrorCode.values()).map(Enum::name).toList()))
				.addProperty("errors", new ArraySchema().items(new Schema<>().$ref("#/components/schemas/ApiFieldError")))
				.required(List.of("status", "code"));
			openApi.getComponents().addSchemas("ApiProblem", problem).addSchemas("ApiFieldError", fieldError);
			ApiResponse error = new ApiResponse().description("오류. 앱은 code로 처리 방식을 정한다")
				.content(new Content().addMediaType("application/problem+json",
						new MediaType().schema(new Schema<>().$ref("#/components/schemas/ApiProblem"))));
			openApi.getPaths()
				.values()
				.forEach(path -> path.readOperations()
					.forEach(operation -> operation.getResponses().addApiResponse("default", error)));
		};
	}

}
