package com.uel.homehelp.booking;

import java.math.BigDecimal;
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
import jakarta.persistence.Version;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.uel.homehelp.address.AddressDetails;
import com.uel.homehelp.catalog.HomeService;
import com.uel.homehelp.user.Role;

/**
 * A booked job. Service, price, and address are copies taken at booking time.
 * Status changes go through the methods below. The schema is defined by migration V12.
 */
@Entity
@Table(name = "bookings")
class Booking {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "customer_id", nullable = false, updatable = false)
	private UUID customerId;

	@Column(name = "provider_id", nullable = false)
	private UUID providerId;

	@Column(name = "service_id", nullable = false, updatable = false)
	private UUID serviceId;

	@Column(name = "service_slug", nullable = false, updatable = false)
	private String serviceSlug;

	@Column(name = "service_name", nullable = false, updatable = false)
	private String serviceName;

	@Column(name = "duration_minutes", nullable = false, updatable = false)
	private int durationMinutes;

	@Column(name = "price_amount", nullable = false, updatable = false, precision = 10, scale = 2)
	private BigDecimal priceAmount;

	@Column(nullable = false, updatable = false, length = 3)
	private String currency;

	@Column(name = "address_line1", nullable = false, updatable = false)
	private String addressLine1;

	@Column(name = "address_line2", updatable = false)
	private String addressLine2;

	@Column(name = "address_city", nullable = false, updatable = false)
	private String addressCity;

	@Column(name = "address_postcode", nullable = false, updatable = false)
	private String addressPostcode;

	@Column(name = "address_country_code", nullable = false, updatable = false)
	private String addressCountryCode;

	@Column(nullable = false, updatable = false)
	private double latitude;

	@Column(nullable = false, updatable = false)
	private double longitude;

	@Column(name = "start_at", nullable = false)
	private Instant startAt;

	@Column(name = "end_at", nullable = false)
	private Instant endAt;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private BookingStatus status = BookingStatus.CONFIRMED;

	@Column(length = 500)
	private String notes;

	@Column(name = "reschedule_count", nullable = false)
	private int rescheduleCount;

	@Column(name = "started_at")
	private Instant startedAt;

	@Column(name = "completed_at")
	private Instant completedAt;

	@Column(name = "cancelled_at")
	private Instant cancelledAt;

	@Column(name = "cancelled_by")
	private UUID cancelledBy;

	@Enumerated(EnumType.STRING)
	@Column(name = "cancelled_by_role", length = 20)
	private Role cancelledByRole;

	@Column(name = "cancellation_reason", length = 500)
	private String cancellationReason;

	@Column(name = "late_cancellation")
	private Boolean lateCancellation;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@UpdateTimestamp
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Version
	private long version;

	protected Booking() {
		// for JPA
	}

	Booking(UUID customerId, UUID providerId, HomeService service, AddressDetails address, Instant startAt,
			String notes) {
		this.customerId = customerId;
		this.providerId = providerId;
		this.serviceId = service.getId();
		this.serviceSlug = service.getSlug();
		this.serviceName = service.getName();
		this.durationMinutes = service.getDurationMinutes();
		this.priceAmount = service.getBasePrice();
		this.currency = service.getCurrency();
		this.addressLine1 = address.line1();
		this.addressLine2 = address.line2();
		this.addressCity = address.city();
		this.addressPostcode = address.postcode();
		this.addressCountryCode = address.countryCode();
		this.latitude = address.latitude();
		this.longitude = address.longitude();
		this.startAt = startAt;
		this.endAt = startAt.plusSeconds(service.getDurationMinutes() * 60L);
		this.notes = notes;
	}

	/** CONFIRMED → IN_PROGRESS, from {@link BookingPolicy#START_EARLY_WINDOW} before the start. */
	void start(Instant now) {
		require(BookingStatus.IN_PROGRESS, "start");
		if (now.isBefore(this.startAt.minus(BookingPolicy.START_EARLY_WINDOW))) {
			throw BookingRuleException.tooEarlyToStart((int) BookingPolicy.START_EARLY_WINDOW.toMinutes());
		}
		this.status = BookingStatus.IN_PROGRESS;
		this.startedAt = now;
	}

	/** IN_PROGRESS → COMPLETED. */
	void complete(Instant now) {
		require(BookingStatus.COMPLETED, "complete");
		this.status = BookingStatus.COMPLETED;
		this.completedAt = now;
	}

	/**
	 * → CANCELLED. Who may cancel in which status is checked by the caller; this only
	 * enforces the state machine and records the details.
	 */
	void cancel(UUID actorId, Role actorRole, String reason, Instant now) {
		require(BookingStatus.CANCELLED, "cancel");
		this.status = BookingStatus.CANCELLED;
		this.cancelledAt = now;
		this.cancelledBy = actorId;
		this.cancelledByRole = actorRole;
		this.cancellationReason = reason;
		this.lateCancellation = actorRole == Role.CUSTOMER
				&& now.isAfter(this.startAt.minus(BookingPolicy.FREE_CHANGE_CUTOFF));
	}

	/** Moves a CONFIRMED booking to a new time, possibly with another provider. */
	void reschedule(UUID newProviderId, Instant newStart) {
		if (this.status != BookingStatus.CONFIRMED) {
			throw BookingRuleException.invalidTransition(this.status, "reschedule");
		}
		this.providerId = newProviderId;
		this.startAt = newStart;
		this.endAt = newStart.plusSeconds(this.durationMinutes * 60L);
		this.rescheduleCount++;
	}

	private void require(BookingStatus next, String action) {
		if (!this.status.canMoveTo(next)) {
			throw BookingRuleException.invalidTransition(this.status, action);
		}
	}

	UUID getId() {
		return this.id;
	}

	UUID getCustomerId() {
		return this.customerId;
	}

	UUID getProviderId() {
		return this.providerId;
	}

	UUID getServiceId() {
		return this.serviceId;
	}

	String getServiceSlug() {
		return this.serviceSlug;
	}

	String getServiceName() {
		return this.serviceName;
	}

	int getDurationMinutes() {
		return this.durationMinutes;
	}

	BigDecimal getPriceAmount() {
		return this.priceAmount;
	}

	String getCurrency() {
		return this.currency;
	}

	String getAddressLine1() {
		return this.addressLine1;
	}

	String getAddressLine2() {
		return this.addressLine2;
	}

	String getAddressCity() {
		return this.addressCity;
	}

	String getAddressPostcode() {
		return this.addressPostcode;
	}

	String getAddressCountryCode() {
		return this.addressCountryCode;
	}

	double getLatitude() {
		return this.latitude;
	}

	double getLongitude() {
		return this.longitude;
	}

	Instant getStartAt() {
		return this.startAt;
	}

	Instant getEndAt() {
		return this.endAt;
	}

	BookingStatus getStatus() {
		return this.status;
	}

	String getNotes() {
		return this.notes;
	}

	int getRescheduleCount() {
		return this.rescheduleCount;
	}

	Instant getCancelledAt() {
		return this.cancelledAt;
	}

	Role getCancelledByRole() {
		return this.cancelledByRole;
	}

	String getCancellationReason() {
		return this.cancellationReason;
	}

	Boolean getLateCancellation() {
		return this.lateCancellation;
	}

	Instant getCreatedAt() {
		return this.createdAt;
	}

}
