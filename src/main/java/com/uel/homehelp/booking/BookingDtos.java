package com.uel.homehelp.booking;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.uel.homehelp.booking.SlotDtos.ServiceSummary;
import com.uel.homehelp.user.Role;

/** Request and response shapes for the booking endpoints. */
final class BookingDtos {

	private BookingDtos() {
	}

	/** Body of {@code POST /api/bookings}: turns my active hold into a booking. */
	record CreateBookingRequest(@NotNull UUID holdId,
			@Size(max = 500) @Schema(example = "Leak under the kitchen sink. Parking on the street.") String notes) {
	}

	/** Body of a customer cancellation; the reason is optional. */
	record CancelRequest(@Size(max = 500) String reason) {
	}

	/** Body of a provider or admin cancellation; the reason is required. */
	record ReasonRequest(@NotBlank @Size(max = 500) String reason) {
	}

	/** Body of {@code POST /api/bookings/{id}/reschedule}: a start time from {@code GET /api/slots}. */
	record RescheduleRequest(@NotNull @Schema(example = "2026-12-01T10:00:00Z") OffsetDateTime start) {
	}

	record Money(BigDecimal amount, String currency) {
	}

	record AddressSummary(String line1, String line2, String city, String postcode, String countryCode) {
	}

	/** A person on the booking. The provider's phone is never shown. */
	record Party(UUID id, String fullName, String phone) {
	}

	record Cancellation(Instant at, Role byRole, String reason, boolean late) {
	}

	record HistoryEntry(BookingEventType type, BookingStatus from, BookingStatus to, Role byRole, String details,
			Instant at) {
	}

	/** A booking. {@code history} is only filled in on the single-booking endpoint. */
	record BookingResponse(UUID id, BookingStatus status, ServiceSummary service, Money price, OffsetDateTime start,
			OffsetDateTime end, AddressSummary address, Party customer, Party provider, String notes,
			int rescheduleCount, Cancellation cancellation, Instant createdAt, List<HistoryEntry> history) {
	}

}
