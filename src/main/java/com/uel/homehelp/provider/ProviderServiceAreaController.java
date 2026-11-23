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

/** The logged-in provider's service area. {@code /api/provider/**} is PROVIDER-only. */
@RestController
@RequestMapping("/api/provider/service-area")
@Tag(name = "Provider onboarding")
class ProviderServiceAreaController {

	private final ProviderServiceAreaService areas;

	ProviderServiceAreaController(ProviderServiceAreaService areas) {
		this.areas = areas;
	}

	@GetMapping
	@Operation(summary = "Get my service area", description = "404 until it has been set.")
	ServiceAreaResponse get(@CurrentUserId UUID userId) {
		return this.areas.get(userId);
	}

	@PutMapping
	@Operation(summary = "Set my service area",
			description = "A base point and how far I travel from it (1 to 50 km). Replaces any previous area.")
	ServiceAreaResponse set(@CurrentUserId UUID userId, @Valid @RequestBody ServiceAreaRequest request) {
		return this.areas.set(userId, request);
	}

}
