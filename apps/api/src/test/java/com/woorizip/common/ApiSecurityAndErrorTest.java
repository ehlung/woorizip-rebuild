package com.woorizip.common;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import com.woorizip.TestcontainersConfiguration;
import com.woorizip.auth.application.AccessTokenIssuer;
import com.woorizip.common.error.ApiException;
import com.woorizip.common.error.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 요청 인증 규칙과 오류 응답 형식을 확인한다 (ADR 0009, 03-architecture 11장).
 *
 * <p>아직 실제 기능 API가 없어서, 이 테스트에서만 쓰는 컨트롤러({@link TestApi})로 확인한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({ TestcontainersConfiguration.class, ApiSecurityAndErrorTest.TestApi.class })
class ApiSecurityAndErrorTest {

	private static final String PROBLEM_JSON = "application/problem+json";

	@Autowired
	MockMvc mvc;

	@Autowired
	AccessTokenIssuer tokens;

	@Test
	void 토큰_없이_요청하면_401과_UNAUTHORIZED() throws Exception {
		this.mvc.perform(get("/test/me"))
			.andExpect(status().isUnauthorized())
			.andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
			.andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
			.andExpect(jsonPath("$.status").value(401))
			.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
	}

	@Test
	void 잘못된_토큰으로_요청하면_401과_UNAUTHORIZED() throws Exception {
		this.mvc.perform(get("/test/me").header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
	}

	@Test
	void 발급한_토큰으로_요청하면_사용자_ID가_전달된다() throws Exception {
		this.mvc.perform(get("/test/me").header(HttpHeaders.AUTHORIZATION, bearer(42)))
			.andExpect(status().isOk())
			.andExpect(content().string("42"));
	}

	@Test
	void 상태_확인_주소는_토큰_없이_열린다() throws Exception {
		this.mvc.perform(get("/actuator/health")).andExpect(status().isOk());
	}

	@Test
	void 없는_주소는_404와_NOT_FOUND() throws Exception {
		this.mvc.perform(get("/test/no-such-path").header(HttpHeaders.AUTHORIZATION, bearer(42)))
			.andExpect(status().isNotFound())
			.andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
			.andExpect(jsonPath("$.code").value("NOT_FOUND"));
	}

	@Test
	void 지원하지_않는_요청_방식은_405와_METHOD_NOT_ALLOWED() throws Exception {
		this.mvc.perform(delete("/test/me").header(HttpHeaders.AUTHORIZATION, bearer(42)))
			.andExpect(status().isMethodNotAllowed())
			.andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
	}

	@Test
	void 입력값_검증에_실패하면_400과_필드별_오류() throws Exception {
		this.mvc
			.perform(post("/test/names").header(HttpHeaders.AUTHORIZATION, bearer(42))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\" \"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
			.andExpect(jsonPath("$.errors[0].field").value("name"));
	}

	@Test
	void 서비스가_던진_ApiException은_그_코드와_상태로_응답한다() throws Exception {
		this.mvc.perform(get("/test/api-exception").header(HttpHeaders.AUTHORIZATION, bearer(42)))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("NOT_FOUND"))
			.andExpect(jsonPath("$.detail").value("가족을 찾을 수 없습니다."));
	}

	@Test
	void 권한이_없으면_403과_FORBIDDEN() throws Exception {
		this.mvc.perform(get("/test/forbidden").header(HttpHeaders.AUTHORIZATION, bearer(42)))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("FORBIDDEN"));
	}

	@Test
	void 예상하지_못한_오류는_500이고_내부_메시지를_응답에_담지_않는다() throws Exception {
		this.mvc.perform(get("/test/boom").header(HttpHeaders.AUTHORIZATION, bearer(42)))
			.andExpect(status().isInternalServerError())
			.andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
			.andExpect(content().string(not(containsString("내부 비밀"))));
	}

	private String bearer(long userId) {
		return "Bearer " + this.tokens.issue(userId).value();
	}

	@RestController
	static class TestApi {

		@GetMapping("/test/me")
		String me(@AuthenticationPrincipal Jwt jwt) {
			return jwt.getSubject();
		}

		@PostMapping("/test/names")
		String name(@Valid @RequestBody NameRequest request) {
			return request.name();
		}

		@GetMapping("/test/api-exception")
		String apiException() {
			throw new ApiException(ErrorCode.NOT_FOUND, "가족을 찾을 수 없습니다.");
		}

		@GetMapping("/test/forbidden")
		String forbidden() {
			throw new AccessDeniedException("다른 가족");
		}

		@GetMapping("/test/boom")
		String boom() {
			throw new IllegalStateException("내부 비밀");
		}

		record NameRequest(@NotBlank String name) {
		}

	}

}
