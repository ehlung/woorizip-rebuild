package com.woorizip.auth.api;

import java.time.Instant;

import com.woorizip.auth.application.SessionTokens;

/** 가입, 로그인, 재발급의 응답. 앱은 refresh token을 SecureStore에, access token을 메모리에 둔다(ADR 0009). */
record TokenResponse(long userId, String accessToken, Instant accessTokenExpiresAt, String refreshToken,
		Instant refreshTokenExpiresAt) {

	static TokenResponse from(SessionTokens.Tokens tokens) {
		return new TokenResponse(tokens.userId(), tokens.accessToken().value(), tokens.accessToken().expiresAt(),
				tokens.refreshToken(), tokens.refreshTokenExpiresAt());
	}

}
