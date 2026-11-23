package com.uel.homehelp.booking;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface BookingEventRepository extends JpaRepository<BookingEvent, UUID> {

	List<BookingEvent> findByBookingIdOrderByOccurredAtAscIdAsc(UUID bookingId);

}
