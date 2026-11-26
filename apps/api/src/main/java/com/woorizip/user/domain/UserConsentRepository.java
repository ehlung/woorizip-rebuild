package com.woorizip.user.domain;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UserConsentRepository extends JpaRepository<UserConsent, Long> {

	List<UserConsent> findByUserId(Long userId);

}
