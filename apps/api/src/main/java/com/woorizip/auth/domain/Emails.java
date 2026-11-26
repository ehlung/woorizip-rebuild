package com.woorizip.auth.domain;

import java.util.Locale;

/** 이메일 주소 비교 규칙. 앞뒤 공백을 지우고 소문자로 바꿔, 대소문자만 다른 주소를 같은 주소로 본다. */
public final class Emails {

	private Emails() {
	}

	public static String normalize(String email) {
		return email.strip().toLowerCase(Locale.ROOT);
	}

}
