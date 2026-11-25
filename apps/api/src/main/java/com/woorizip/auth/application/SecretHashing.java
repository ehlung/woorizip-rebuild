package com.woorizip.auth.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * 인증 코드와 가입 확인 값을 만들고 해시로 바꾼다. DB에는 해시만 저장한다 (ADR 0009).
 *
 * <p>6자리 코드는 경우의 수가 100만 개라 해시를 알면 금방 원문을 찾을 수 있다. 해시는 DB를 본 사람이 코드를
 * 바로 읽지 못하게 하는 정도이고, 실제 보호는 10분 만료와 5회 제한이 한다.
 */
public final class SecretHashing {

	private static final SecureRandom RANDOM = new SecureRandom();

	private SecretHashing() {
	}

	/** 000000~999999 중 하나. */
	public static String newSixDigitCode() {
		return String.format("%06d", RANDOM.nextInt(1_000_000));
	}

	/** 무작위 256비트를 URL에 쓸 수 있는 base64로. */
	public static String newToken() {
		byte[] bytes = new byte[32];
		RANDOM.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	/** SHA-256 hex. 여러 값을 이어 해시할 때는 구분자로 줄바꿈을 넣는다. */
	public static String sha256(String... parts) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			return HexFormat.of().formatHex(digest.digest(String.join("\n", parts).getBytes(StandardCharsets.UTF_8)));
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException(ex);
		}
	}

	/** 걸리는 시간으로 맞은 글자 수를 알 수 없게 비교한다. */
	public static boolean sameHash(String a, String b) {
		return MessageDigest.isEqual(a.getBytes(StandardCharsets.US_ASCII), b.getBytes(StandardCharsets.US_ASCII));
	}

}
