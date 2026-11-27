package com.woorizip.infra.mail;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 발송 작업을 보낸다. 커밋 직후 한 번 바로 보내고({@link MailAfterCommitSender}), 실패한 것은 스케줄러가 간격을 두고
 * 다시 보낸다.
 *
 * <p>재시도 간격은 30초, 1분, 2분, 4분이다. 만료 시각(코드 메일은 코드 만료)이 지나면 더 보내지 않는다.
 * 여러 서버가 동시에 돌아도 {@code FOR UPDATE SKIP LOCKED}로 같은 작업을 두 번 잡지 않는다.
 */
@Service
public class MailDispatcher {

	static final List<Duration> RETRY_DELAYS = List.of(Duration.ofSeconds(30), Duration.ofMinutes(1),
			Duration.ofMinutes(2), Duration.ofMinutes(4));

	private static final int BATCH_SIZE = 20;

	private static final Logger log = LoggerFactory.getLogger(MailDispatcher.class);

	private final MailJobRepository jobs;

	private final MailGateway gateway;

	private final Clock clock;

	MailDispatcher(MailJobRepository jobs, MailGateway gateway, Clock clock) {
		this.jobs = jobs;
		this.gateway = gateway;
		this.clock = clock;
	}

	/**
	 * 그 작업을 바로 한 번 보낸다. {@link MailAfterCommitSender}가 요청 트랜잭션이 커밋된 뒤 부른다. 커밋 직후에는
	 * 끝난 트랜잭션이 아직 묶여 있을 수 있어 새 트랜잭션을 연다.
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void sendNow(long jobId) {
		this.jobs.lockPending(jobId).ifPresent(this::attempt);
	}

	/** 보낼 차례인 작업을 보낸다. 처리한 작업 수를 돌려준다. */
	@Transactional
	public int dispatchDue() {
		List<MailJob> due = this.jobs.lockDue(this.clock.instant(), BATCH_SIZE);
		due.forEach(this::attempt);
		return due.size();
	}

	private void attempt(MailJob job) {
		Instant now = this.clock.instant();
		if (job.isExpired(now)) {
			job.expire();
			return;
		}
		try {
			this.gateway.send(job.toMail());
			job.markSent(now);
		}
		catch (RuntimeException ex) {
			log.warn("메일 발송 실패 (작업 {}, {}번째 시도)", job.getId(), job.getAttempts() + 1, ex);
			job.recordFailure(now, ex.getMessage(), RETRY_DELAYS);
		}
	}

}
