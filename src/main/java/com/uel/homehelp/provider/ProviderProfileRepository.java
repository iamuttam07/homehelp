package com.uel.homehelp.provider;

import java.util.List;
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

	/**
	 * Approved providers who offer the service and whose service area covers the point.
	 * The first distance check uses the GIST index with the largest allowed radius; the
	 * second applies each provider's own radius.
	 */
	@Query(value = """
			SELECT p.user_id FROM provider_profiles p
			JOIN provider_services ps ON ps.provider_id = p.user_id
			WHERE ps.service_id = :serviceId
			  AND p.status = 'APPROVED'
			  AND ST_DWithin(p.base_location,
			                 CAST(ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326) AS geography),
			                 :maxRadiusMetres)
			  AND ST_DWithin(p.base_location,
			                 CAST(ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326) AS geography),
			                 p.service_radius_km * 1000)
			""", nativeQuery = true)
	List<UUID> findEligibleProviderIds(@Param("serviceId") UUID serviceId, @Param("latitude") double latitude,
			@Param("longitude") double longitude, @Param("maxRadiusMetres") double maxRadiusMetres);

}
