package com.woorizip.infra.mail;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.UUID;

import com.woorizip.MutableClock;
import com.woorizip.TestClockConfiguration;
import com.woorizip.TestcontainersConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

/** 메일 발송 작업과 재시도 (ADR 0018). A-88을 확인한다. */
@SpringBootTest
@ActiveProfiles("test")
@Import({ TestcontainersConfiguration.class, TestClockConfiguration.class })
class MailDispatcherTest {

	@Autowired
	MailOutbox outbox;

	@Autowired
	MailDispatcher dispatcher;

	@Autowired
	MailJobRepository jobs;

	@Autowired
	FakeMailGateway gateway;

	@Autowired
	MutableClock clock;

	@Autowired
	TransactionTemplate tx;

	@AfterEach
	void stopFailing() {
		this.gateway.failNext(0);
	}

	@Test
	void 커밋되면_바로_보내고_본문을_지운다() {
		String to = enqueue(Duration.ofMinutes(10));

		MailJob job = onlyJobTo(to);
		assertThat(job.getStatus()).isEqualTo(MailJobStatus.SENT);
		assertThat(job.getBody()).isNull();
		assertThat(this.gateway.sentTo(to)).hasSize(1);
	}

	@Test
	void 롤백되면_메일이_나가지_않는다() {
		String to = newEmail();
		this.tx.executeWithoutResult(status -> {
			this.outbox.enqueue(MailKind.SIGNUP_CODE, new Mail(to, "제목", "본문"),
					this.clock.instant().plus(Duration.ofMinutes(10)));
			status.setRollbackOnly();
		});

		assertThat(this.jobs.findByRecipientOrderByIdAsc(to)).isEmpty();
		assertThat(this.gateway.sentTo(to)).isEmpty();
	}

	@Test
	void A88_일시적으로_실패해도_간격을_두고_다시_보내_결국_보낸다() {
		this.gateway.failNext(2);
		String to = enqueue(Duration.ofMinutes(10));
		assertThat(onlyJobTo(to).getStatus()).isEqualTo(MailJobStatus.PENDING);

		// 첫 재시도는 30초 뒤다. 그 전에는 꺼내지 않는다.
		this.dispatcher.dispatchDue();
		assertThat(onlyJobTo(to).getAttempts()).isEqualTo(1);

		this.clock.advance(Duration.ofSeconds(30));
		this.dispatcher.dispatchDue();
		assertThat(onlyJobTo(to).getStatus()).isEqualTo(MailJobStatus.PENDING);
		assertThat(onlyJobTo(to).getAttempts()).isEqualTo(2);

		this.clock.advance(Duration.ofMinutes(1));
		this.dispatcher.dispatchDue();
		MailJob job = onlyJobTo(to);
		assertThat(job.getStatus()).isEqualTo(MailJobStatus.SENT);
		assertThat(job.getAttempts()).isEqualTo(3);
		assertThat(job.getBody()).isNull();
		assertThat(this.gateway.sentTo(to)).hasSize(1);
	}

	@Test
	void 만료_시각이_지나면_더_보내지_않고_본문을_지운다() {
		this.gateway.failNext(100);
		String to = enqueue(Duration.ofMinutes(1));

		this.clock.advance(Duration.ofMinutes(2));
		this.dispatcher.dispatchDue();

		MailJob job = onlyJobTo(to);
		assertThat(job.getStatus()).isEqualTo(MailJobStatus.FAILED);
		assertThat(job.getBody()).isNull();
		assertThat(this.gateway.sentTo(to)).isEmpty();
	}

	@Test
	void 다섯_번_실패하면_더_보내지_않는다() {
		this.gateway.failNext(100);
		String to = enqueue(Duration.ofHours(1));
		for (Duration delay : MailDispatcher.RETRY_DELAYS) {
			this.clock.advance(delay);
			this.dispatcher.dispatchDue();
		}

		MailJob job = onlyJobTo(to);
		assertThat(job.getStatus()).isEqualTo(MailJobStatus.FAILED);
		assertThat(job.getAttempts()).isEqualTo(5);
		assertThat(job.getBody()).isNull();
	}

	private String enqueue(Duration ttl) {
		String to = newEmail();
		this.tx.executeWithoutResult(status -> this.outbox.enqueue(MailKind.SIGNUP_CODE,
				new Mail(to, "[우리.zip] 가입 인증 코드", "인증 코드: 123456"), this.clock.instant().plus(ttl)));
		return to;
	}

	private MailJob onlyJobTo(String to) {
		assertThat(this.jobs.findByRecipientOrderByIdAsc(to)).hasSize(1);
		return this.jobs.findByRecipientOrderByIdAsc(to).getFirst();
	}

	private static String newEmail() {
		return "mail-" + UUID.randomUUID() + "@example.com";
	}

}
