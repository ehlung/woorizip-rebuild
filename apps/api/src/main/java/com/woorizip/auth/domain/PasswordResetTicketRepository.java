package com.woorizip.auth.domain;

import java.time.Instant;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PasswordResetTicketRepository extends JpaRepository<PasswordResetTicket, Long> {

	/** 같은 확인 값으로 동시에 재설정해도 한 번만 쓰이도록 잠근다. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<PasswordResetTicket> findByTokenHash(String tokenHash);

	/** 그 이메일의 쓰지 않은 확인 값을 모두 쓴 것으로 바꾼다. 재설정이 끝나면 남은 확인 값을 쓸 수 없게 한다. */
	@Modifying(flushAutomatically = true)
	@Query("UPDATE PasswordResetTicket t SET t.usedAt = :now WHERE t.email = :email AND t.usedAt IS NULL")
	int invalidateUnused(@Param("email") String email, @Param("now") Instant now);

}
