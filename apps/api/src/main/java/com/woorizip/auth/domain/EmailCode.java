package com.woorizip.auth.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 메일로 보낸 6자리 코드 (ADR 0009, 03-architecture 8장).
 *
 * <p>원문은 저장하지 않고 해시만 둔다. 10분 뒤 만료되고, 5회 틀리면 맞게 입력해도 쓸 수 없다.
 */
@Entity
@Table(name = "email_codes")
public class EmailCode {

	public static final int MAX_FAILED_ATTEMPTS = 5;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private EmailCodePurpose purpose;

	@Column(nullable = false, length = 320)
	private String email;

	@Column(name = "code_hash", nullable = false, length = 64)
	private String codeHash;

	@Column(name = "failed_attempts", nullable = false)
	private int failedAttempts;

	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	@Column(name = "used_at")
	private Instant usedAt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected EmailCode() {
	}

	public EmailCode(EmailCodePurpose purpose, String email, String codeHash, Instant now, Instant expiresAt) {
		this.purpose = purpose;
		this.email = email;
		this.codeHash = codeHash;
		this.failedAttempts = 0;
		this.createdAt = now;
		this.expiresAt = expiresAt;
	}

	/** 아직 확인에 쓸 수 있는가. 만료, 5회 틀림, 이미 씀이면 쓸 수 없다. */
	public boolean isUsable(Instant now) {
		return this.usedAt == null && now.isBefore(this.expiresAt) && this.failedAttempts < MAX_FAILED_ATTEMPTS;
	}

	public void recordFailedAttempt() {
		this.failedAttempts++;
	}

	public void markUsed(Instant now) {
		this.usedAt = now;
	}

	public String getCodeHash() {
		return this.codeHash;
	}

	public String getEmail() {
		return this.email;
	}

	public int getFailedAttempts() {
		return this.failedAttempts;
	}

}
