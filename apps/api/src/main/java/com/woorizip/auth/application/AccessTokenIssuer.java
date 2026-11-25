package com.woorizip.auth.application;

import java.time.Clock;
import java.time.Instant;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/**
 * access token(JWT)을 발급한다 (ADR 0009).
 *
 * <p>토큰은 사용자 ID에 연결하고 이메일에는 연결하지 않는다. 이메일이 바뀌어도 로그인이 유지된다(A-48).
 */
@Service
public class AccessTokenIssuer {

	private final JwtEncoder encoder;

	private final JwtProperties properties;

	private final Clock clock;

	AccessTokenIssuer(JwtEncoder encoder, JwtProperties properties, Clock clock) {
		this.encoder = encoder;
		this.properties = properties;
		this.clock = clock;
	}

	public AccessToken issue(long userId) {
		Instant now = this.clock.instant();
		Instant expiresAt = now.plus(this.properties.accessTokenTtl());
		JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).keyId(this.properties.currentKeyId()).build();
		JwtClaimsSet claims = JwtClaimsSet.builder()
			.issuer(this.properties.issuer())
			.subject(Long.toString(userId))
			.issuedAt(now)
			.expiresAt(expiresAt)
			.build();
		String value = this.encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
		return new AccessToken(value, expiresAt);
	}

	public record AccessToken(String value, Instant expiresAt) {
	}

}
