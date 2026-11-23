package com.uel.homehelp.provider;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import com.uel.homehelp.provider.AvailabilityDtos.AvailabilityResponse;
import com.uel.homehelp.provider.OnboardingResponse.Checklist;
import com.uel.homehelp.user.User;

/** Shapes used by the admin review API. */
final class ProviderReviewDtos {

	private ProviderReviewDtos() {
	}

	/** One row in the admin's provider list. */
	record ProviderSummary(UUID userId, String fullName, String email, OnboardingStatus status, Instant submittedAt,
			Instant reviewedAt, int skillCount) {

		static ProviderSummary from(ProviderProfile profile, User user) {
			return new ProviderSummary(profile.getUserId(), (user != null) ? user.getFullName() : null,
					(user != null) ? user.getEmail() : null, profile.getStatus(), profile.getSubmittedAt(),
					profile.getReviewedAt(), profile.getServices().size());
		}

	}

	/** Everything an admin needs to decide on a provider. */
	record ProviderDetail(UUID userId, String fullName, String email, String phone, OnboardingStatus status,
			Instant submittedAt, Instant reviewedAt, String rejectionReason, String bio, Integer yearsOfExperience,
			Checklist checklist, List<SkillResponse> skills, ServiceAreaResponse serviceArea,
			AvailabilityResponse availability) {
	}

	/**
	 * Body of {@code POST /api/admin/providers/{id}/reject}.
	 *
	 * @param reason shown to the provider, so they know what to fix
	 */
	record RejectRequest(@NotBlank @Size(max = 500) String reason) {
	}

}
