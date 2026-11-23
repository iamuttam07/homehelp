package com.uel.homehelp.provider;

import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.uel.homehelp.auth.CurrentUserId;
import com.uel.homehelp.provider.AvailabilityDtos.AvailabilityRequest;
import com.uel.homehelp.provider.AvailabilityDtos.AvailabilityResponse;

/** The logged-in provider's weekly working hours. {@code /api/provider/**} is PROVIDER-only. */
@RestController
@RequestMapping("/api/provider/availability")
@Tag(name = "Provider onboarding")
class ProviderAvailabilityController {

	private final ProviderAvailabilityService availability;

	ProviderAvailabilityController(ProviderAvailabilityService availability) {
		this.availability = availability;
	}

	@GetMapping
	@Operation(summary = "Get my weekly availability", description = "Sorted by day and start time.")
	AvailabilityResponse get(@CurrentUserId UUID userId) {
		return this.availability.get(userId);
	}

	@PutMapping
	@Operation(summary = "Replace my weekly availability",
			description = "Sends the whole week. Times are HH:mm on quarter hours; windows on the same day must not overlap; up to 4 per day.")
	AvailabilityResponse replace(@CurrentUserId UUID userId, @Valid @RequestBody AvailabilityRequest request) {
		return this.availability.replace(userId, request);
	}

}
