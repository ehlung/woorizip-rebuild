package com.woorizip.auth.application;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * 비밀번호 규칙 (docs/plans/auth.md).
 *
 * <ul>
 * <li>8자 이상 64자 이하. 한글과 이모지도 한 글자로 센다(NIST SP 800-63B-4: 코드 포인트 하나를 한 글자로).</li>
 * <li>대문자, 특수문자 같은 문자 종류 규칙은 두지 않는다(NIST가 금지).</li>
 * <li>UTF-8로 72바이트 이하. BCrypt는 72바이트를 넘으면 예외를 던진다(Spring Security 7.1.3에서 확인).
 * 한글은 한 글자가 3바이트라 24자까지다.</li>
 * </ul>
 *
 * <p>NIST는 비밀번호만으로 로그인하면 15자 이상을 요구하지만, 연세 많은 사용자의 입력 부담 때문에 8자로 정했다.
 */
public final class PasswordPolicy {

	static final int MIN_LENGTH = 8;

	static final int MAX_LENGTH = 64;

	static final int MAX_BYTES = 72;

	private PasswordPolicy() {
	}

	/** 규칙에 맞지 않으면 그 이유. */
	public static Optional<String> violation(String password) {
		int length = password.codePointCount(0, password.length());
		if (length < MIN_LENGTH) {
			return Optional.of(MIN_LENGTH + "자 이상이어야 합니다.");
		}
		if (length > MAX_LENGTH) {
			return Optional.of(MAX_LENGTH + "자 이하여야 합니다.");
		}
		if (password.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
			return Optional.of("너무 깁니다. 한글은 24자까지 쓸 수 있습니다.");
		}
		return Optional.empty();
	}

}
