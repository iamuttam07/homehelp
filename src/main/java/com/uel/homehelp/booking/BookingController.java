package com.uel.homehelp.booking;

import java.net.URI;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.uel.homehelp.auth.CurrentUserId;
import com.uel.homehelp.booking.BookingDtos.BookingResponse;
import com.uel.homehelp.booking.BookingDtos.CancelRequest;
import com.uel.homehelp.booking.BookingDtos.CreateBookingRequest;
import com.uel.homehelp.booking.BookingDtos.RescheduleRequest;
import com.uel.homehelp.common.web.PageResponse;

/** Customers' bookings. Every endpoint takes an optional {@code timeZone} for the returned times. */
@RestController
@RequestMapping("/api/bookings")
@Tag(name = "Booking")
class BookingController {

	private final BookingService bookings;

	private final BookingViews views;

	BookingController(BookingService bookings, BookingViews views) {
		this.bookings = bookings;
		this.views = views;
	}

	@PostMapping
	@Operation(summary = "Book my held slot",
			description = "Turns my active slot hold into a CONFIRMED booking. 409 if the slot was lost meanwhile.")
	ResponseEntity<BookingResponse> create(@CurrentUserId UUID userId, @Valid @RequestBody CreateBookingRequest request,
			@RequestParam(name = "timeZone", defaultValue = SlotService.DEFAULT_TIME_ZONE) @Size(max = 50) String timeZone) {
		Booking booking = this.bookings.create(userId, request);
		return ResponseEntity.created(URI.create("/api/bookings/" + booking.getId()))
			.body(this.views.detail(booking, SlotHoldService.zone(timeZone)));
	}

	@GetMapping
	@Operation(summary = "List my bookings", description = "As a customer, soonest first. Optionally filtered by status.")
	PageResponse<BookingResponse> mine(@CurrentUserId UUID userId,
			@RequestParam(name = "status", required = false) BookingStatus status,
			@RequestParam(name = "page", defaultValue = "0") @Min(0) int page,
			@RequestParam(name = "size", defaultValue = "20") @Min(1) @Max(50) int size,
			@RequestParam(name = "timeZone", defaultValue = SlotService.DEFAULT_TIME_ZONE) @Size(max = 50) String timeZone) {
		return this.views.page(this.bookings.listForCustomer(userId, status, page, size), SlotHoldService.zone(timeZone));
	}

	@GetMapping("/{id}")
	@Operation(summary = "Get a booking with its history",
			description = "For its customer, its provider, or an admin. Anyone else gets 404.")
	BookingResponse get(@CurrentUserId UUID userId, Authentication authentication, @PathVariable UUID id,
			@RequestParam(name = "timeZone", defaultValue = SlotService.DEFAULT_TIME_ZONE) @Size(max = 50) String timeZone) {
		boolean admin = authentication.getAuthorities().stream().anyMatch((a) -> "ROLE_ADMIN".equals(a.getAuthority()));
		return this.views.detail(this.bookings.get(userId, admin, id), SlotHoldService.zone(timeZone));
	}

	@PostMapping("/{id}/cancel")
	@Operation(summary = "Cancel my booking",
			description = "Before it starts. Within 24 hours of the start it's marked as a late cancellation.")
	BookingResponse cancel(@CurrentUserId UUID userId, @PathVariable UUID id,
			@Valid @RequestBody(required = false) CancelRequest request,
			@RequestParam(name = "timeZone", defaultValue = SlotService.DEFAULT_TIME_ZONE) @Size(max = 50) String timeZone) {
		Booking booking = this.bookings.cancelByCustomer(userId, id, (request != null) ? request.reason() : null);
		return this.views.detail(booking, SlotHoldService.zone(timeZone));
	}

	@PostMapping("/{id}/reschedule")
	@Operation(summary = "Reschedule my booking",
			description = "To another start time from GET /api/slots. At least 24 hours before the current start; at most twice.")
	BookingResponse reschedule(@CurrentUserId UUID userId, @PathVariable UUID id,
			@Valid @RequestBody RescheduleRequest request,
			@RequestParam(name = "timeZone", defaultValue = SlotService.DEFAULT_TIME_ZONE) @Size(max = 50) String timeZone) {
		Booking booking = this.bookings.reschedule(userId, id, request.start().toInstant());
		return this.views.detail(booking, SlotHoldService.zone(timeZone));
	}

}
