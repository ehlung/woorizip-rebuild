package com.woorizip.auth.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import com.woorizip.auth.domain.EmailCode;
import com.woorizip.auth.domain.EmailCodePurpose;
import com.woorizip.auth.domain.EmailCodeRepository;
import com.woorizip.auth.domain.Emails;
import com.woorizip.auth.domain.IdentityProvider;
import com.woorizip.auth.domain.SignupTicket;
import com.woorizip.auth.domain.SignupTicketRepository;
import com.woorizip.auth.domain.UserIdentity;
import com.woorizip.auth.domain.UserIdentityRepository;
import com.woorizip.common.error.ApiException;
import com.woorizip.common.error.ApiProblem;
import com.woorizip.common.error.ErrorCode;
import com.woorizip.infra.db.AdvisoryLocks;
import com.woorizip.infra.mail.Mail;
import com.woorizip.infra.mail.MailKind;
import com.woorizip.infra.mail.MailOutbox;
import com.woorizip.user.domain.ConsentKind;
import com.woorizip.user.domain.ScreenSize;
import com.woorizip.user.domain.User;
import com.woorizip.user.domain.UserConsent;
import com.woorizip.user.domain.UserConsentRepository;
import com.woorizip.user.domain.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 이메일 가입 (03-architecture 8장, user-flows A).
 *
 * <ol>
 * <li>코드 요청: 6자리 코드를 메일로 보낸다. 이미 가입된 이메일이면 코드 대신 안내 메일을 보내고, 응답은 같다.</li>
 * <li>코드 확인: 맞으면 가입 확인 값을 발급한다.</li>
 * <li>가입: 확인 값을 검증하고 계정을 만든 뒤 access token을 발급한다.</li>
 * </ol>
 */
@Service
public class SignupService {

	static final Duration CODE_TTL = Duration.ofMinutes(10);

	static final Duration TICKET_TTL = Duration.ofMinutes(30);

	static final Duration ALREADY_REGISTERED_MAIL_TTL = Duration.ofHours(1);

	static final Duration REQUEST_INTERVAL = Duration.ofMinutes(1);

	static final int DAILY_REQUEST_LIMIT = 10;

	/** 약관 문서가 아직 없어 v0으로 기록한다. 약관을 쓰면 올린다. */
	static final String CONSENT_VERSION = "v0";

	private static final Set<ConsentKind> REQUIRED_CONSENTS = EnumSet.allOf(ConsentKind.class);

	private static final Set<MailKind> SIGNUP_MAILS = EnumSet.of(MailKind.SIGNUP_CODE,
			MailKind.SIGNUP_ALREADY_REGISTERED);

	private final EmailCodeRepository codes;

	private final SignupTicketRepository tickets;

	private final UserRepository users;

	private final UserIdentityRepository identities;

	private final UserConsentRepository consents;

	private final MailOutbox mails;

	private final PasswordEncoder passwordEncoder;

	private final AccessTokenIssuer accessTokens;

	private final AdvisoryLocks locks;

	private final Clock clock;

	SignupService(EmailCodeRepository codes, SignupTicketRepository tickets, UserRepository users,
			UserIdentityRepository identities, UserConsentRepository consents, MailOutbox mails,
			PasswordEncoder passwordEncoder, AccessTokenIssuer accessTokens, AdvisoryLocks locks, Clock clock) {
		this.codes = codes;
		this.tickets = tickets;
		this.users = users;
		this.identities = identities;
		this.consents = consents;
		this.mails = mails;
		this.passwordEncoder = passwordEncoder;
		this.accessTokens = accessTokens;
		this.locks = locks;
		this.clock = clock;
	}

	/**
	 * 가입 코드를 요청한다. 가입 여부와 관계없이 같은 요청 횟수 제한을 걸어, 응답이나 제한으로 가입 여부를 알 수 없게
	 * 한다(A-85). 같은 이메일로 동시에 요청해도 횟수 제한을 함께 통과하지 않도록, 이메일마다 잠그고 센다.
	 */
	@Transactional
	public void requestCode(String rawEmail) {
		String email = Emails.normalize(rawEmail);
		this.locks.lockForTransaction("signup-email:" + email);
		Instant now = this.clock.instant();
		if (this.mails.countSince(email, SIGNUP_MAILS, now.minus(REQUEST_INTERVAL)) > 0
				|| this.mails.countSince(email, SIGNUP_MAILS, now.minus(Duration.ofDays(1))) >= DAILY_REQUEST_LIMIT) {
			throw new ApiException(ErrorCode.TOO_MANY_REQUESTS);
		}
		if (this.identities.existsByProviderAndSubject(IdentityProvider.EMAIL, email)) {
			this.mails.enqueue(MailKind.SIGNUP_ALREADY_REGISTERED, SignupMails.alreadyRegistered(email),
					now.plus(ALREADY_REGISTERED_MAIL_TTL));
			return;
		}
		String code = SecretHashing.newSixDigitCode();
		Instant expiresAt = now.plus(CODE_TTL);
		this.codes.save(new EmailCode(EmailCodePurpose.SIGNUP, email, codeHash(email, code), now, expiresAt));
		this.mails.enqueue(MailKind.SIGNUP_CODE, SignupMails.code(email, code), expiresAt);
	}

	/**
	 * 코드를 확인한다. 틀린 횟수는 실패 응답을 보내도 남아야 하므로 이 예외로는 롤백하지 않는다.
	 */
	@Transactional(noRollbackFor = ApiException.class)
	public IssuedTicket verifyCode(String rawEmail, String code) {
		String email = Emails.normalize(rawEmail);
		Instant now = this.clock.instant();
		EmailCode latest = this.codes.findFirstByPurposeAndEmailOrderByCreatedAtDescIdDesc(EmailCodePurpose.SIGNUP, email)
			.filter(candidate -> candidate.isUsable(now))
			.orElseThrow(() -> new ApiException(ErrorCode.EMAIL_CODE_EXPIRED));
		if (!SecretHashing.sameHash(latest.getCodeHash(), codeHash(email, code))) {
			latest.recordFailedAttempt();
			throw new ApiException(ErrorCode.EMAIL_CODE_INVALID);
		}
		latest.markUsed(now);
		String token = SecretHashing.newToken();
		SignupTicket ticket = this.tickets
			.save(new SignupTicket(email, SecretHashing.sha256(token), now, now.plus(TICKET_TTL)));
		return new IssuedTicket(token, ticket.getExpiresAt());
	}

	@Transactional
	public SignedUp signup(SignupCommand command) {
		validate(command);
		Instant now = this.clock.instant();
		SignupTicket ticket = this.tickets.findByTokenHash(SecretHashing.sha256(command.signupToken()))
			.filter(candidate -> candidate.isUsable(now))
			.orElseThrow(() -> new ApiException(ErrorCode.SIGNUP_TOKEN_INVALID));
		ticket.markUsed(now);
		if (this.identities.existsByProviderAndSubject(IdentityProvider.EMAIL, ticket.getEmail())) {
			throw new ApiException(ErrorCode.EMAIL_ALREADY_REGISTERED);
		}
		User user = this.users.save(new User(command.name().strip(), command.screenSize()));
		try {
			this.identities.saveAndFlush(
					UserIdentity.email(user, ticket.getEmail(), this.passwordEncoder.encode(command.password())));
		}
		catch (DataIntegrityViolationException ex) {
			// 확인과 저장 사이에 같은 이메일로 먼저 가입한 경우
			throw new ApiException(ErrorCode.EMAIL_ALREADY_REGISTERED);
		}
		for (ConsentKind kind : command.consents()) {
			this.consents.save(new UserConsent(user, kind, CONSENT_VERSION, now));
		}
		AccessTokenIssuer.AccessToken token = this.accessTokens.issue(user.getId());
		return new SignedUp(user.getId(), token);
	}

	private static void validate(SignupCommand command) {
		List<ApiProblem.FieldError> errors = new ArrayList<>();
		PasswordPolicy.violation(command.password())
			.ifPresent(message -> errors.add(new ApiProblem.FieldError("password", message)));
		if (!command.consents().containsAll(REQUIRED_CONSENTS)) {
			errors.add(new ApiProblem.FieldError("consents", "필수 약관에 모두 동의해야 합니다."));
		}
		if (!errors.isEmpty()) {
			throw ApiException.validationFailed(errors);
		}
	}

	private static String codeHash(String email, String code) {
		return SecretHashing.sha256(EmailCodePurpose.SIGNUP.name(), email, code);
	}

	public record IssuedTicket(String signupToken, Instant expiresAt) {
	}

	public record SignupCommand(String signupToken, String password, String name, ScreenSize screenSize,
			Set<ConsentKind> consents) {
	}

	public record SignedUp(long userId, AccessTokenIssuer.AccessToken accessToken) {
	}

	/** 가입 메일 문구. 본문에는 코드와 안내만 담는다(ADR 0018). */
	static final class SignupMails {

		private SignupMails() {
		}

		static Mail code(String to, String code) {
			return new Mail(to, "[우리.zip] 가입 인증 코드",
					"인증 코드: " + code + "\n\n10분 안에 앱에 입력해 주세요.\n요청하지 않았다면 이 메일은 무시해도 됩니다.");
		}

		static Mail alreadyRegistered(String to) {
			return new Mail(to, "[우리.zip] 이미 가입된 이메일입니다",
					"이 이메일로 가입된 계정이 있어요. 앱에서 로그인하거나, 비밀번호를 잊었다면 비밀번호 찾기로 다시 설정하세요.\n요청하지 않았다면 이 메일은 무시해도 됩니다.");
		}

	}

}
