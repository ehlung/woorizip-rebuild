package com.woorizip.infra.mail;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * 실패한 메일을 주기적으로 다시 보낸다. 테스트는 이 스케줄을 끄고 {@link MailDispatcher#dispatchDue()}를
 * 직접 부른다({@code woorizip.mail.dispatcher.enabled=false}).
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(name = "woorizip.mail.dispatcher.enabled", havingValue = "true", matchIfMissing = true)
class MailDispatchSchedule {

	private final MailDispatcher dispatcher;

	MailDispatchSchedule(MailDispatcher dispatcher) {
		this.dispatcher = dispatcher;
	}

	@Scheduled(fixedDelayString = "${woorizip.mail.dispatcher.delay-ms:10000}")
	void dispatch() {
		this.dispatcher.dispatchDue();
	}

}
