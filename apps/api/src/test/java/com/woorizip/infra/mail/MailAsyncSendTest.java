package com.woorizip.infra.mail;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import com.woorizip.TestcontainersConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 커밋 직후 메일을 요청과 다른 스레드에서 보내는지 확인한다. 다른 테스트는 결과를 바로 보려고 요청 스레드에서 보낸다
 * ({@code woorizip.mail.send-async=false}).
 */
@SpringBootTest(properties = "woorizip.mail.send-async=true")
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class MailAsyncSendTest {

	@Autowired
	MailOutbox outbox;

	@Autowired
	FakeMailGateway gateway;

	@Autowired
	TransactionTemplate tx;

	@AfterEach
	void release() {
		this.gateway.release();
	}

	@Test
	void 메일_서버가_느려도_요청은_발송을_기다리지_않는다() throws Exception {
		String to = "async-" + UUID.randomUUID() + "@example.com";
		this.gateway.hold();

		this.tx.executeWithoutResult(status -> this.outbox.enqueue(MailKind.SIGNUP_CODE,
				new Mail(to, "제목", "본문"), Instant.now().plus(Duration.ofMinutes(10))));

		// 발송이 붙잡혀 있는데도 트랜잭션이 끝났다. 요청 스레드가 발송을 기다리지 않았다는 뜻이다.
		assertThat(this.gateway.sentTo(to)).isEmpty();
		this.gateway.release();
		assertThat(this.gateway.awaitSentTo(to, 1, Duration.ofSeconds(5))).hasSize(1);
	}

}
