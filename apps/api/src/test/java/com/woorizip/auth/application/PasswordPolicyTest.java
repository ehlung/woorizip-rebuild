package com.woorizip.auth.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PasswordPolicyTest {

	@Test
	void 여덟_자부터_허용한다() {
		assertThat(PasswordPolicy.violation("1234567")).isPresent();
		assertThat(PasswordPolicy.violation("12345678")).isEmpty();
	}

	@Test
	void 문자_종류_규칙은_없고_띄어쓰기와_한글을_받는다() {
		assertThat(PasswordPolicy.violation("우리 가족 사랑해요")).isEmpty();
	}

	@Test
	void 한글은_한_글자를_한_자로_센다() {
		assertThat(PasswordPolicy.violation("가나다라마바사")).isPresent();
		assertThat(PasswordPolicy.violation("가나다라마바사아")).isEmpty();
	}

	@Test
	void 육십사_자를_넘으면_거부한다() {
		assertThat(PasswordPolicy.violation("a".repeat(64))).isEmpty();
		assertThat(PasswordPolicy.violation("a".repeat(65))).isPresent();
	}

	@Test
	void BCrypt_한도인_72바이트를_넘으면_거부한다() {
		assertThat(PasswordPolicy.violation("가".repeat(24))).isEmpty();
		assertThat(PasswordPolicy.violation("가".repeat(25))).isPresent();
	}

}
