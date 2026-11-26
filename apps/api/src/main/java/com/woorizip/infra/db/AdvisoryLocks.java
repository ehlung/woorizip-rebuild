package com.woorizip.infra.db;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * PostgreSQL의 트랜잭션 단위 advisory lock.
 *
 * <p>같은 이름으로 잠그는 요청은 앞선 트랜잭션이 끝날 때까지 기다린다. 잠금은 트랜잭션이 끝나면 자동으로 풀린다
 * (https://www.postgresql.org/docs/17/explicit-locking.html). 서버가 여러 대여도 DB 하나로 순서를 맞춘다.
 */
@Component
public class AdvisoryLocks {

	@PersistenceContext
	private EntityManager entityManager;

	/** 이 트랜잭션이 끝날 때까지 {@code name}의 잠금을 잡는다. */
	@Transactional(propagation = Propagation.MANDATORY)
	public void lockForTransaction(String name) {
		this.entityManager.createNativeQuery("SELECT 1 FROM (SELECT pg_advisory_xact_lock(:key)) AS locked")
			.setParameter("key", keyOf(name))
			.getSingleResult();
	}

	/** 이름을 64비트 키로 바꾼다. 다른 이름이 같은 키가 되면 서로 기다릴 뿐 결과는 틀리지 않는다. */
	static long keyOf(String name) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(name.getBytes(StandardCharsets.UTF_8));
			return ByteBuffer.wrap(digest).getLong();
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException(ex);
		}
	}

}
