package com.woorizip.auth.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 이메일 코드를 확인한 뒤 가입을 끝낼 때까지 쓰는 확인 값 (03-architecture 8장).
 *
 * <p>원문은 앱에만 주고 해시만 저장한다. 30분 뒤 만료되고 한 번만 쓸 수 있다.
 */
@Entity
@Table(name = "signup_tickets")
public class SignupTicket {

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

	protected SignupTicket() {
	}

	public SignupTicket(String email, String tokenHash, Instant now, Instant expiresAt) {
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
