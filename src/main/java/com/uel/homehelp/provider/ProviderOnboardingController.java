package com.uel.homehelp.provider;

import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.uel.homehelp.auth.CurrentUserId;

/** The logged-in provider's onboarding status. {@code /api/provider/**} is PROVIDER-only. */
@RestController
@RequestMapping("/api/provider/onboarding")
@Tag(name = "Provider onboarding")
class ProviderOnboardingController {

	private final ProviderOnboardingService onboarding;

	ProviderOnboardingController(ProviderOnboardingService onboarding) {
		this.onboarding = onboarding;
	}

	@GetMapping
	@Operation(summary = "Get my onboarding status",
			description = "Status, a checklist of what's done, and whether I can submit. Shows the reason if rejected.")
	OnboardingResponse get(@CurrentUserId UUID userId) {
		return this.onboarding.get(userId);
	}

	@PostMapping("/submit")
	@Operation(summary = "Submit my profile for review",
			description = "Needs skills, a service area, and availability. Allowed while ONBOARDING or REJECTED.")
	OnboardingResponse submit(@CurrentUserId UUID userId) {
		return this.onboarding.submit(userId);
	}

}
