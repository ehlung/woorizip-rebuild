package com.woorizip.infra.mail;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 보낼 메일 한 건. 요청 트랜잭션에서 저장하고, 커밋 직후와 스케줄러가 보낸다.
 *
 * <p>본문에는 인증 코드가 들어 있어서, 보내거나 더 보내지 않기로 하면 바로 지운다.
 */
@Entity
@Table(name = "mail_jobs")
public class MailJob {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private MailKind kind;

	@Column(nullable = false, length = 320)
	private String recipient;

	@Column(nullable = false, length = 200)
	private String subject;

	@Column(length = 2000)
	private String body;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private MailJobStatus status;

	@Column(nullable = false)
	private int attempts;

	@Column(name = "next_attempt_at", nullable = false)
	private Instant nextAttemptAt;

	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	@Column(name = "last_error", length = 500)
	private String lastError;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "sent_at")
	private Instant sentAt;

	protected MailJob() {
	}

	MailJob(MailKind kind, Mail mail, Instant now, Instant expiresAt) {
		this.kind = kind;
		this.recipient = mail.to();
		this.subject = mail.subject();
		this.body = mail.body();
		this.status = MailJobStatus.PENDING;
		this.attempts = 0;
		this.nextAttemptAt = now;
		this.expiresAt = expiresAt;
		this.createdAt = now;
	}

	boolean isExpired(Instant now) {
		return !now.isBefore(this.expiresAt);
	}

	Mail toMail() {
		return new Mail(this.recipient, this.subject, this.body);
	}

	void markSent(Instant now) {
		this.attempts++;
		this.status = MailJobStatus.SENT;
		this.sentAt = now;
		this.body = null;
	}

	/** 실패를 기록하고, 남은 재시도가 있으면 다음 시각을 정한다. 없으면 실패로 끝낸다. */
	void recordFailure(Instant now, String error, List<Duration> retryDelays) {
		this.attempts++;
		this.lastError = error == null ? null : error.substring(0, Math.min(error.length(), 500));
		if (this.attempts > retryDelays.size()) {
			fail();
			return;
		}
		this.nextAttemptAt = now.plus(retryDelays.get(this.attempts - 1));
	}

	/** 만료돼서 더 보내지 않는다. */
	void expire() {
		fail();
	}

	private void fail() {
		this.status = MailJobStatus.FAILED;
		this.body = null;
	}

	public Long getId() {
		return this.id;
	}

	public MailKind getKind() {
		return this.kind;
	}

	public String getRecipient() {
		return this.recipient;
	}

	public String getBody() {
		return this.body;
	}

	public MailJobStatus getStatus() {
		return this.status;
	}

	public int getAttempts() {
		return this.attempts;
	}

	public Instant getNextAttemptAt() {
		return this.nextAttemptAt;
	}

}
