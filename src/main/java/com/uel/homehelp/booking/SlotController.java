package com.uel.homehelp.booking;

import java.time.LocalDate;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.uel.homehelp.auth.CurrentUserId;
import com.uel.homehelp.booking.SlotDtos.SlotsResponse;

@RestController
@RequestMapping("/api/slots")
@Tag(name = "Booking", description = "Find times and book services")
class SlotController {

	private final SlotService slots;

	SlotController(SlotService slots) {
		this.slots = slots;
	}

	@GetMapping
	@Operation(summary = "Find available slots",
			description = "Start times on the given date (every 30 minutes) when at least one approved provider who "
					+ "offers the service and covers the address is free for the whole job. At least 2 hours' "
					+ "notice; up to 30 days ahead. The address must be one of mine.")
	SlotsResponse find(@CurrentUserId UUID userId,
			@Parameter(description = "Service slug, e.g. leak-repair")
			@RequestParam("service") @NotBlank @Size(max = 80) String service,
			@Parameter(description = "Date in the given time zone, YYYY-MM-DD")
			@RequestParam("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
			@Parameter(description = "One of my addresses")
			@RequestParam("addressId") UUID addressId,
			@Parameter(description = "IANA time zone for the date and the returned times")
			@RequestParam(name = "timeZone", defaultValue = SlotService.DEFAULT_TIME_ZONE) @Size(max = 50) String timeZone) {
		return this.slots.find(userId, service, date, addressId, timeZone);
	}

}
