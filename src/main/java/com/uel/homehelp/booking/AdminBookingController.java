package com.uel.homehelp.booking;

import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.uel.homehelp.auth.CurrentUserId;
import com.uel.homehelp.booking.BookingDtos.BookingResponse;
import com.uel.homehelp.booking.BookingDtos.ReasonRequest;

/** Admin booking actions. {@code /api/admin/**} is ADMIN-only. Admins read bookings via GET /api/bookings/{id}. */
@RestController
@RequestMapping("/api/admin/bookings")
@Tag(name = "Admin: bookings", description = "Intervene in bookings (admins only)")
class AdminBookingController {

	private final BookingService bookings;

	private final BookingViews views;

	AdminBookingController(BookingService bookings, BookingViews views) {
		this.bookings = bookings;
		this.views = views;
	}

	@PostMapping("/{id}/cancel")
	@Operation(summary = "Cancel any booking", description = "CONFIRMED or IN_PROGRESS. A reason is required.")
	BookingResponse cancel(@CurrentUserId UUID adminId, @PathVariable UUID id, @Valid @RequestBody ReasonRequest request,
			@RequestParam(name = "timeZone", defaultValue = SlotService.DEFAULT_TIME_ZONE) @Size(max = 50) String timeZone) {
		return this.views.detail(this.bookings.cancelByAdmin(adminId, id, request.reason()),
				SlotHoldService.zone(timeZone));
	}

}
