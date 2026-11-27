package com.woorizip.auth.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 재설정 코드를 확인한 뒤 새 비밀번호를 정할 때까지 쓰는 확인 값 (docs/plans/auth.md PR 4).
 *
 * <p>원문은 앱에만 주고 해시만 저장한다. 10분 뒤 만료되고 한 번만 쓸 수 있다. 가입 여부를 숨기려고 가입하지 않은
 * 이메일도 같은 흐름을 타므로, 사용자가 아니라 이메일에 연결한다.
 */
@Entity
@Table(name = "password_reset_tickets")
public class PasswordResetTicket {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 320)
	private String email;

	@Column(name = "token_hash", nullable = false, length = 64, unique = true)
	private String tokenHash;

	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	@Column(name = "used_at")
	private Instant usedAt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected PasswordResetTicket() {
	}

	public PasswordResetTicket(String email, String tokenHash, Instant now, Instant expiresAt) {
		this.email = email;
		this.tokenHash = tokenHash;
		this.createdAt = now;
		this.expiresAt = expiresAt;
	}

	public boolean isUsable(Instant now) {
		return this.usedAt == null && now.isBefore(this.expiresAt);
	}

	public void markUsed(Instant now) {
		this.usedAt = now;
	}

	public String getEmail() {
		return this.email;
	}

	public Instant getExpiresAt() {
		return this.expiresAt;
	}

}
