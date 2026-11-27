package com.woorizip.infra.mail;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
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

	private volatile CountDownLatch hold = new CountDownLatch(0);

	@Override
	public void send(Mail mail) {
		awaitRelease();
		synchronized (this) {
			if (this.failuresToSimulate.getAndUpdate(n -> Math.max(0, n - 1)) > 0) {
				throw new IllegalStateException("가짜 발송 실패(테스트용)");
			}
			this.sent.add(mail);
			notifyAll();
		}
		log.info("가짜 메일 발송: to={}, subject={}\n{}", mail.to(), mail.subject(), mail.body());
	}

	/** {@link #release()}를 부를 때까지 발송을 붙잡아 둔다. 메일 서버가 느린 경우를 흉내 낸다(테스트용). */
	public void hold() {
		this.hold = new CountDownLatch(1);
	}

	public void release() {
		this.hold.countDown();
	}

	/** 그 주소로 {@code count}통이 보내질 때까지 기다린다(테스트용). */
	public synchronized List<Mail> awaitSentTo(String to, int count, Duration timeout) throws InterruptedException {
		long deadline = System.nanoTime() + timeout.toNanos();
		while (sentTo(to).size() < count) {
			long remaining = deadline - System.nanoTime();
			if (remaining <= 0) {
				throw new IllegalStateException(to + "로 " + count + "통이 보내지지 않았다");
			}
			TimeUnit.NANOSECONDS.timedWait(this, remaining);
		}
		return sentTo(to);
	}

	private void awaitRelease() {
		try {
			if (!this.hold.await(10, TimeUnit.SECONDS)) {
				throw new IllegalStateException("가짜 발송이 풀리지 않았다(테스트용)");
			}
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException(ex);
		}
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
