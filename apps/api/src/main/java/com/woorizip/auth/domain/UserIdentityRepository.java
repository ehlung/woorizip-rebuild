package com.woorizip.auth.domain;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UserIdentityRepository extends JpaRepository<UserIdentity, Long> {

	boolean existsByProviderAndSubject(IdentityProvider provider, String subject);

	Optional<UserIdentity> findByProviderAndSubject(IdentityProvider provider, String subject);

}
