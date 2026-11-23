package com.woorizip.family.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

/** 가족 공간. 구성원은 초대 코드로 들어온다. */
@Entity
@Table(name = "families")
public class Family {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 20)
	private String name;

	@Column(name = "invite_code", nullable = false, unique = true, length = 8)
	private String inviteCode;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected Family() {
	}

	public Family(String name, String inviteCode) {
		this.name = name;
		this.inviteCode = inviteCode;
	}

	public Long getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public String getInviteCode() {
		return inviteCode;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

}
