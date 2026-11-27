package com.woorizip.auth.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

	/** 같은 토큰으로 동시에 재발급해도 한 번만 바뀌도록 잠근다. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<RefreshToken> findByTokenHash(String tokenHash);

	/** 사용자의 아직 폐기되지 않은 토큰을 모두 폐기한다(탈취 의심, 비밀번호 재설정, 회원 탈퇴). */
	@Modifying
	@Query("UPDATE RefreshToken t SET t.revokedAt = :now WHERE t.user.id = :userId AND t.revokedAt IS NULL")
	int revokeAllOfUser(@Param("userId") long userId, @Param("now") Instant now);

	/** 한 기기(세션)의 토큰을 모두 폐기한다(로그아웃). */
	@Modifying
	@Query("UPDATE RefreshToken t SET t.revokedAt = :now WHERE t.sessionId = :sessionId AND t.revokedAt IS NULL")
	int revokeSession(@Param("sessionId") UUID sessionId, @Param("now") Instant now);

	List<RefreshToken> findByUserIdOrderByIdAsc(Long userId);

}
