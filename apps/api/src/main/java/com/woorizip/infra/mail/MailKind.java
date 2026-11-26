package com.woorizip.infra.mail;

/** 보내는 메일의 종류. 같은 이메일로 보낸 횟수를 셀 때도 쓴다. */
public enum MailKind {

	/** 가입 인증 코드 */
	SIGNUP_CODE,

	/** 이미 가입된 이메일로 가입 코드를 요청했을 때의 안내 */
	SIGNUP_ALREADY_REGISTERED

}
