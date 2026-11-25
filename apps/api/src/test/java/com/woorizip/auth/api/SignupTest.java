package com.woorizip.auth.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.jayway.jsonpath.JsonPath;
import com.woorizip.MutableClock;
import com.woorizip.TestClockConfiguration;
import com.woorizip.TestcontainersConfiguration;
import com.woorizip.auth.application.SignupService;
import com.woorizip.auth.domain.IdentityProvider;
import com.woorizip.auth.domain.UserIdentityRepository;
import com.woorizip.common.error.ApiException;
import com.woorizip.infra.mail.FakeMailGateway;
import com.woorizip.infra.mail.Mail;
import com.woorizip.user.domain.ConsentKind;
import com.woorizip.user.domain.UserConsent;
import com.woorizip.user.domain.UserConsentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 이메일 가입 흐름 (03-architecture 8장). A-83, A-84, A-85와 A-87의 가입 부분을 확인한다.
 *
 * <p>메일은 가짜 발송으로 보내고, 테스트는 보낸 메일에서 코드를 읽는다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({ TestcontainersConfiguration.class, TestClockConfiguration.class })
class SignupTest {

	private static final Pattern CODE = Pattern.compile("인증 코드: (\\d{6})");

	private static final String PASSWORD = "우리 가족 사랑해";

	@Autowired
	MockMvc mvc;

	@Autowired
	FakeMailGateway mails;

	@Autowired
	MutableClock clock;

	@Autowired
	UserIdentityRepository identities;

	@Autowired
	UserConsentRepository consents;

	@Autowired
	JwtDecoder jwtDecoder;

	@Autowired
	SignupService signupService;

	@Test
	void 코드를_받아_확인하고_가입하면_로그인_상태가_된다() throws Exception {
		String email = newEmail();

		requestCode(email).andExpect(status().isAccepted());
		String token = verify(email, codeSentTo(email)).andExpect(status().isOk())
			.andExpect(jsonPath("$.signupToken").isNotEmpty())
			.andReturn()
			.getResponse()
			.getContentAsString();
		String signupToken = JsonPath.read(token, "$.signupToken");

		String body = signup(signupToken, PASSWORD, allConsents()).andExpect(status().isCreated())
			.andExpect(jsonPath("$.accessToken").isNotEmpty())
			.andReturn()
			.getResponse()
			.getContentAsString();

		Number userId = JsonPath.read(body, "$.userId");
		String accessToken = JsonPath.read(body, "$.accessToken");
		assertThat(this.jwtDecoder.decode(accessToken).getSubject()).isEqualTo(userId.toString());
		assertThat(this.identities.existsByProviderAndSubject(IdentityProvider.EMAIL, email)).isTrue();
		assertThat(this.consents.findByUserId(userId.longValue())).extracting(UserConsent::getKind)
			.containsExactlyInAnyOrder(ConsentKind.values());
		assertThat(this.consents.findByUserId(userId.longValue())).extracting(UserConsent::getVersion)
			.containsOnly("v0");
	}

	@Test
	void A83_이메일_인증을_마치지_않으면_가입을_끝낼_수_없다() throws Exception {
		signup("made-up-token", PASSWORD, allConsents()).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("SIGNUP_TOKEN_INVALID"));
	}

	@Test
	void 가입_확인_값은_한_번만_쓸_수_있다() throws Exception {
		String email = newEmail();
		String signupToken = verifiedSignupToken(email);
		signup(signupToken, PASSWORD, allConsents()).andExpect(status().isCreated());

		signup(signupToken, PASSWORD, allConsents()).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("SIGNUP_TOKEN_INVALID"));
	}

	@Test
	void 가입_확인_값은_30분_뒤_만료된다() throws Exception {
		String signupToken = verifiedSignupToken(newEmail());
		this.clock.advance(Duration.ofMinutes(30));

		signup(signupToken, PASSWORD, allConsents()).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("SIGNUP_TOKEN_INVALID"));
	}

	@Test
	void A84_코드는_10분_뒤에는_맞게_입력해도_거부된다() throws Exception {
		String email = newEmail();
		requestCode(email);
		String code = codeSentTo(email);
		this.clock.advance(Duration.ofMinutes(10));

		verify(email, code).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("EMAIL_CODE_EXPIRED"));
	}

	@Test
	void A84_코드는_5회_틀린_뒤에는_맞게_입력해도_거부된다() throws Exception {
		String email = newEmail();
		requestCode(email);
		String code = codeSentTo(email);
		String wrong = code.equals("000000") ? "111111" : "000000";
		for (int i = 0; i < 5; i++) {
			verify(email, wrong).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("EMAIL_CODE_INVALID"));
		}

		verify(email, code).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("EMAIL_CODE_EXPIRED"));
	}

	@Test
	void A85_이미_가입된_이메일로_요청해도_응답은_같고_안내_메일이_간다() throws Exception {
		String email = newEmail();
		signup(verifiedSignupToken(email), PASSWORD, allConsents()).andExpect(status().isCreated());
		this.clock.advance(Duration.ofMinutes(1));

		String registered = requestCode(email).andExpect(status().isAccepted())
			.andReturn()
			.getResponse()
			.getContentAsString();
		String fresh = requestCode(newEmail()).andExpect(status().isAccepted())
			.andReturn()
			.getResponse()
			.getContentAsString();

		assertThat(registered).isEqualTo(fresh);
		Mail last = this.mails.sentTo(email).getLast();
		assertThat(last.subject()).contains("이미 가입된");
		assertThat(last.body()).doesNotContainPattern("\\d{6}");
	}

	@Test
	void 같은_이메일로는_1분에_한_번만_요청할_수_있다() throws Exception {
		String email = newEmail();
		requestCode(email).andExpect(status().isAccepted());

		requestCode(email).andExpect(status().isTooManyRequests())
			.andExpect(jsonPath("$.code").value("TOO_MANY_REQUESTS"));

		this.clock.advance(Duration.ofMinutes(1));
		requestCode(email).andExpect(status().isAccepted());
	}

	@Test
	void 같은_이메일로_동시에_요청해도_한_번만_통과한다() throws Exception {
		String email = newEmail();
		CountDownLatch start = new CountDownLatch(1);
		Callable<String> request = () -> {
			start.await();
			try {
				this.signupService.requestCode(email);
				return "ACCEPTED";
			}
			catch (ApiException ex) {
				return ex.code().name();
			}
		};
		try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
			Future<String> first = pool.submit(request);
			Future<String> second = pool.submit(request);
			start.countDown();

			assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
				.containsExactlyInAnyOrder("ACCEPTED", "TOO_MANY_REQUESTS");
		}
		assertThat(this.mails.sentTo(email)).hasSize(1);
	}

	@Test
	void 같은_이메일로는_하루에_10번까지_요청할_수_있다() throws Exception {
		String email = newEmail();
		for (int i = 0; i < 10; i++) {
			requestCode(email).andExpect(status().isAccepted());
			this.clock.advance(Duration.ofMinutes(1));
		}

		requestCode(email).andExpect(status().isTooManyRequests());
	}

	@Test
	void 새로_받은_코드가_있으면_이전_코드는_쓸_수_없다() throws Exception {
		String email = newEmail();
		requestCode(email);
		String first = codeSentTo(email);
		this.clock.advance(Duration.ofMinutes(1));
		requestCode(email);
		String second = codeSentTo(email);

		if (!first.equals(second)) {
			verify(email, first).andExpect(jsonPath("$.code").value("EMAIL_CODE_INVALID"));
		}
		verify(email, second).andExpect(status().isOk());
	}

	@Test
	void 대소문자만_다른_이메일은_같은_주소로_본다() throws Exception {
		String email = newEmail();
		requestCode(email.toUpperCase());
		verify(email, codeSentTo(email)).andExpect(status().isOk());
	}

	@Test
	void 필수_약관에_모두_동의하지_않으면_가입할_수_없다() throws Exception {
		String signupToken = verifiedSignupToken(newEmail());

		signup(signupToken, PASSWORD, List.of(ConsentKind.TERMS_OF_SERVICE, ConsentKind.PRIVACY_POLICY))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
			.andExpect(jsonPath("$.errors[0].field").value("consents"));
	}

	@Test
	void 비밀번호_규칙에_맞지_않으면_400이고_서버_오류가_나지_않는다() throws Exception {
		String signupToken = verifiedSignupToken(newEmail());

		signup(signupToken, "짧은비번", allConsents()).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].field").value("password"));
		signup(signupToken, "가".repeat(25), allConsents()).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].field").value("password"));
	}

	private String verifiedSignupToken(String email) throws Exception {
		requestCode(email);
		String body = verify(email, codeSentTo(email)).andReturn().getResponse().getContentAsString();
		return JsonPath.read(body, "$.signupToken");
	}

	private String codeSentTo(String email) {
		Matcher matcher = CODE.matcher(this.mails.sentTo(email).getLast().body());
		assertThat(matcher.find()).isTrue();
		return matcher.group(1);
	}

	private ResultActions requestCode(String email) throws Exception {
		return this.mvc.perform(post("/auth/signup/email-code").contentType(MediaType.APPLICATION_JSON)
			.content("{\"email\":\"" + email + "\"}"));
	}

	private ResultActions verify(String email, String code) throws Exception {
		return this.mvc.perform(post("/auth/signup/email-code/verify").contentType(MediaType.APPLICATION_JSON)
			.content("{\"email\":\"" + email + "\",\"code\":\"" + code + "\"}"));
	}

	private ResultActions signup(String signupToken, String password, List<ConsentKind> consentKinds) throws Exception {
		String consentsJson = consentKinds.stream().map(kind -> "\"" + kind + "\"").toList().toString();
		return this.mvc.perform(post("/auth/signup").contentType(MediaType.APPLICATION_JSON)
			.content("{\"signupToken\":\"" + signupToken + "\",\"password\":\"" + password
					+ "\",\"name\":\"엄마\",\"screenSize\":\"LARGE\",\"consents\":" + consentsJson + "}"));
	}

	private static List<ConsentKind> allConsents() {
		return List.of(ConsentKind.values());
	}

	private static String newEmail() {
		return "user-" + UUID.randomUUID() + "@example.com";
	}

}
