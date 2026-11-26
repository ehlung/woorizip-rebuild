package com.woorizip.user.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** 사용자가 동의한 약관 하나. 약관 버전별로 남긴다. */
@Entity
@Table(name = "user_consents")
public class UserConsent {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private ConsentKind kind;

	@Column(nullable = false, length = 20)
	private String version;

	@Column(name = "agreed_at", nullable = false)
	private Instant agreedAt;

	protected UserConsent() {
	}

	public UserConsent(User user, ConsentKind kind, String version, Instant agreedAt) {
		this.user = user;
		this.kind = kind;
		this.version = version;
		this.agreedAt = agreedAt;
	}

	public ConsentKind getKind() {
		return this.kind;
	}

	public String getVersion() {
		return this.version;
	}

}
