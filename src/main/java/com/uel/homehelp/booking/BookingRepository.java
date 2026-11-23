package com.uel.homehelp.booking;

import java.time.Instant;
import java.util.Collection;
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

interface BookingRepository extends JpaRepository<Booking, UUID> {

	/** Loads the booking and locks its row, so two changes can't interleave. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select b from Booking b where b.id = :id")
	Optional<Booking> findByIdForUpdate(@Param("id") UUID id);

	/** Active bookings of these providers that overlap {@code [from, to)}. */
	@Query("""
			select b from Booking b
			where b.providerId in :providerIds and b.status in :statuses
			  and b.startAt < :to and b.endAt > :from""")
	List<Booking> findOverlapping(@Param("providerIds") Collection<UUID> providerIds,
			@Param("statuses") Collection<BookingStatus> statuses, @Param("from") Instant from, @Param("to") Instant to);

	Page<Booking> findByCustomerId(UUID customerId, Pageable pageable);

	Page<Booking> findByCustomerIdAndStatus(UUID customerId, BookingStatus status, Pageable pageable);

	Page<Booking> findByProviderId(UUID providerId, Pageable pageable);

	Page<Booking> findByProviderIdAndStatus(UUID providerId, BookingStatus status, Pageable pageable);

}
