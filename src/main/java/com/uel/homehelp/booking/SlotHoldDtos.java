package com.uel.homehelp.booking;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.uel.homehelp.booking.SlotDtos.ServiceSummary;

/** Request and response shapes for {@code /api/slot-holds}. */
final class SlotHoldDtos {

	private SlotHoldDtos() {
	}

	/**
	 * Body of {@code POST /api/slot-holds}.
	 *
	 * @param start a {@code start} value returned by {@code GET /api/slots}
	 * @param timeZone zone for the returned times (default Europe/London)
	 */
	record HoldRequest(

			@NotBlank @Size(max = 80) @Schema(example = "leak-repair")
			String service,

			@NotNull
			UUID addressId,

			@NotNull @Schema(example = "2026-11-30T09:00:00Z")
			OffsetDateTime start,

			@Size(max = 50) @Schema(example = "Europe/London")
			String timeZone) {
	}

	/**
	 * An active hold. The provider stays hidden until the booking is confirmed.
	 *
	 * @param expiresAt the hold disappears at this time unless it is booked
	 */
	record HoldResponse(UUID holdId, ServiceSummary service, UUID addressId, OffsetDateTime start, OffsetDateTime end,
			Instant expiresAt) {

		static HoldResponse from(SlotHold hold, ZoneId zone) {
			return new HoldResponse(hold.holdId(),
					new ServiceSummary(hold.serviceSlug(), hold.serviceName(), hold.durationMinutes()), hold.addressId(),
					hold.start().atZone(zone).toOffsetDateTime(), hold.end().atZone(zone).toOffsetDateTime(),
					hold.expiresAt());
		}

	}

}
