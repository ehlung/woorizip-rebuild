package com.woorizip.user.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

/** 서비스 계정. 가족 리소스의 권한은 {@code Member}로 판단한다 (ADR 0008). */
@Entity
@Table(name = "users")
public class User {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 20)
	private String name;

	@Enumerated(EnumType.STRING)
	@Column(name = "screen_size", nullable = false, length = 10)
	private ScreenSize screenSize;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected User() {
	}

	public User(String name, ScreenSize screenSize) {
		this.name = name;
		this.screenSize = screenSize;
	}

	public Long getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public ScreenSize getScreenSize() {
		return screenSize;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

}
