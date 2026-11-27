package com.woorizip.auth.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.woorizip.auth.domain.EmailCode;
import com.woorizip.auth.domain.EmailCodePurpose;
import com.woorizip.auth.domain.EmailCodeRepository;
import com.woorizip.auth.domain.Emails;
import com.woorizip.auth.domain.IdentityProvider;
import com.woorizip.auth.domain.LoginFailureRepository;
import com.woorizip.auth.domain.PasswordResetTicket;
import com.woorizip.auth.domain.PasswordResetTicketRepository;
import com.woorizip.auth.domain.RefreshTokenRepository;
import com.woorizip.auth.domain.UserIdentity;
import com.woorizip.auth.domain.UserIdentityRepository;
import com.woorizip.common.error.ApiException;
import com.woorizip.common.error.ApiProblem;
import com.woorizip.common.error.ErrorCode;
import com.woorizip.infra.db.AdvisoryLocks;
import com.woorizip.infra.mail.Mail;
import com.woorizip.infra.mail.MailKind;
import com.woorizip.infra.mail.MailOutbox;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 비밀번호 재설정 (ADR 0009, user-flows B, docs/plans/auth.md PR 4).
 *
 * <ol>
 * <li>코드 요청: 가입된 이메일이면 6자리 코드를 메일로 보낸다. 가입하지 않은 이메일이면 메일은 보내지 않고 코드만
 * 저장한다.</li>
 * <li>코드 확인: 맞으면 재설정 확인 값을 발급한다.</li>
 * <li>재설정: 비밀번호를 바꾸고, 그 사용자의 refresh token을 모두 폐기하고(A-39), 로그인 실패 기록을 지우고, 변경
 * 안내 메일을 보낸다. 로그인 상태로 만들지 않는다.</li>
 * </ol>
 *
 * <p>가입한 이메일과 가입하지 않은 이메일은 응답, 요청 횟수 제한, 코드 확인 결과가 모두 같다. 메일은 요청과 다른
 * 스레드에서 보내므로 응답 시간도 메일 발송에 영향을 받지 않는다.
 */
@Service
public class PasswordResetService {

	static final Duration CODE_TTL = Duration.ofMinutes(10);

	static final Duration TICKET_TTL = Duration.ofMinutes(10);

	static final Duration CHANGED_MAIL_TTL = Duration.ofDays(1);

	static final Duration REQUEST_INTERVAL = Duration.ofMinutes(1);

	static final int DAILY_REQUEST_LIMIT = 10;

	private final EmailCodeRepository codes;

	private final PasswordResetTicketRepository tickets;

	private final UserIdentityRepository identities;

	private final RefreshTokenRepository refreshTokens;

	private final LoginFailureRepository loginFailures;

	private final MailOutbox mails;

	private final PasswordEncoder passwordEncoder;

	private final AdvisoryLocks locks;

	private final Clock clock;

	PasswordResetService(EmailCodeRepository codes, PasswordResetTicketRepository tickets,
			UserIdentityRepository identities, RefreshTokenRepository refreshTokens,
			LoginFailureRepository loginFailures, MailOutbox mails, PasswordEncoder passwordEncoder,
			AdvisoryLocks locks, Clock clock) {
		this.codes = codes;
		this.tickets = tickets;
		this.identities = identities;
		this.refreshTokens = refreshTokens;
		this.loginFailures = loginFailures;
		this.mails = mails;
		this.passwordEncoder = passwordEncoder;
		this.locks = locks;
		this.clock = clock;
	}

	/**
	 * 재설정 코드를 요청한다. 요청 횟수는 가입 여부와 관계없이 저장한 코드 수로 센다. 같은 이메일로 동시에 요청해도 횟수
	 * 제한을 함께 통과하지 않도록 이메일마다 잠그고 센다.
	 */
	@Transactional
	public void requestCode(String rawEmail) {
		String email = Emails.normalize(rawEmail);
		this.locks.lockForTransaction("password-reset-email:" + email);
		Instant now = this.clock.instant();
		if (requestsSince(email, now.minus(REQUEST_INTERVAL)) > 0
				|| requestsSince(email, now.minus(Duration.ofDays(1))) >= DAILY_REQUEST_LIMIT) {
			throw new ApiException(ErrorCode.TOO_MANY_REQUESTS);
		}
		String code = SecretHashing.newSixDigitCode();
		Instant expiresAt = now.plus(CODE_TTL);
		this.codes.save(new EmailCode(EmailCodePurpose.PASSWORD_RESET, email, codeHash(email, code), now, expiresAt));
		if (this.identities.existsByProviderAndSubject(IdentityProvider.EMAIL, email)) {
			this.mails.enqueue(MailKind.PASSWORD_RESET_CODE, ResetMails.code(email, code), expiresAt);
		}
	}

	/** 코드를 확인한다. 틀린 횟수는 실패 응답을 보내도 남아야 하므로 이 예외로는 롤백하지 않는다. */
	@Transactional(noRollbackFor = ApiException.class)
	public IssuedTicket verifyCode(String rawEmail, String code) {
		String email = Emails.normalize(rawEmail);
		Instant now = this.clock.instant();
		EmailCode latest = this.codes
			.findFirstByPurposeAndEmailOrderByCreatedAtDescIdDesc(EmailCodePurpose.PASSWORD_RESET, email)
			.filter(candidate -> candidate.isUsable(now))
			.orElseThrow(() -> new ApiException(ErrorCode.EMAIL_CODE_EXPIRED));
		if (!SecretHashing.sameHash(latest.getCodeHash(), codeHash(email, code))) {
			latest.recordFailedAttempt();
			throw new ApiException(ErrorCode.EMAIL_CODE_INVALID);
		}
		latest.markUsed(now);
		String token = SecretHashing.newToken();
		PasswordResetTicket ticket = this.tickets
			.save(new PasswordResetTicket(email, SecretHashing.sha256(token), now, now.plus(TICKET_TTL)));
		return new IssuedTicket(token, ticket.getExpiresAt());
	}

	/**
	 * 비밀번호를 바꾼다. 확인 값의 이메일로 가입된 계정이 없으면(가짜 코드를 맞힌 경우) 아무것도 바꾸지 않지만, 응답과
	 * 비밀번호 해시 계산은 같게 한다.
	 */
	@Transactional
	public void reset(String resetToken, String newPassword) {
		PasswordPolicy.violation(newPassword).ifPresent(message -> {
			throw ApiException.validationFailed(List.of(new ApiProblem.FieldError("newPassword", message)));
		});
		Instant now = this.clock.instant();
		PasswordResetTicket ticket = this.tickets.findByTokenHash(SecretHashing.sha256(resetToken))
			.filter(candidate -> candidate.isUsable(now))
			.orElseThrow(() -> new ApiException(ErrorCode.PASSWORD_RESET_TOKEN_INVALID));
		String email = ticket.getEmail();
		// 로그인과 같은 잠금을 잡아, 로그인 실패 기록을 지우는 동안 로그인이 끼어들지 않게 한다.
		this.locks.lockForTransaction("login-email:" + email);
		String passwordHash = this.passwordEncoder.encode(newPassword);
		ticket.markUsed(now);
		this.tickets.invalidateUnused(email, now);
		Optional<UserIdentity> identity = this.identities.findByProviderAndSubject(IdentityProvider.EMAIL, email);
		if (identity.isEmpty()) {
			return;
		}
		identity.get().changePassword(passwordHash);
		this.refreshTokens.revokeAllOfUser(identity.get().getUser().getId(), now);
		this.loginFailures.findById(email).ifPresent(this.loginFailures::delete);
		this.mails.enqueue(MailKind.PASSWORD_CHANGED, ResetMails.changed(email), now.plus(CHANGED_MAIL_TTL));
	}

	private long requestsSince(String email, Instant since) {
		return this.codes.countByPurposeAndEmailAndCreatedAtAfter(EmailCodePurpose.PASSWORD_RESET, email, since);
	}

	private static String codeHash(String email, String code) {
		return SecretHashing.sha256(EmailCodePurpose.PASSWORD_RESET.name(), email, code);
	}

	public record IssuedTicket(String resetToken, Instant expiresAt) {
	}

	/** 재설정 메일 문구. 본문에는 코드와 안내만 담는다(ADR 0018). */
	static final class ResetMails {

		private ResetMails() {
		}

		static Mail code(String to, String code) {
			return new Mail(to, "[우리.zip] 비밀번호 재설정 코드", "인증 코드: " + code
					+ "\n\n10분 안에 앱에 입력해 주세요.\n비밀번호 재설정을 요청하지 않았다면 이 메일은 무시해도 됩니다. 비밀번호는 바뀌지 않습니다.");
		}

		static Mail changed(String to) {
			return new Mail(to, "[우리.zip] 비밀번호가 변경되었습니다",
					"계정의 비밀번호가 변경되었고, 모든 기기에서 로그아웃되었습니다.\n직접 바꾸지 않았다면 앱의 비밀번호 찾기로 바로 다시 바꿔 주세요.");
		}

	}

}
