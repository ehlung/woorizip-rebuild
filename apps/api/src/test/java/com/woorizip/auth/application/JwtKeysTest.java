package com.woorizip.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;

/** 서명 키 설정과 토큰 검증 규칙 (ADR 0009). Spring 컨텍스트 없이 확인한다. */
class JwtKeysTest {

	private static final String SECRET_A = "a".repeat(32);

	private static final String SECRET_B = "b".repeat(32);

	@Test
	void 현재_키로_서명한_토큰을_검증한다() {
		JwtKeys keys = keys("k1", key("k1", SECRET_A));

		Jwt jwt = keys.decoder().decode(token(keys, "k1", "woorizip", Instant.now().plusSeconds(900)));

		assertThat(jwt.getSubject()).isEqualTo("42");
		assertThat(jwt.getHeaders()).containsEntry("kid", "k1").containsEntry("alg", "HS256");
	}

	@Test
	void 키를_바꾼_뒤에도_목록에_남아_있는_이전_키의_토큰은_검증한다() {
		JwtKeys before = keys("k1", key("k1", SECRET_A));
		String oldToken = token(before, "k1", "woorizip", Instant.now().plusSeconds(900));

		JwtKeys after = keys("k2", key("k1", SECRET_A), key("k2", SECRET_B));

		assertThat(after.decoder().decode(oldToken).getSubject()).isEqualTo("42");
	}

	@Test
	void 목록에서_지운_키로_서명한_토큰은_거부한다() {
		JwtKeys before = keys("k1", key("k1", SECRET_A));
		String oldToken = token(before, "k1", "woorizip", Instant.now().plusSeconds(900));

		JwtKeys after = keys("k2", key("k2", SECRET_B));

		assertThatThrownBy(() -> after.decoder().decode(oldToken)).isInstanceOf(JwtException.class);
	}

	@Test
	void 같은_키_ID라도_다른_키로_서명한_토큰은_거부한다() {
		JwtKeys attacker = keys("k1", key("k1", SECRET_B));
		String forged = token(attacker, "k1", "woorizip", Instant.now().plusSeconds(900));

		JwtKeys server = keys("k1", key("k1", SECRET_A));

		assertThatThrownBy(() -> server.decoder().decode(forged)).isInstanceOf(JwtException.class);
	}

	@Test
	void 만료된_토큰은_거부한다() {
		JwtKeys keys = keys("k1", key("k1", SECRET_A));
		// Spring의 만료 검증은 시계 오차로 60초를 허용한다(JwtTimestampValidator 기본값). 그보다 오래 지난 토큰을 쓴다.
		String expired = token(keys, "k1", "woorizip", Instant.now().minus(Duration.ofMinutes(5)));

		assertThatThrownBy(() -> keys.decoder().decode(expired)).isInstanceOf(JwtException.class);
	}

	@Test
	void 발급자가_다른_토큰은_거부한다() {
		JwtKeys keys = keys("k1", key("k1", SECRET_A));
		String other = token(keys, "k1", "someone-else", Instant.now().plusSeconds(900));

		assertThatThrownBy(() -> keys.decoder().decode(other)).isInstanceOf(JwtException.class);
	}

	@Test
	void 서명이_없는_토큰은_거부한다() {
		JwtKeys keys = keys("k1", key("k1", SECRET_A));
		String header = base64("{\"alg\":\"none\",\"kid\":\"k1\"}");
		String payload = base64("{\"iss\":\"woorizip\",\"sub\":\"42\",\"exp\":" + Instant.now().plusSeconds(900).getEpochSecond() + "}");

		assertThatThrownBy(() -> keys.decoder().decode(header + "." + payload + "."))
			.isInstanceOf(JwtException.class);
	}

	@Test
	void 키가_256비트보다_짧으면_시작하지_않는다() {
		assertThatThrownBy(() -> keys("k1", key("k1", "a".repeat(31)))).isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("32바이트");
	}

	@Test
	void 현재_키_ID가_목록에_없으면_시작하지_않는다() {
		assertThatThrownBy(() -> keys("k9", key("k1", SECRET_A))).isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("k9");
	}

	@Test
	void 같은_키_ID가_두_번_있으면_시작하지_않는다() {
		assertThatThrownBy(() -> keys("k1", key("k1", SECRET_A), key("k1", SECRET_B)))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("같은 키 ID");
	}

	private static JwtKeys keys(String currentKeyId, JwtProperties.Key... keys) {
		return new JwtKeys(new JwtProperties("woorizip", Duration.ofMinutes(15), currentKeyId, List.of(keys)));
	}

	private static JwtProperties.Key key(String id, String secret) {
		return new JwtProperties.Key(id, secret);
	}

	private static String token(JwtKeys keys, String keyId, String issuer, Instant expiresAt) {
		JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).keyId(keyId).build();
		JwtClaimsSet claims = JwtClaimsSet.builder()
			.issuer(issuer)
			.subject("42")
			.issuedAt(expiresAt.minus(Duration.ofMinutes(15)))
			.expiresAt(expiresAt)
			.build();
		return keys.encoder().encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
	}

	private static String base64(String json) {
		return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
	}

}
