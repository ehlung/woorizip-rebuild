package com.woorizip.auth.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import com.woorizip.user.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * refresh token 하나 (ADR 0009). 원문은 앱에만 주고 해시만 저장한다.
 *
 * <p>쓸 때마다 같은 세션의 새 토큰으로 바꾸고, 이전 토큰에는 바꾼 시각을 남긴다. 바꾼 토큰이 유예 시간이 지난 뒤에
 * 다시 오면 탈취로 본다.
 */
@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@Column(name = "session_id", nullable = false)
	private UUID sessionId;

	@Column(name = "token_hash", nullable = false, length = 64, unique = true)
	private String tokenHash;

	@Column(name = "session_started_at", nullable = false)
	private Instant sessionStartedAt;

	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "rotated_at")
	private Instant rotatedAt;

	@Column(name = "revoked_at")
	private Instant revokedAt;

	protected RefreshToken() {
	}

	public RefreshToken(User user, UUID sessionId, String tokenHash, Instant sessionStartedAt, Instant now,
			Instant expiresAt) {
		this.user = user;
		this.sessionId = sessionId;
		this.tokenHash = tokenHash;
		this.sessionStartedAt = sessionStartedAt;
		this.createdAt = now;
		this.expiresAt = expiresAt;
	}

	public boolean isRotated() {
		return this.rotatedAt != null;
	}

	/** 바꾼 지 {@code grace}가 지나지 않았는지. 바꾸지 않은 토큰이면 false다. */
	public boolean wasRotatedWithin(Duration grace, Instant now) {
		return this.rotatedAt != null && now.isBefore(this.rotatedAt.plus(grace));
	}

	public boolean isRevoked() {
		return this.revokedAt != null;
	}

	public boolean isExpired(Instant now) {
		return !now.isBefore(this.expiresAt);
	}

	public void markRotated(Instant now) {
		this.rotatedAt = now;
	}

	public User getUser() {
		return this.user;
	}

	public UUID getSessionId() {
		return this.sessionId;
	}

	public Instant getSessionStartedAt() {
		return this.sessionStartedAt;
	}

	public String getTokenHash() {
		return this.tokenHash;
	}

	public Instant getExpiresAt() {
		return this.expiresAt;
	}

	public Instant getRevokedAt() {
		return this.revokedAt;
	}

}
