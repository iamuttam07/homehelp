package com.uel.homehelp.provider;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/** Body of {@code PUT /api/provider/profile}. Both fields are optional and replaced as given. */
record ProviderProfileRequest(

		@Size(max = 1000)
		String bio,

		@Min(0) @Max(60)
		Integer yearsOfExperience) {
}
