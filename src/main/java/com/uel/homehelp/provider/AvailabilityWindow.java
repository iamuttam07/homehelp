package com.uel.homehelp.provider;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A weekly working window, e.g. Monday 09:00-17:00, in the provider's time zone.
 * The schema is defined by Flyway migration V10.
 */
@Entity
@Table(name = "provider_availability")
class AvailabilityWindow {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "provider_id", nullable = false, updatable = false)
	private UUID providerId;

	@Convert(converter = IsoDayOfWeekConverter.class)
	@Column(name = "day_of_week", nullable = false)
	private DayOfWeek dayOfWeek;

	@Column(name = "start_time", nullable = false)
	private LocalTime startTime;

	@Column(name = "end_time", nullable = false)
	private LocalTime endTime;

	protected AvailabilityWindow() {
		// for JPA
	}

	AvailabilityWindow(UUID providerId, DayOfWeek dayOfWeek, LocalTime startTime, LocalTime endTime) {
		this.providerId = providerId;
		this.dayOfWeek = dayOfWeek;
		this.startTime = startTime;
		this.endTime = endTime;
	}

	UUID getProviderId() {
		return this.providerId;
	}

	DayOfWeek getDayOfWeek() {
		return this.dayOfWeek;
	}

	LocalTime getStartTime() {
		return this.startTime;
	}

	LocalTime getEndTime() {
		return this.endTime;
	}

}
