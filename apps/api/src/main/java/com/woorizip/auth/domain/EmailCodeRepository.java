package com.woorizip.auth.domain;

import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface EmailCodeRepository extends JpaRepository<EmailCode, Long> {

	/** 그 이메일의 가장 최근 코드. 동시에 확인해도 틀린 횟수가 빠지지 않게 잠근다. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<EmailCode> findFirstByPurposeAndEmailOrderByCreatedAtDescIdDesc(EmailCodePurpose purpose, String email);

}
