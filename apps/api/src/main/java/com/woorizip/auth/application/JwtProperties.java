package com.woorizip.auth.application;

import java.time.Duration;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * access token(JWT) 설정 (ADR 0009).
 *
 * <p>서명 키는 저장소에 두지 않는다. 운영은 Parameter Store에서, 로컬은 local 프로필에서 받는다(ADR 0014).
 * 값이 없으면 서버가 시작하지 않는다.
 *
 * @param issuer 토큰을 발급한 곳. 검증할 때 같은지 확인한다
 * @param accessTokenTtl access token 유효시간 (15분)
 * @param currentKeyId 새 토큰에 서명할 키의 ID. 토큰 헤더의 {@code kid}에 들어간다
 * @param keys 검증에 쓸 수 있는 키 목록. 키를 바꿀 때는 새 키를 더하고 currentKeyId를 바꾼 뒤, 이전 키를 지운다
 */
@Validated
@ConfigurationProperties("woorizip.auth.jwt")
public record JwtProperties(@NotBlank String issuer, @NotNull Duration accessTokenTtl, @NotBlank String currentKeyId,
		@NotEmpty List<@Valid Key> keys) {

	/**
	 * @param id 키 ID
	 * @param secret HS256 서명 키. UTF-8로 32바이트(256비트) 이상이어야 한다 (RFC 7518 3.2)
	 */
	public record Key(@NotBlank String id, @NotBlank String secret) {
	}

}
