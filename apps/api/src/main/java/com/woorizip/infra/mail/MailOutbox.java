package com.woorizip.infra.mail;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 메일을 발송 작업으로 저장한다 (ADR 0018).
 *
 * <p>요청 트랜잭션 안에서 저장하므로, 요청이 롤백되면 메일도 나가지 않는다. 커밋되면 {@link MailDispatcher}가
 * 바로 보내고, 실패하면 스케줄러가 다시 보낸다.
 */
@Service
public class MailOutbox {

	private final MailJobRepository jobs;

	private final ApplicationEventPublisher events;

	private final Clock clock;

	MailOutbox(MailJobRepository jobs, ApplicationEventPublisher events, Clock clock) {
		this.jobs = jobs;
		this.events = events;
		this.clock = clock;
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public void enqueue(MailKind kind, Mail mail, Instant expiresAt) {
		MailJob job = this.jobs.save(new MailJob(kind, mail, this.clock.instant(), expiresAt));
		this.events.publishEvent(new MailJobCreated(job.getId()));
	}

	/** 같은 이메일로 {@code since} 이후에 만든 메일 수. 요청 횟수 제한에 쓴다. */
	@Transactional(readOnly = true)
	public long countSince(String recipient, Collection<MailKind> kinds, Instant since) {
		return this.jobs.countByRecipientAndKindInAndCreatedAtAfter(recipient, kinds, since);
	}

	record MailJobCreated(long jobId) {
	}

}
