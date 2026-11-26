package com.woorizip.auth.domain;

import java.time.Instant;

import com.woorizip.user.domain.User;
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
import org.hibernate.annotations.CreationTimestamp;

/** 계정의 로그인 수단 하나. 한 계정이 여러 수단을 가질 수 있다. */
@Entity
@Table(name = "user_identities")
public class UserIdentity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private IdentityProvider provider;

	@Column(nullable = false, length = 320)
	private String subject;

	@Column(name = "password_hash", length = 100)
	private String passwordHash;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected UserIdentity() {
	}

	private UserIdentity(User user, IdentityProvider provider, String subject, String passwordHash) {
		this.user = user;
		this.provider = provider;
		this.subject = subject;
		this.passwordHash = passwordHash;
	}

	/** 이메일 로그인 수단. 이메일은 소문자로 바꿔 저장해 대소문자가 다른 같은 주소를 한 계정으로 본다. */
	public static UserIdentity email(User user, String email, String passwordHash) {
		return new UserIdentity(user, IdentityProvider.EMAIL, Emails.normalize(email), passwordHash);
	}

	public Long getId() {
		return id;
	}

	public User getUser() {
		return user;
	}

	public IdentityProvider getProvider() {
		return provider;
	}

	public String getSubject() {
		return subject;
	}

	public String getPasswordHash() {
		return passwordHash;
	}

}
