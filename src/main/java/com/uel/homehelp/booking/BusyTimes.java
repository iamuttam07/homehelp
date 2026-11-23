package com.uel.homehelp.booking;

import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Component;

/**
 * When providers are already taken: other customers' slot holds (Redis) plus active
 * bookings (database), as 15-minute cells. Slot search, holding, and rescheduling all
 * use this, so they agree on what "free" means.
 */
@Component
class BusyTimes {

	private final SlotHoldStore holds;

	private final BookingRepository bookings;

	BusyTimes(SlotHoldStore holds, BookingRepository bookings) {
		this.holds = holds;
		this.bookings = bookings;
	}

	/**
	 * @param ignoreHoldId a hold to treat as free (the caller's own), or null
	 * @param ignoreBookingId a booking to treat as free (the one being rescheduled), or null
	 * @return provider id → start instants of busy cells
	 */
	Map<UUID, Set<Instant>> cells(Collection<UUID> providerIds, Instant from, Instant to, UUID ignoreHoldId,
			UUID ignoreBookingId) {
		Map<UUID, Set<Instant>> busy = this.holds.heldCells(providerIds, from, to, ignoreHoldId);
		if (providerIds.isEmpty()) {
			return busy;
		}
		for (Booking booking : this.bookings.findOverlapping(providerIds, BookingStatus.ACTIVE, from, to)) {
			if (booking.getId().equals(ignoreBookingId)) {
				continue;
			}
			Set<Instant> cells = busy.computeIfAbsent(booking.getProviderId(), (k) -> new HashSet<>());
			for (Instant cell = SlotHoldStore.cellStart(booking.getStartAt()); cell.isBefore(booking.getEndAt());
					cell = cell.plusSeconds(SlotHoldStore.CELL_SECONDS)) {
				cells.add(cell);
			}
		}
		return busy;
	}

}
