package com.uel.homehelp.provider;

import java.time.Instant;
import java.util.UUID;

record ProviderProfileResponse(UUID userId, String bio, Integer yearsOfExperience, Instant updatedAt) {

	static ProviderProfileResponse from(ProviderProfile profile) {
		return new ProviderProfileResponse(profile.getUserId(), profile.getBio(), profile.getYearsOfExperience(),
				profile.getUpdatedAt());
	}

}
