package com.uel.homehelp.booking;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import com.uel.homehelp.user.Role;

/** One entry in a booking's history (migration V12). */
@Entity
@Table(name = "booking_events")
class BookingEvent {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "booking_id", nullable = false, updatable = false)
	private UUID bookingId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false, length = 20)
	private BookingEventType type;

	@Enumerated(EnumType.STRING)
	@Column(name = "from_status", updatable = false, length = 20)
	private BookingStatus fromStatus;

	@Enumerated(EnumType.STRING)
	@Column(name = "to_status", nullable = false, updatable = false, length = 20)
	private BookingStatus toStatus;

	@Column(name = "actor_id", updatable = false)
	private UUID actorId;

	@Enumerated(EnumType.STRING)
	@Column(name = "actor_role", nullable = false, updatable = false, length = 20)
	private Role actorRole;

	@Column(updatable = false, length = 500)
	private String details;

	@Column(name = "occurred_at", nullable = false, updatable = false)
	private Instant occurredAt;

	protected BookingEvent() {
		// for JPA
	}

	BookingEvent(UUID bookingId, BookingEventType type, BookingStatus fromStatus, BookingStatus toStatus,
			UUID actorId, Role actorRole, String details, Instant occurredAt) {
		this.bookingId = bookingId;
		this.type = type;
		this.fromStatus = fromStatus;
		this.toStatus = toStatus;
		this.actorId = actorId;
		this.actorRole = actorRole;
		this.details = details;
		this.occurredAt = occurredAt;
	}

	BookingEventType getType() {
		return this.type;
	}

	BookingStatus getFromStatus() {
		return this.fromStatus;
	}

	BookingStatus getToStatus() {
		return this.toStatus;
	}

	Role getActorRole() {
		return this.actorRole;
	}

	String getDetails() {
		return this.details;
	}

	Instant getOccurredAt() {
		return this.occurredAt;
	}

}
