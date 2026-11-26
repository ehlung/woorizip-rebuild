package com.woorizip.auth.domain;

import java.time.Duration;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 같은 이메일의 연속 로그인 실패 (ADR 0009, docs/plans/auth.md).
 *
 * <p>5번 연속 실패하면 1분 잠그고, 그 뒤 실패할 때마다 잠금 시간을 2배로 늘린다(최대 15분). 성공하면 지운다.
 */
@Entity
@Table(name = "login_failures")
public class LoginFailure {

	public static final int FREE_ATTEMPTS = 5;

	static final Duration FIRST_LOCK = Duration.ofMinutes(1);

	static final Duration MAX_LOCK = Duration.ofMinutes(15);

	@Id
	@Column(length = 320)
	private String email;

	@Column(name = "consecutive_failures", nullable = false)
	private int consecutiveFailures;

	@Column(name = "locked_until")
	private Instant lockedUntil;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected LoginFailure() {
	}

	public LoginFailure(String email) {
		this.email = email;
	}

	public boolean isLocked(Instant now) {
		return this.lockedUntil != null && now.isBefore(this.lockedUntil);
	}

	public void recordFailure(Instant now) {
		this.consecutiveFailures++;
		this.updatedAt = now;
		if (this.consecutiveFailures >= FREE_ATTEMPTS) {
			this.lockedUntil = now.plus(lockDuration(this.consecutiveFailures - FREE_ATTEMPTS));
		}
	}

	/** 5번째 실패에서 1분, 그 뒤 실패마다 2배, 최대 15분. */
	static Duration lockDuration(int failuresBeyondFree) {
		Duration duration = FIRST_LOCK;
		for (int i = 0; i < failuresBeyondFree && duration.compareTo(MAX_LOCK) < 0; i++) {
			duration = duration.multipliedBy(2);
		}
		return duration.compareTo(MAX_LOCK) > 0 ? MAX_LOCK : duration;
	}

	public Instant getLockedUntil() {
		return this.lockedUntil;
	}

	public int getConsecutiveFailures() {
		return this.consecutiveFailures;
	}

}
