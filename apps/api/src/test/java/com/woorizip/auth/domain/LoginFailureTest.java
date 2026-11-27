package com.woorizip.auth.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;

class LoginFailureTest {

	@Test
	void 다섯_번째_실패부터_30초_잠그고_실패마다_두_배로_늘리되_15분을_넘지_않는다() {
		assertThat(LoginFailure.lockDuration(0)).isEqualTo(Duration.ofSeconds(30));
		assertThat(LoginFailure.lockDuration(1)).isEqualTo(Duration.ofMinutes(1));
		assertThat(LoginFailure.lockDuration(4)).isEqualTo(Duration.ofMinutes(8));
		assertThat(LoginFailure.lockDuration(5)).isEqualTo(Duration.ofMinutes(15));
		assertThat(LoginFailure.lockDuration(100)).isEqualTo(Duration.ofMinutes(15));
	}

	@Test
	void 네_번까지는_잠그지_않는다() {
		Instant now = Instant.parse("2026-11-26T00:00:00Z");
		LoginFailure failure = new LoginFailure("a@example.com");
		for (int i = 0; i < 4; i++) {
			failure.recordFailure(now);
		}
		assertThat(failure.isLocked(now)).isFalse();

		failure.recordFailure(now);
		assertThat(failure.isLocked(now)).isTrue();
		assertThat(failure.remainingLock(now)).isEqualTo(Duration.ofSeconds(30));
		assertThat(failure.isLocked(now.plus(Duration.ofSeconds(30)))).isFalse();
	}

	@Test
	void 연속_100번_실패하면_비밀번호를_재설정해야_한다() {
		Instant now = Instant.parse("2026-11-26T00:00:00Z");
		LoginFailure failure = new LoginFailure("a@example.com");
		for (int i = 0; i < 99; i++) {
			failure.recordFailure(now);
		}
		assertThat(failure.requiresPasswordReset()).isFalse();

		failure.recordFailure(now);
		assertThat(failure.requiresPasswordReset()).isTrue();
	}

}
