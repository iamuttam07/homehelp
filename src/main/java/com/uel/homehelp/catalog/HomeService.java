package com.uel.homehelp.catalog;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Something a customer can book, such as "Leak repair". Named {@code HomeService}
 * to avoid clashing with Spring's {@code @Service}. The schema is defined by Flyway
 * migration V6; the starter services by V7.
 */
@Entity
@Table(name = "services")
public class HomeService {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "category_id", nullable = false)
	private Category category;

	@Column(nullable = false, unique = true, length = 80)
	private String slug;

	@Column(nullable = false, length = 120)
	private String name;

	@Column(length = 1000)
	private String description;

	/** Starting price; the final price may depend on the job. */
	@Column(name = "base_price", nullable = false, precision = 10, scale = 2)
	private BigDecimal basePrice;

	/** ISO 4217 code, e.g. {@code GBP}. */
	@Column(nullable = false, length = 3)
	private String currency = "GBP";

	/** Expected length of the job, a multiple of 15 minutes. */
	@Column(name = "duration_minutes", nullable = false)
	private int durationMinutes;

	@Column(nullable = false)
	private boolean active = true;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@UpdateTimestamp
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Version
	private long version;

	protected HomeService() {
		// for JPA
	}

	public HomeService(Category category, String slug, String name, String description, BigDecimal basePrice,
			int durationMinutes) {
		this.category = category;
		this.slug = slug;
		this.name = name;
		this.description = description;
		this.basePrice = basePrice;
		this.durationMinutes = durationMinutes;
	}

	public void setActive(boolean active) {
		this.active = active;
	}

	public UUID getId() {
		return this.id;
	}

	public Category getCategory() {
		return this.category;
	}

	public String getSlug() {
		return this.slug;
	}

	public String getName() {
		return this.name;
	}

	public String getDescription() {
		return this.description;
	}

	public BigDecimal getBasePrice() {
		return this.basePrice;
	}

	public String getCurrency() {
		return this.currency;
	}

	public int getDurationMinutes() {
		return this.durationMinutes;
	}

	public boolean isActive() {
		return this.active;
	}

}
