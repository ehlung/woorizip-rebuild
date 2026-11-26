package com.woorizip.infra.mail;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 실제로 보내지 않고 보낸 메일을 기록하는 가짜 발송 (ADR 0016, ADR 0018).
 *
 * <p>local과 test 프로필에서만 등록되므로 운영에서는 켤 수 없다(ADR 0005). 로컬에서는 로그로 본문(인증 코드)을
 * 보여 주어 실제 메일 없이 가입할 수 있다. 테스트는 {@link #sentTo(String)}로 코드를 읽는다.
 */
@Component
@Profile({ "local", "test" })
public class FakeMailGateway implements MailGateway {

	private static final Logger log = LoggerFactory.getLogger(FakeMailGateway.class);

	private final List<Mail> sent = new ArrayList<>();

	private final AtomicInteger failuresToSimulate = new AtomicInteger();

	@Override
	public synchronized void send(Mail mail) {
		if (this.failuresToSimulate.getAndUpdate(n -> Math.max(0, n - 1)) > 0) {
			throw new IllegalStateException("가짜 발송 실패(테스트용)");
		}
		this.sent.add(mail);
		log.info("가짜 메일 발송: to={}, subject={}\n{}", mail.to(), mail.subject(), mail.body());
	}

	/** 그 주소로 보낸 메일, 보낸 순서대로. */
	public synchronized List<Mail> sentTo(String to) {
		return this.sent.stream().filter(mail -> mail.to().equals(to)).toList();
	}

	/** 다음 {@code count}번의 발송을 실패시킨다. */
	public void failNext(int count) {
		this.failuresToSimulate.set(count);
	}

}
