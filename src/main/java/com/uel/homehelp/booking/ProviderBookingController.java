package com.uel.homehelp.booking;

import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.uel.homehelp.auth.CurrentUserId;
import com.uel.homehelp.booking.BookingDtos.BookingResponse;
import com.uel.homehelp.booking.BookingDtos.ReasonRequest;
import com.uel.homehelp.common.web.PageResponse;

/** A provider's jobs. {@code /api/provider/**} is PROVIDER-only. */
@RestController
@RequestMapping("/api/provider/bookings")
@Tag(name = "Provider jobs", description = "Bookings assigned to me (providers only)")
class ProviderBookingController {

	private final BookingService bookings;

	private final BookingViews views;

	ProviderBookingController(BookingService bookings, BookingViews views) {
		this.bookings = bookings;
		this.views = views;
	}

	@GetMapping
	@Operation(summary = "List my jobs", description = "Soonest first. Includes the customer's name and phone.")
	PageResponse<BookingResponse> jobs(@CurrentUserId UUID userId,
			@RequestParam(name = "status", required = false) BookingStatus status,
			@RequestParam(name = "page", defaultValue = "0") @Min(0) int page,
			@RequestParam(name = "size", defaultValue = "20") @Min(1) @Max(50) int size,
			@RequestParam(name = "timeZone", defaultValue = SlotService.DEFAULT_TIME_ZONE) @Size(max = 50) String timeZone) {
		return this.views.page(this.bookings.listForProvider(userId, status, page, size), SlotHoldService.zone(timeZone));
	}

	@PostMapping("/{id}/start")
	@Operation(summary = "Start a job", description = "CONFIRMED → IN_PROGRESS, from 30 minutes before the booked time.")
	BookingResponse start(@CurrentUserId UUID userId, @PathVariable UUID id,
			@RequestParam(name = "timeZone", defaultValue = SlotService.DEFAULT_TIME_ZONE) @Size(max = 50) String timeZone) {
		return this.views.detail(this.bookings.start(userId, id), SlotHoldService.zone(timeZone));
	}

	@PostMapping("/{id}/complete")
	@Operation(summary = "Complete a job", description = "IN_PROGRESS → COMPLETED.")
	BookingResponse complete(@CurrentUserId UUID userId, @PathVariable UUID id,
			@RequestParam(name = "timeZone", defaultValue = SlotService.DEFAULT_TIME_ZONE) @Size(max = 50) String timeZone) {
		return this.views.detail(this.bookings.complete(userId, id), SlotHoldService.zone(timeZone));
	}

	@PostMapping("/{id}/cancel")
	@Operation(summary = "Cancel a job", description = "Only CONFIRMED jobs before they start. A reason is required.")
	BookingResponse cancel(@CurrentUserId UUID userId, @PathVariable UUID id, @Valid @RequestBody ReasonRequest request,
			@RequestParam(name = "timeZone", defaultValue = SlotService.DEFAULT_TIME_ZONE) @Size(max = 50) String timeZone) {
		return this.views.detail(this.bookings.cancelByProvider(userId, id, request.reason()),
				SlotHoldService.zone(timeZone));
	}

}
