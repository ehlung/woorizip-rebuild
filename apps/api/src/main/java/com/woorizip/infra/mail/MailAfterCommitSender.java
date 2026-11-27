package com.woorizip.infra.mail;

import java.util.concurrent.Executor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 요청 트랜잭션이 커밋되면 그 메일을 요청과 다른 스레드에서 바로 보낸다.
 *
 * <p>요청 스레드에서 보내면 메일을 보내는 요청만 메일 서버 호출 시간만큼 늦어진다. 비밀번호 재설정은 가입한 이메일에만
 * 메일을 보내므로, 응답 시간으로 가입 여부가 드러난다(OWASP Forgot Password Cheat Sheet, docs/plans/auth.md PR 4).
 *
 * <p>실행기는 빈으로 등록하지 않는다. 등록하면 Spring Boot가 자동으로 만드는 기본 실행기가 물러난다. 서버가 멈출 때
 * 보내지 못한 메일은 대기 상태로 남고 스케줄러가 다시 보낸다. 테스트는 {@code woorizip.mail.send-async=false}로
 * 요청 스레드에서 보내 결과를 바로 확인한다.
 */
@Component
class MailAfterCommitSender {

	private static final Logger log = LoggerFactory.getLogger(MailAfterCommitSender.class);

	private final MailDispatcher dispatcher;

	private final Executor executor;

	MailAfterCommitSender(MailDispatcher dispatcher, @Value("${woorizip.mail.send-async:true}") boolean async) {
		this.dispatcher = dispatcher;
		this.executor = async ? virtualThreads() : new SyncTaskExecutor();
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	void sendAfterCommit(MailOutbox.MailJobCreated event) {
		this.executor.execute(() -> {
			try {
				this.dispatcher.sendNow(event.jobId());
			}
			catch (RuntimeException ex) {
				// 작업은 대기 상태로 남아 스케줄러가 다시 보낸다.
				log.warn("커밋 직후 메일 발송 실패 (작업 {})", event.jobId(), ex);
			}
		});
	}

	private static Executor virtualThreads() {
		SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor("mail-send-");
		executor.setVirtualThreads(true);
		return executor;
	}

}
