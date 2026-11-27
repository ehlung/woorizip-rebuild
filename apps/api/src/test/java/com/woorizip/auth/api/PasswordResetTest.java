package com.woorizip.auth.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.jayway.jsonpath.JsonPath;
import com.woorizip.MutableClock;
import com.woorizip.TestClockConfiguration;
import com.woorizip.TestcontainersConfiguration;
import com.woorizip.auth.application.SecretHashing;
import com.woorizip.auth.domain.IdentityProvider;
import com.woorizip.auth.domain.LoginFailure;
import com.woorizip.auth.domain.LoginFailureRepository;
import com.woorizip.auth.domain.PasswordResetTicket;
import com.woorizip.auth.domain.PasswordResetTicketRepository;
import com.woorizip.auth.domain.UserIdentity;
import com.woorizip.auth.domain.UserIdentityRepository;
import com.woorizip.infra.mail.FakeMailGateway;
import com.woorizip.infra.mail.Mail;
import com.woorizip.user.domain.ScreenSize;
import com.woorizip.user.domain.User;
import com.woorizip.user.domain.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 비밀번호 재설정 (user-flows B, docs/plans/auth.md PR 4). A-39, A-46과 A-87의 재설정 부분을 확인한다.
 *
 * <p>메일은 가짜 발송으로 보내고, 테스트는 보낸 메일에서 코드를 읽는다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({ TestcontainersConfiguration.class, TestClockConfiguration.class })
class PasswordResetTest {

	private static final Pattern CODE = Pattern.compile("인증 코드: (\\d{6})");

	private static final String OLD_PASSWORD = "우리 가족 사랑해";

	private static final String NEW_PASSWORD = "새 비밀번호 입니다";

	@Autowired
	MockMvc mvc;

	@Autowired
	FakeMailGateway mails;

	@Autowired
	MutableClock clock;

	@Autowired
	UserRepository users;

	@Autowired
	UserIdentityRepository identities;

	@Autowired
	LoginFailureRepository loginFailures;

	@Autowired
	PasswordResetTicketRepository tickets;

	@Autowired
	PasswordEncoder passwordEncoder;

	@Autowired
	TransactionTemplate tx;

	@Test
	void A87_가짜_발송으로_코드를_받아_비밀번호를_바꾸면_새_비밀번호로만_로그인된다() throws Exception {
		String email = newUser();

		requestCode(email).andExpect(status().isAccepted());
		String resetToken = resetTokenFor(email, codeSentTo(email));
		reset(resetToken, NEW_PASSWORD).andExpect(status().isNoContent());

		login(email, OLD_PASSWORD).andExpect(status().isUnauthorized());
		login(email, NEW_PASSWORD).andExpect(status().isOk());
		Mail last = this.mails.sentTo(email).getLast();
		assertThat(last.subject()).contains("비밀번호가 변경");
		assertThat(last.body()).doesNotContainPattern("\\d{6}");
	}

	@Test
	void A39_비밀번호를_재설정하면_그_사용자의_다른_기기_refresh_token도_거부된다() throws Exception {
		String email = newUser();
		String phone = refreshTokenOf(loginBody(email));
		String tablet = refreshTokenOf(loginBody(email));

		requestCode(email);
		reset(resetTokenFor(email, codeSentTo(email)), NEW_PASSWORD).andExpect(status().isNoContent());

		refresh(phone).andExpect(status().isUnauthorized());
		refresh(tablet).andExpect(status().isUnauthorized());
	}

	@Test
	void A46_재설정_코드는_10분_뒤에는_맞게_입력해도_거부된다() throws Exception {
		String email = newUser();
		requestCode(email);
		String code = codeSentTo(email);

		this.clock.advance(Duration.ofMinutes(10));

		verify(email, code).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("EMAIL_CODE_EXPIRED"));
	}

	@Test
	void A46_재설정_코드는_5회_틀린_뒤에는_맞게_입력해도_거부된다() throws Exception {
		String email = newUser();
		requestCode(email);
		String code = codeSentTo(email);
		String wrong = wrongCode(code);
		for (int i = 0; i < 5; i++) {
			verify(email, wrong).andExpect(jsonPath("$.code").value("EMAIL_CODE_INVALID"));
		}

		verify(email, code).andExpect(jsonPath("$.code").value("EMAIL_CODE_EXPIRED"));
	}

	@Test
	void 가입하지_않은_이메일에는_메일을_보내지_않고_응답과_코드_확인_결과는_같다() throws Exception {
		String registered = newUser();
		String unknown = newEmail();

		String registeredResponse = requestCode(registered).andExpect(status().isAccepted())
			.andReturn()
			.getResponse()
			.getContentAsString();
		String unknownResponse = requestCode(unknown).andExpect(status().isAccepted())
			.andReturn()
			.getResponse()
			.getContentAsString();
		assertThat(registeredResponse).isEqualTo(unknownResponse);
		assertThat(this.mails.sentTo(unknown)).isEmpty();

		String wrong = wrongCode(codeSentTo(registered));
		for (int i = 0; i < 5; i++) {
			assertThat(verifyBody(registered, wrong)).isEqualTo(verifyBody(unknown, wrong)).contains("EMAIL_CODE_INVALID");
		}
		assertThat(verifyBody(registered, wrong)).isEqualTo(verifyBody(unknown, wrong)).contains("EMAIL_CODE_EXPIRED");
	}

	@Test
	void 가입하지_않은_이메일도_같은_요청_횟수_제한을_받는다() throws Exception {
		String registered = newUser();
		String unknown = newEmail();
		requestCode(registered).andExpect(status().isAccepted());
		requestCode(unknown).andExpect(status().isAccepted());

		requestCode(registered).andExpect(status().isTooManyRequests());
		requestCode(unknown).andExpect(status().isTooManyRequests());

		this.clock.advance(Duration.ofMinutes(1));
		requestCode(registered).andExpect(status().isAccepted());
		requestCode(unknown).andExpect(status().isAccepted());
	}

	@Test
	void 로그인이_잠긴_이메일도_재설정할_수_있고_재설정하면_잠금이_풀린다() throws Exception {
		String email = newUser();
		LoginFailure failure = new LoginFailure(email);
		for (int i = 0; i < LoginFailure.MAX_CONSECUTIVE_FAILURES; i++) {
			failure.recordFailure(this.clock.instant());
		}
		this.loginFailures.save(failure);
		login(email, OLD_PASSWORD).andExpect(jsonPath("$.code").value("PASSWORD_RESET_REQUIRED"));

		requestCode(email).andExpect(status().isAccepted());
		reset(resetTokenFor(email, codeSentTo(email)), NEW_PASSWORD).andExpect(status().isNoContent());

		login(email, NEW_PASSWORD).andExpect(status().isOk());
	}

	@Test
	void 재설정_확인_값은_한_번만_쓰고_재설정하면_남은_확인_값도_쓸_수_없다() throws Exception {
		String email = newUser();
		requestCode(email);
		String first = resetTokenFor(email, codeSentTo(email));
		this.clock.advance(Duration.ofMinutes(1));
		requestCode(email);
		String second = resetTokenFor(email, codeSentTo(email));

		reset(first, NEW_PASSWORD).andExpect(status().isNoContent());

		reset(first, "또 다른 비밀번호").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("PASSWORD_RESET_TOKEN_INVALID"));
		reset(second, "또 다른 비밀번호").andExpect(jsonPath("$.code").value("PASSWORD_RESET_TOKEN_INVALID"));
		login(email, NEW_PASSWORD).andExpect(status().isOk());
	}

	@Test
	void 재설정_확인_값은_10분_뒤_만료된다() throws Exception {
		String email = newUser();
		requestCode(email);
		String resetToken = resetTokenFor(email, codeSentTo(email));

		this.clock.advance(Duration.ofMinutes(10));

		reset(resetToken, NEW_PASSWORD).andExpect(jsonPath("$.code").value("PASSWORD_RESET_TOKEN_INVALID"));
		login(email, OLD_PASSWORD).andExpect(status().isOk());
	}

	@Test
	void 가입하지_않은_이메일의_확인_값으로_재설정해도_같은_응답이고_아무것도_바뀌지_않는다() throws Exception {
		String unknown = newEmail();
		String resetToken = SecretHashing.newToken();
		this.tickets.save(new PasswordResetTicket(unknown, SecretHashing.sha256(resetToken), this.clock.instant(),
				this.clock.instant().plus(Duration.ofMinutes(10))));

		reset(resetToken, NEW_PASSWORD).andExpect(status().isNoContent());

		assertThat(this.identities.existsByProviderAndSubject(IdentityProvider.EMAIL, unknown)).isFalse();
		assertThat(this.mails.sentTo(unknown)).isEmpty();
	}

	@Test
	void 새_비밀번호도_가입과_같은_규칙을_따른다() throws Exception {
		String email = newUser();
		requestCode(email);
		String resetToken = resetTokenFor(email, codeSentTo(email));

		reset(resetToken, "짧은비번").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
			.andExpect(jsonPath("$.errors[0].field").value("newPassword"));
		reset(resetToken, NEW_PASSWORD).andExpect(status().isNoContent());
	}

	private String newUser() {
		String email = newEmail();
		this.tx.executeWithoutResult(status -> {
			User user = this.users.save(new User("엄마", ScreenSize.LARGE));
			this.identities.save(UserIdentity.email(user, email, this.passwordEncoder.encode(OLD_PASSWORD)));
		});
		return email;
	}

	private String codeSentTo(String email) {
		Matcher matcher = CODE.matcher(this.mails.sentTo(email).getLast().body());
		assertThat(matcher.find()).isTrue();
		return matcher.group(1);
	}

	private static String wrongCode(String code) {
		return code.equals("000000") ? "000001" : "000000";
	}

	private String resetTokenFor(String email, String code) throws Exception {
		String body = verify(email, code).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		return JsonPath.read(body, "$.resetToken");
	}

	private String verifyBody(String email, String code) throws Exception {
		return verify(email, code).andReturn().getResponse().getContentAsString();
	}

	private String loginBody(String email) throws Exception {
		return login(email, OLD_PASSWORD).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
	}

	private static String refreshTokenOf(String body) {
		return JsonPath.read(body, "$.refreshToken");
	}

	private ResultActions requestCode(String email) throws Exception {
		return post("/auth/password-reset/email-code", "{\"email\":\"" + email + "\"}");
	}

	private ResultActions verify(String email, String code) throws Exception {
		return post("/auth/password-reset/email-code/verify",
				"{\"email\":\"" + email + "\",\"code\":\"" + code + "\"}");
	}

	private ResultActions reset(String resetToken, String newPassword) throws Exception {
		return post("/auth/password-reset",
				"{\"resetToken\":\"" + resetToken + "\",\"newPassword\":\"" + newPassword + "\"}");
	}

	private ResultActions login(String email, String password) throws Exception {
		return post("/auth/login", "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}");
	}

	private ResultActions refresh(String refreshToken) throws Exception {
		return post("/auth/token/refresh", "{\"refreshToken\":\"" + refreshToken + "\"}");
	}

	private ResultActions post(String path, String json) throws Exception {
		return this.mvc.perform(MockMvcRequestBuilders.post(path).contentType(MediaType.APPLICATION_JSON).content(json));
	}

	private static String newEmail() {
		return "reset-" + UUID.randomUUID() + "@example.com";
	}

}
