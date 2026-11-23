package com.uel.homehelp.provider;

import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;

/** Fills in a provider's profile directly in the database, for tests. */
final class OnboardingFixtures {

	private OnboardingFixtures() {
	}

	static void addSkill(JdbcTemplate jdbc, UUID providerId, String serviceSlug) {
		jdbc.update("INSERT INTO provider_services (provider_id, service_id) SELECT ?, id FROM services WHERE slug = ?",
				providerId, serviceSlug);
	}

	static void setServiceArea(JdbcTemplate jdbc, UUID providerId) {
		jdbc.update("""
				UPDATE provider_profiles SET base_latitude = 51.5202, base_longitude = -0.0716, service_radius_km = 5
				WHERE user_id = ?""", providerId);
	}

	static void addAvailability(JdbcTemplate jdbc, UUID providerId) {
		jdbc.update("""
				INSERT INTO provider_availability (provider_id, day_of_week, start_time, end_time)
				VALUES (?, 1, '09:00', '17:00')""", providerId);
	}

	/** Skills, service area, and availability: everything needed to submit. */
	static void completeProfile(JdbcTemplate jdbc, UUID providerId) {
		addSkill(jdbc, providerId, "leak-repair");
		setServiceArea(jdbc, providerId);
		addAvailability(jdbc, providerId);
	}

}
