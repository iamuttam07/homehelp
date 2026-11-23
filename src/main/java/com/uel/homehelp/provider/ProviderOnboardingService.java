package com.uel.homehelp.provider;

import java.time.Clock;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.uel.homehelp.common.web.ResourceNotFoundException;
import com.uel.homehelp.provider.OnboardingResponse.Checklist;

/** The provider's side of onboarding: see progress and submit for review. */
@Service
class ProviderOnboardingService {

	private final ProviderProfileRepository profiles;

	private final AvailabilityWindowRepository windows;

	private final Clock clock;

	ProviderOnboardingService(ProviderProfileRepository profiles, AvailabilityWindowRepository windows, Clock clock) {
		this.profiles = profiles;
		this.windows = windows;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	OnboardingResponse get(UUID providerId) {
		ProviderProfile profile = this.profiles.findById(providerId)
			.orElseThrow(() -> new ResourceNotFoundException("Provider profile"));
		return response(profile);
	}

	/**
	 * Submits the profile for admin review.
	 * @throws OnboardingConflictException if already submitted or approved
	 * @throws ProfileIncompleteException if skills, service area, or availability is missing
	 */
	@Transactional
	OnboardingResponse submit(UUID providerId) {
		ProviderProfile profile = this.profiles.findByIdForUpdate(providerId)
			.orElseThrow(() -> new ResourceNotFoundException("Provider profile"));
		if (!profile.canSubmitFromCurrentStatus()) {
			throw new OnboardingConflictException("submit", profile.getStatus());
		}
		Checklist checklist = checklist(profile);
		if (!checklist.complete()) {
			throw new ProfileIncompleteException(checklist.missing());
		}
		profile.submit(this.clock.instant());
		return response(this.profiles.saveAndFlush(profile));
	}

	Checklist checklist(ProviderProfile profile) {
		boolean skills = profile.getServices()
			.stream()
			.anyMatch((s) -> s.isActive() && s.getCategory().isActive());
		return new Checklist(skills, profile.hasServiceArea(), this.windows.existsByProviderId(profile.getUserId()));
	}

	private OnboardingResponse response(ProviderProfile profile) {
		Checklist checklist = checklist(profile);
		boolean canSubmit = profile.canSubmitFromCurrentStatus() && checklist.complete();
		return new OnboardingResponse(profile.getStatus(), checklist, canSubmit, profile.getSubmittedAt(),
				profile.getReviewedAt(), profile.getRejectionReason());
	}

}
