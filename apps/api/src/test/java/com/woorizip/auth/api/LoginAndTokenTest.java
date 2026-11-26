package com.woorizip.auth.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;

import com.jayway.jsonpath.JsonPath;
import com.woorizip.MutableClock;
import com.woorizip.TestClockConfiguration;
import com.woorizip.TestcontainersConfiguration;
import com.woorizip.auth.application.JwtKeys;
import com.woorizip.auth.application.JwtProperties;
import com.woorizip.auth.application.SecretHashing;
import com.woorizip.auth.domain.LoginFailure;
import com.woorizip.auth.domain.LoginFailureRepository;
import com.woorizip.auth.domain.RefreshToken;
import com.woorizip.auth.domain.RefreshTokenRepository;
import com.woorizip.auth.domain.UserIdentity;
import com.woorizip.auth.domain.UserIdentityRepository;
import com.woorizip.user.domain.ScreenSize;
import com.woorizip.user.domain.User;
import com.woorizip.user.domain.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 로그인, 토큰 재발급, 로그아웃 (ADR 0009의 검증 항목). A-37, 38, 40, 41, 42, 44, 47, 48을 확인한다.
 *
 * <p>로그인이 필요한 API가 아직 없어서, 이 테스트에서만 쓰는 {@link TestApi}로 access token을 확인한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({ TestcontainersConfiguration.class, TestClockConfiguration.class, LoginAndTokenTest.TestApi.class })
class LoginAndTokenTest {

	private static final String PASSWORD = "우리 가족 사랑해";

	@Autowired
	MockMvc mvc;

	@Autowired
	MutableClock clock;

	@Autowired
	UserRepository users;

	@Autowired
	UserIdentityRepository identities;

	@Autowired
	RefreshTokenRepository refreshTokens;

	@Autowired
	LoginFailureRepository loginFailures;

	@Autowired
	PasswordEncoder passwordEncoder;

	@Autowired
	JwtEncoder jwtEncoder;

	@Autowired
	TransactionTemplate tx;

	@Autowired
	JdbcTemplate jdbc;

	@Test
	void 로그인하면_access_token과_refresh_token을_받고_access_token으로_요청할_수_있다() throws Exception {
		String email = newUser();
		String body = login(email, PASSWORD).andExpect(status().isOk())
			.andExpect(jsonPath("$.refreshToken").isNotEmpty())
			.andReturn()
			.getResponse()
			.getContentAsString();

		me(JsonPath.read(body, "$.accessToken")).andExpect(status().isOk());
	}

	@Test
	void A37_만료된_access_token은_401이고_refresh_token으로_재발급받으면_다시_요청할_수_있다() throws Exception {
		String email = newUser();
		String body = loginBody(email);
		long userId = ((Number) JsonPath.read(body, "$.userId")).longValue();
		String expired = accessToken(this.jwtEncoder, "test", userId, Instant.now().minus(Duration.ofMinutes(5)));

		me(expired).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

		String refreshed = refresh(JsonPath.read(body, "$.refreshToken")).andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		me(JsonPath.read(refreshed, "$.accessToken")).andExpect(status().isOk());
	}

	@Test
	void A38_로그아웃한_기기의_refresh_token으로_재발급하면_거부된다() throws Exception {
		String email = newUser();
		String phone = refreshTokenOf(loginBody(email));
		String tablet = refreshTokenOf(loginBody(email));

		logout(phone).andExpect(status().isNoContent());

		refresh(phone).andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("REFRESH_TOKEN_INVALID"));
		refresh(tablet).andExpect(status().isOk());
	}

	@Test
	void 로그아웃은_다시_해도_없는_토큰이어도_204다() throws Exception {
		String token = refreshTokenOf(loginBody(newUser()));
		logout(token).andExpect(status().isNoContent());
		logout(token).andExpect(status().isNoContent());
		logout("no-such-token").andExpect(status().isNoContent());
	}

	@Test
	void A40_이미_쓴_refresh_token으로_재발급하면_그_사용자의_refresh_token이_모두_폐기된다() throws Exception {
		String email = newUser();
		String first = refreshTokenOf(loginBody(email));
		String otherDevice = refreshTokenOf(loginBody(email));
		String second = refreshTokenOf(refreshBody(first));
		this.clock.advance(Duration.ofSeconds(30));

		refresh(first).andExpect(status().isUnauthorized());

		refresh(second).andExpect(status().isUnauthorized());
		refresh(otherDevice).andExpect(status().isUnauthorized());
	}

	@Test
	void 바꾼_지_30초_안에_다시_온_refresh_token은_받아_주고_양쪽_토큰_모두_쓸_수_있다() throws Exception {
		String first = refreshTokenOf(loginBody(newUser()));
		String received = refreshTokenOf(refreshBody(first));

		// 새 토큰을 담은 응답이 앱에 닿지 않아 앱이 같은 토큰으로 다시 보낸 경우
		this.clock.advance(Duration.ofSeconds(29));
		String retried = refreshTokenOf(refreshBody(first));

		refresh(received).andExpect(status().isOk());
		refresh(retried).andExpect(status().isOk());
	}

	@Test
	void 같은_refresh_token으로_동시에_재발급해도_둘_다_성공하고_토큰이_폐기되지_않는다() throws Exception {
		String token = refreshTokenOf(loginBody(newUser()));
		CountDownLatch start = new CountDownLatch(1);
		List<CompletableFuture<Integer>> requests = List.of(1, 2).stream().map(i -> CompletableFuture.supplyAsync(() -> {
			try {
				start.await();
				return refresh(token).andReturn().getResponse().getStatus();
			}
			catch (Exception ex) {
				throw new IllegalStateException(ex);
			}
		})).toList();
		start.countDown();

		assertThat(requests.stream().map(CompletableFuture::join)).containsExactly(200, 200);
	}

	@Test
	void A41_30일_동안_쓰지_않은_refresh_token은_거부된다() throws Exception {
		String token = refreshTokenOf(loginBody(newUser()));
		this.clock.advance(Duration.ofDays(30));

		refresh(token).andExpect(status().isUnauthorized());
	}

	@Test
	void 쓰는_동안은_30일이_연장되지만_처음_로그인한_뒤_1년이_지나면_다시_로그인해야_한다() throws Exception {
		String token = refreshTokenOf(loginBody(newUser()));
		for (int i = 0; i < 12; i++) {
			this.clock.advance(Duration.ofDays(29));
			token = refreshTokenOf(refreshBody(token));
		}
		// 처음 로그인한 뒤 348일. 다음 29일 뒤는 377일로 1년을 넘는다.
		this.clock.advance(Duration.ofDays(29));

		refresh(token).andExpect(status().isUnauthorized());
	}

	@Test
	void A42_DB에는_refresh_token_원문이_아니라_해시만_저장된다() throws Exception {
		String email = newUser();
		String body = loginBody(email);
		String raw = refreshTokenOf(body);
		long userId = ((Number) JsonPath.read(body, "$.userId")).longValue();

		List<RefreshToken> stored = this.refreshTokens.findByUserIdOrderByIdAsc(userId);
		assertThat(stored).hasSize(1);
		assertThat(stored.getFirst().getTokenHash()).isEqualTo(SecretHashing.sha256(raw)).isNotEqualTo(raw);
		List<String> columns = this.jdbc.queryForList(
				"SELECT CONCAT_WS('|', token_hash, session_id::text) FROM refresh_tokens WHERE user_id = ?",
				String.class, userId);
		assertThat(columns).noneMatch(row -> row.contains(raw));
	}

	@Test
	void A44_서명_키를_바꾼_뒤_이전_키의_access_token은_거부되고_refresh_token으로_재발급받으면_쓸_수_있다() throws Exception {
		String email = newUser();
		String body = loginBody(email);
		long userId = ((Number) JsonPath.read(body, "$.userId")).longValue();
		JwtKeys retiredKey = new JwtKeys(new JwtProperties("woorizip", Duration.ofMinutes(15), "retired",
				List.of(new JwtProperties.Key("retired", "r".repeat(32)))));
		String signedWithRetiredKey = accessToken(retiredKey.encoder(), "retired", userId,
				Instant.now().plus(Duration.ofMinutes(10)));

		me(signedWithRetiredKey).andExpect(status().isUnauthorized());

		String refreshed = refreshBody(refreshTokenOf(body));
		me(JsonPath.read(refreshed, "$.accessToken")).andExpect(status().isOk());
	}

	@Test
	void A47_없는_이메일과_틀린_비밀번호는_같은_응답을_준다() throws Exception {
		String email = newUser();

		String wrongPassword = login(email, "틀린 비밀번호입니다").andExpect(status().isUnauthorized())
			.andReturn()
			.getResponse()
			.getContentAsString();
		String noSuchEmail = login(newEmail(), "틀린 비밀번호입니다").andExpect(status().isUnauthorized())
			.andReturn()
			.getResponse()
			.getContentAsString();

		assertThat(wrongPassword).isEqualTo(noSuchEmail).contains("LOGIN_FAILED");
	}

	@Test
	void A47_없는_이메일도_같은_횟수에서_잠긴다() throws Exception {
		String registered = newUser();
		String unknown = newEmail();
		for (int i = 0; i < 5; i++) {
			login(registered, "틀린 비밀번호입니다");
			login(unknown, "틀린 비밀번호입니다");
		}

		String lockedRegistered = login(registered, PASSWORD).andExpect(status().isTooManyRequests())
			.andReturn()
			.getResponse()
			.getContentAsString();
		String lockedUnknown = login(unknown, PASSWORD).andExpect(status().isTooManyRequests())
			.andReturn()
			.getResponse()
			.getContentAsString();
		assertThat(lockedRegistered).isEqualTo(lockedUnknown);
	}

	@Test
	void 다섯_번_연속_실패하면_30초_잠기고_남은_시간을_알려_주며_성공하면_초기화된다() throws Exception {
		String email = newUser();
		for (int i = 0; i < 5; i++) {
			login(email, "틀린 비밀번호입니다").andExpect(status().isUnauthorized());
		}
		login(email, PASSWORD).andExpect(status().isTooManyRequests())
			.andExpect(jsonPath("$.code").value("LOGIN_LOCKED"))
			.andExpect(header().string(HttpHeaders.RETRY_AFTER, "30"));

		this.clock.advance(Duration.ofMillis(10_500));
		login(email, PASSWORD).andExpect(header().string(HttpHeaders.RETRY_AFTER, "20"));

		this.clock.advance(Duration.ofMillis(19_500));
		login(email, PASSWORD).andExpect(status().isOk());

		// 초기화됐으므로 다시 4번까지는 잠기지 않는다.
		for (int i = 0; i < 4; i++) {
			login(email, "틀린 비밀번호입니다").andExpect(status().isUnauthorized());
		}
		login(email, PASSWORD).andExpect(status().isOk());
	}

	@Test
	void 연속_100번_실패하면_맞는_비밀번호로도_로그인할_수_없고_비밀번호_재설정을_안내한다() throws Exception {
		String email = newUser();
		LoginFailure failure = new LoginFailure(email);
		for (int i = 0; i < LoginFailure.MAX_CONSECUTIVE_FAILURES - 1; i++) {
			failure.recordFailure(this.clock.instant().minus(Duration.ofDays(1)));
		}
		this.loginFailures.save(failure);

		login(email, "틀린 비밀번호입니다").andExpect(status().isUnauthorized());

		this.clock.advance(Duration.ofDays(1));
		login(email, PASSWORD).andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("PASSWORD_RESET_REQUIRED"));
	}

	@Test
	void A48_이메일이_바뀌어도_기존_로그인은_계속_유효하다() throws Exception {
		String email = newUser();
		String body = loginBody(email);

		this.jdbc.update("UPDATE user_identities SET subject = ? WHERE subject = ?", newEmail(), email);

		me(JsonPath.read(body, "$.accessToken")).andExpect(status().isOk());
		refresh(refreshTokenOf(body)).andExpect(status().isOk());
	}

	@Test
	void BCrypt_한도를_넘는_비밀번호로_로그인해도_서버_오류가_나지_않는다() throws Exception {
		login(newUser(), "가".repeat(25)).andExpect(status().isUnauthorized());
	}

	private String newUser() {
		String email = newEmail();
		this.tx.executeWithoutResult(status -> {
			User user = this.users.save(new User("엄마", ScreenSize.LARGE));
			this.identities.save(UserIdentity.email(user, email, this.passwordEncoder.encode(PASSWORD)));
		});
		return email;
	}

	private String loginBody(String email) throws Exception {
		return login(email, PASSWORD).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
	}

	private String refreshBody(String refreshToken) throws Exception {
		return refresh(refreshToken).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
	}

	private static String refreshTokenOf(String body) {
		return JsonPath.read(body, "$.refreshToken");
	}

	private ResultActions login(String email, String password) throws Exception {
		return this.mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
			.content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"));
	}

	private ResultActions refresh(String refreshToken) throws Exception {
		return this.mvc.perform(post("/auth/token/refresh").contentType(MediaType.APPLICATION_JSON)
			.content("{\"refreshToken\":\"" + refreshToken + "\"}"));
	}

	private ResultActions logout(String refreshToken) throws Exception {
		return this.mvc.perform(post("/auth/logout").contentType(MediaType.APPLICATION_JSON)
			.content("{\"refreshToken\":\"" + refreshToken + "\"}"));
	}

	private ResultActions me(String accessToken) throws Exception {
		return this.mvc.perform(get("/test/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken));
	}

	private static String accessToken(JwtEncoder encoder, String keyId, long userId, Instant expiresAt) {
		JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).keyId(keyId).build();
		JwtClaimsSet claims = JwtClaimsSet.builder()
			.issuer("woorizip")
			.subject(Long.toString(userId))
			.issuedAt(expiresAt.minus(Duration.ofMinutes(15)))
			.expiresAt(expiresAt)
			.build();
		return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
	}

	private static String newEmail() {
		return "login-" + UUID.randomUUID() + "@example.com";
	}

	@RestController
	static class TestApi {

		@GetMapping("/test/me")
		String me(@AuthenticationPrincipal Jwt jwt) {
			return jwt.getSubject();
		}

	}

}
