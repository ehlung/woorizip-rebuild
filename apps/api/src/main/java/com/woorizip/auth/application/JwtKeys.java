package com.woorizip.auth.application;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.OctetSequenceKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * 설정의 서명 키로 JWT 인코더와 디코더를 만든다 (HS256, ADR 0009).
 *
 * <p>인코더는 현재 키 하나로 서명하고 헤더에 키 ID({@code kid})를 넣는다. 디코더는 토큰 헤더의 {@code kid}와
 * 알고리즘이 맞는 키를 설정의 키 목록에서 골라 검증한다. 목록에 없는 키로 서명한 토큰은 거부된다.
 */
public final class JwtKeys {

	/** RFC 7518 3.2: HS256에는 해시 출력 크기(256비트) 이상의 키를 써야 한다. */
	static final int MIN_SECRET_BYTES = 32;

	private final JwtProperties properties;

	private final List<JWK> jwks;

	private final JWK currentKey;

	public JwtKeys(JwtProperties properties) {
		this.properties = properties;
		this.jwks = properties.keys().stream().map(JwtKeys::toJwk).toList();
		this.currentKey = this.jwks.stream()
			.filter(jwk -> jwk.getKeyID().equals(properties.currentKeyId()))
			.findFirst()
			.orElseThrow(() -> new IllegalStateException(
					"woorizip.auth.jwt.current-key-id(" + properties.currentKeyId() + ")가 키 목록에 없습니다."));
		Set<String> ids = new HashSet<>();
		for (JWK jwk : this.jwks) {
			if (!ids.add(jwk.getKeyID())) {
				throw new IllegalStateException("woorizip.auth.jwt.keys에 같은 키 ID가 있습니다: " + jwk.getKeyID());
			}
		}
	}

	public JwtEncoder encoder() {
		return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(this.currentKey)));
	}

	public JwtDecoder decoder() {
		DefaultJWTProcessor<SecurityContext> processor = new DefaultJWTProcessor<>();
		processor.setJWSKeySelector(
				new JWSVerificationKeySelector<>(JWSAlgorithm.HS256, new ImmutableJWKSet<>(new JWKSet(this.jwks))));
		// 만료 시각과 발급자는 아래의 Spring 검증기가 확인한다.
		processor.setJWTClaimsSetVerifier((claims, context) -> {
		});
		NimbusJwtDecoder decoder = new NimbusJwtDecoder(processor);
		decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(this.properties.issuer()));
		return decoder;
	}

	private static JWK toJwk(JwtProperties.Key key) {
		byte[] secret = key.secret().getBytes(StandardCharsets.UTF_8);
		if (secret.length < MIN_SECRET_BYTES) {
			throw new IllegalStateException("서명 키 " + key.id() + "가 너무 짧습니다. " + MIN_SECRET_BYTES
					+ "바이트(256비트) 이상이어야 합니다 (RFC 7518 3.2).");
		}
		return new OctetSequenceKey.Builder(secret).keyID(key.id())
			.algorithm(JWSAlgorithm.HS256)
			.keyUse(KeyUse.SIGNATURE)
			.build();
	}

}
