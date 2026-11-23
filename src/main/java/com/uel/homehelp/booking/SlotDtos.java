package com.uel.homehelp.booking;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Response shapes for {@code GET /api/slots}. */
final class SlotDtos {

	private SlotDtos() {
	}

	record ServiceSummary(String slug, String name, int durationMinutes) {
	}

	/**
	 * A time the job could start.
	 *
	 * @param start in the requested time zone, with its offset
	 * @param end start plus the service duration
	 * @param availableProviders how many providers are free for the whole job
	 */
	record Slot(OffsetDateTime start, OffsetDateTime end, int availableProviders) {
	}

	record SlotsResponse(ServiceSummary service, LocalDate date, String timeZone, UUID addressId, List<Slot> slots) {
	}

}
