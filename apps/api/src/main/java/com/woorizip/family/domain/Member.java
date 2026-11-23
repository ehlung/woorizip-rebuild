package com.woorizip.family.domain;

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

/**
 * 사용자의 가족 소속. 가족 안의 활동(답변, 반응)은 사용자가 아니라 구성원을 가리킨다 (ADR 0008).
 * 이름과 호칭은 가족마다 다를 수 있도록 구성원에 둔다.
 */
@Entity
@Table(name = "members")
public class Member {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "family_id", nullable = false)
	private Family family;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private MemberRole role;

	@Column(nullable = false, length = 20)
	private String name;

	@Column(nullable = false, length = 20)
	private String relation;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private MemberStatus status;

	@Column(name = "joined_at", nullable = false, updatable = false)
	private Instant joinedAt;

	@Column(name = "left_at")
	private Instant leftAt;

	protected Member() {
	}

	private Member(Family family, User user, MemberRole role, String relation, Instant joinedAt) {
		this.family = family;
		this.user = user;
		this.role = role;
		this.name = user.getName();
		this.relation = relation;
		this.status = MemberStatus.ACTIVE;
		this.joinedAt = joinedAt;
	}

	/** 가족을 만든 사람. 가입 때의 이름을 구성원 이름의 처음 값으로 쓴다. */
	public static Member owner(Family family, User user, String relation, Instant joinedAt) {
		return new Member(family, user, MemberRole.OWNER, relation, joinedAt);
	}

	/** 초대 코드로 들어온 사람. */
	public static Member member(Family family, User user, String relation, Instant joinedAt) {
		return new Member(family, user, MemberRole.MEMBER, relation, joinedAt);
	}

	public void leave(Instant leftAt) {
		this.status = MemberStatus.LEFT;
		this.leftAt = leftAt;
	}

	public Long getId() {
		return id;
	}

	public Family getFamily() {
		return family;
	}

	public User getUser() {
		return user;
	}

	public MemberRole getRole() {
		return role;
	}

	public String getName() {
		return name;
	}

	public String getRelation() {
		return relation;
	}

	public MemberStatus getStatus() {
		return status;
	}

	public Instant getJoinedAt() {
		return joinedAt;
	}

	public Instant getLeftAt() {
		return leftAt;
	}

}
