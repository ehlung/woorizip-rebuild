package com.woorizip.auth.domain;

import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface SignupTicketRepository extends JpaRepository<SignupTicket, Long> {

	/** 같은 확인 값으로 동시에 가입해도 한 번만 쓰이도록 잠근다. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<SignupTicket> findByTokenHash(String tokenHash);

}
