package com.woorizip.auth.application;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import com.woorizip.auth.domain.Emails;
import com.woorizip.auth.domain.IdentityProvider;
import com.woorizip.auth.domain.LoginFailure;
import com.woorizip.auth.domain.LoginFailureRepository;
import com.woorizip.auth.domain.UserIdentity;
import com.woorizip.auth.domain.UserIdentityRepository;
import com.woorizip.common.error.ApiException;
import com.woorizip.common.error.ErrorCode;
import com.woorizip.infra.db.AdvisoryLocks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 이메일 로그인 (ADR 0009, 03-architecture 8장).
 *
 * <p>이메일이 없을 때와 비밀번호가 틀렸을 때 같은 응답을 준다(A-47). 응답 시간도 맞추려고, 이메일이 없으면 가짜 해시로
 * 비밀번호를 한 번 비교한다(Spring Security {@code DaoAuthenticationProvider}와 같은 방식). 연속 실패 횟수도 가입
 * 여부와 관계없이 이메일로 센다. 잠금과 재설정 요구도 같은 이유로 가입 여부와 관계없이 같은 응답을 준다.
 */
@Service
public class LoginService {

	private static final Logger log = LoggerFactory.getLogger(LoginService.class);

	private static final String USER_NOT_FOUND_PASSWORD = "userNotFoundPassword";

	private final UserIdentityRepository identities;

	private final LoginFailureRepository failures;

	private final PasswordEncoder passwordEncoder;

	private final SessionTokens sessions;

	private final AdvisoryLocks locks;

	private final Clock clock;

	private volatile String userNotFoundHash;

	LoginService(UserIdentityRepository identities, LoginFailureRepository failures, PasswordEncoder passwordEncoder,
			SessionTokens sessions, AdvisoryLocks locks, Clock clock) {
		this.identities = identities;
		this.failures = failures;
		this.passwordEncoder = passwordEncoder;
		this.sessions = sessions;
		this.locks = locks;
		this.clock = clock;
	}

	/** 실패 횟수는 실패 응답을 보내도 남아야 하므로 이 예외로는 롤백하지 않는다. */
	@Transactional(noRollbackFor = ApiException.class)
	public SessionTokens.Tokens login(String rawEmail, String password) {
		String email = Emails.normalize(rawEmail);
		// 같은 이메일로 동시에 시도해도 실패 횟수와 잠금이 빠지지 않게 한 번에 하나씩 처리한다.
		this.locks.lockForTransaction("login-email:" + email);
		Instant now = this.clock.instant();
		Optional<LoginFailure> failure = this.failures.findById(email);
		if (failure.isPresent() && failure.get().requiresPasswordReset()) {
			throw new ApiException(ErrorCode.PASSWORD_RESET_REQUIRED);
		}
		if (failure.isPresent() && failure.get().isLocked(now)) {
			throw ApiException.retryAfter(ErrorCode.LOGIN_LOCKED, failure.get().remainingLock(now));
		}
		Optional<UserIdentity> identity = this.identities.findByProviderAndSubject(IdentityProvider.EMAIL, email);
		if (!passwordMatches(identity, password)) {
			LoginFailure record = failure.orElseGet(() -> new LoginFailure(email));
			record.recordFailure(now);
			this.failures.save(record);
			logLock(record, now);
			throw new ApiException(ErrorCode.LOGIN_FAILED);
		}
		failure.ifPresent(this.failures::delete);
		return this.sessions.startSession(identity.get().getUser());
	}

	/** 잠금이 얼마나 자주 걸리는지 보려고 남긴다(OWASP 인증 치트시트). 이메일은 개인정보라 남기지 않는다. */
	private static void logLock(LoginFailure record, Instant now) {
		if (record.requiresPasswordReset()) {
			log.warn("로그인 잠금: 연속 {}번 실패해 비밀번호 재설정이 필요함", record.getConsecutiveFailures());
		}
		else if (record.isLocked(now)) {
			log.info("로그인 잠금: 연속 {}번 실패, {}초", record.getConsecutiveFailures(),
					record.remainingLock(now).toSeconds());
		}
	}

	private boolean passwordMatches(Optional<UserIdentity> identity, String password) {
		// 가입 때 72바이트를 넘는 비밀번호는 받지 않았다. BCrypt가 예외를 던지기 전에 실패로 처리한다.
		if (password.getBytes(StandardCharsets.UTF_8).length > PasswordPolicy.MAX_BYTES) {
			return false;
		}
		if (identity.isEmpty()) {
			this.passwordEncoder.matches(password, userNotFoundHash());
			return false;
		}
		return this.passwordEncoder.matches(password, identity.get().getPasswordHash());
	}

	private String userNotFoundHash() {
		if (this.userNotFoundHash == null) {
			this.userNotFoundHash = this.passwordEncoder.encode(USER_NOT_FOUND_PASSWORD);
		}
		return this.userNotFoundHash;
	}

}
