package com.woorizip.auth.domain;

import java.time.Instant;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface EmailCodeRepository extends JpaRepository<EmailCode, Long> {

	/** 그 이메일의 가장 최근 코드. 동시에 확인해도 틀린 횟수가 빠지지 않게 잠근다. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<EmailCode> findFirstByPurposeAndEmailOrderByCreatedAtDescIdDesc(EmailCodePurpose purpose, String email);

	/** 그 이메일로 {@code after} 이후에 만든 코드 수. 비밀번호 재설정의 요청 횟수 제한에 쓴다. */
	long countByPurposeAndEmailAndCreatedAtAfter(EmailCodePurpose purpose, String email, Instant after);

}
