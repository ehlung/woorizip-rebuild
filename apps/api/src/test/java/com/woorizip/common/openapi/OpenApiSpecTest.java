package com.woorizip.common.openapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.woorizip.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 서버 코드에서 OpenAPI 명세를 만들어 {@code packages/api-client/openapi.json}에 쓴다 (ADR 0006).
 *
 * <p>앱용 클라이언트는 이 파일로 생성한다. CI는 테스트 뒤에 이 파일과 생성된 클라이언트가 커밋된 것과
 * 같은지 확인한다. 서버 API를 바꾸고 클라이언트를 다시 생성하지 않으면 병합할 수 없다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class OpenApiSpecTest {

	/** Gradle은 테스트를 apps/api에서 실행한다. */
	static final Path SPEC_FILE = Path.of("../../packages/api-client/openapi.json");

	@Autowired
	MockMvc mvc;

	@Test
	void 명세를_파일로_쓴다() throws Exception {
		String spec = this.mvc.perform(get("/v3/api-docs"))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString(StandardCharsets.UTF_8);

		assertThat(spec).contains("\"openapi\"").contains("\"bearer\"");
		// 오류 응답 형식과 오류 코드 목록이 명세에 들어가야 앱이 code를 타입으로 쓸 수 있다.
		assertThat(spec).contains("\"ApiProblem\"").contains("\"EMAIL_CODE_EXPIRED\"").contains("\"default\"");
		Files.writeString(SPEC_FILE, spec.endsWith("\n") ? spec : spec + "\n", StandardCharsets.UTF_8);
	}

}
