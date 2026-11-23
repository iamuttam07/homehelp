package com.uel.homehelp.provider;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * A provider's onboarding progress.
 *
 * @param status current status
 * @param checklist which parts of the profile are done
 * @param canSubmit whether {@code POST /api/provider/onboarding/submit} would succeed now
 * @param rejectionReason why an admin rejected the profile; only when {@code REJECTED}
 */
record OnboardingResponse(OnboardingStatus status, Checklist checklist, boolean canSubmit, Instant submittedAt,
		Instant reviewedAt, String rejectionReason) {

	/** The three things a provider must set before submitting. */
	record Checklist(boolean skills, boolean serviceArea, boolean availability) {

		boolean complete() {
			return this.skills && this.serviceArea && this.availability;
		}

		/** Names of the missing parts, e.g. {@code ["skills", "availability"]}. */
		List<String> missing() {
			List<String> missing = new ArrayList<>();
			if (!this.skills) {
				missing.add("skills");
			}
			if (!this.serviceArea) {
				missing.add("serviceArea");
			}
			if (!this.availability) {
				missing.add("availability");
			}
			return missing;
		}

	}

}
