package com.woorizip.auth.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import com.woorizip.auth.domain.RefreshToken;
import com.woorizip.auth.domain.RefreshTokenRepository;
import com.woorizip.common.error.ApiException;
import com.woorizip.common.error.ErrorCode;
import com.woorizip.user.domain.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * access token과 refresh token을 발급하고 바꾼다 (ADR 0009).
 *
 * <ul>
 * <li>refresh token은 무작위 256비트이고 해시만 저장한다.</li>
 * <li>재발급할 때마다 같은 세션의 새 토큰으로 바꾼다(rotation). 이미 바꾼 토큰이 다시 오면 탈취로 보고 그 사용자의 토큰을
 * 모두 폐기한다.</li>
 * <li>만료는 발급 시각부터 30일이고, 세션을 시작한 뒤 1년을 넘지 않는다.</li>
 * </ul>
 */
@Service
public class SessionTokens {

	static final Duration REFRESH_IDLE_TTL = Duration.ofDays(30);

	static final Duration SESSION_MAX_AGE = Duration.ofDays(365);

	private final RefreshTokenRepository refreshTokens;

	private final AccessTokenIssuer accessTokens;

	private final Clock clock;

	SessionTokens(RefreshTokenRepository refreshTokens, AccessTokenIssuer accessTokens, Clock clock) {
		this.refreshTokens = refreshTokens;
		this.accessTokens = accessTokens;
		this.clock = clock;
	}

	/** 로그인이나 가입으로 새 세션을 시작한다. */
	@Transactional(propagation = Propagation.MANDATORY)
	public Tokens startSession(User user) {
		Instant now = this.clock.instant();
		return issue(user, UUID.randomUUID(), now, now);
	}

	/**
	 * refresh token을 새것으로 바꾸고 access token을 새로 발급한다. 탈취를 의심해 폐기한 것은 실패 응답을 보내도 남아야
	 * 하므로 이 예외로는 롤백하지 않는다.
	 */
	@Transactional(noRollbackFor = ApiException.class)
	public Tokens refresh(String rawRefreshToken) {
		Instant now = this.clock.instant();
		RefreshToken current = this.refreshTokens.findByTokenHash(SecretHashing.sha256(rawRefreshToken))
			.orElseThrow(SessionTokens::invalid);
		if (current.isRevoked()) {
			throw invalid();
		}
		if (current.isRotated()) {
			// 이미 바꾼 토큰이 다시 왔다. 훔친 쪽과 원래 사용자 중 누구인지 알 수 없어 모두 끊는다(RFC 9700).
			this.refreshTokens.revokeAllOfUser(current.getUser().getId(), now);
			throw invalid();
		}
		if (current.isExpired(now)) {
			throw invalid();
		}
		current.markRotated(now);
		return issue(current.getUser(), current.getSessionId(), current.getSessionStartedAt(), now);
	}

	/** 그 기기(세션)의 토큰을 폐기한다. 없거나 이미 폐기된 토큰이어도 실패로 보지 않는다. */
	@Transactional
	public void logout(String rawRefreshToken) {
		this.refreshTokens.findByTokenHash(SecretHashing.sha256(rawRefreshToken))
			.ifPresent(token -> this.refreshTokens.revokeSession(token.getSessionId(), this.clock.instant()));
	}

	private Tokens issue(User user, UUID sessionId, Instant sessionStartedAt, Instant now) {
		String raw = SecretHashing.newToken();
		Instant idleExpiry = now.plus(REFRESH_IDLE_TTL);
		Instant maxExpiry = sessionStartedAt.plus(SESSION_MAX_AGE);
		Instant expiresAt = idleExpiry.isBefore(maxExpiry) ? idleExpiry : maxExpiry;
		this.refreshTokens
			.save(new RefreshToken(user, sessionId, SecretHashing.sha256(raw), sessionStartedAt, now, expiresAt));
		return new Tokens(user.getId(), this.accessTokens.issue(user.getId()), raw, expiresAt);
	}

	private static ApiException invalid() {
		return new ApiException(ErrorCode.REFRESH_TOKEN_INVALID);
	}

	public record Tokens(long userId, AccessTokenIssuer.AccessToken accessToken, String refreshToken,
			Instant refreshTokenExpiresAt) {
	}

}
