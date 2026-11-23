package com.uel.homehelp.provider;

import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface ProviderProfileRepository extends JpaRepository<ProviderProfile, UUID> {

	/** Loads the profile and locks its row until the transaction ends. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select p from ProviderProfile p where p.userId = :userId")
	Optional<ProviderProfile> findByIdForUpdate(@Param("userId") UUID userId);

	Page<ProviderProfile> findByStatus(OnboardingStatus status, Pageable pageable);

}
