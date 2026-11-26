package com.woorizip.infra.mail;

/**
 * 메일을 실제로 보내는 곳.
 *
 * <p>로컬과 테스트는 {@link FakeMailGateway}를 쓴다. 운영 발송(Amazon SES)은 발신 도메인을 정한 뒤 만든다
 * (ADR 0018, docs/plans/auth.md). 구현이 없으면 서버가 시작하지 않는다.
 */
public interface MailGateway {

	/** 보내지 못하면 예외를 던진다. 예외가 나면 {@link MailDispatcher}가 나중에 다시 보낸다. */
	void send(Mail mail);

}
