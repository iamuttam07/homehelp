package com.uel.homehelp.provider;

import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.uel.homehelp.auth.CurrentUserId;

/** The logged-in provider's own profile. {@code /api/provider/**} is PROVIDER-only (see SecurityConfig). */
@RestController
@RequestMapping("/api/provider/profile")
class ProviderProfileController {

	private final ProviderProfileService service;

	ProviderProfileController(ProviderProfileService service) {
		this.service = service;
	}

	@GetMapping
	ProviderProfileResponse get(@CurrentUserId UUID userId) {
		return ProviderProfileResponse.from(this.service.get(userId));
	}

	@PutMapping
	ProviderProfileResponse update(@CurrentUserId UUID userId, @Valid @RequestBody ProviderProfileRequest request) {
		return ProviderProfileResponse.from(this.service.update(userId, request));
	}

}
