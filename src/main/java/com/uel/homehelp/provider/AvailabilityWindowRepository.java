package com.uel.homehelp.provider;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface AvailabilityWindowRepository extends JpaRepository<AvailabilityWindow, UUID> {

	List<AvailabilityWindow> findByProviderId(UUID providerId);

	boolean existsByProviderId(UUID providerId);

	List<AvailabilityWindow> findByProviderIdIn(Collection<UUID> providerIds);

	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("delete from AvailabilityWindow w where w.providerId = :providerId")
	void deleteByProviderId(@Param("providerId") UUID providerId);

}
