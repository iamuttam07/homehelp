package com.uel.homehelp.provider;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Provider-specific details, one row per PROVIDER user (same id as the user).
 * The schema is defined by Flyway migration V4.
 */
@Entity
@Table(name = "provider_profiles")
class ProviderProfile {

	@Id
	@Column(name = "user_id")
	private UUID userId;

	@Column(length = 1000)
	private String bio;

	@Column(name = "years_of_experience")
	private Integer yearsOfExperience;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@UpdateTimestamp
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	/** Wrapper type so Spring Data can tell new profiles (null) from existing ones. */
	@Version
	private Long version;

	protected ProviderProfile() {
		// for JPA
	}

	ProviderProfile(UUID userId) {
		this.userId = userId;
	}

	void update(String bio, Integer yearsOfExperience) {
		this.bio = bio;
		this.yearsOfExperience = yearsOfExperience;
	}

	UUID getUserId() {
		return this.userId;
	}

	String getBio() {
		return this.bio;
	}

	Integer getYearsOfExperience() {
		return this.yearsOfExperience;
	}

	Instant getUpdatedAt() {
		return this.updatedAt;
	}

}
