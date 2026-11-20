package com.uel.homehelp.catalog;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * A group of related services, such as Plumbing. The schema is defined by Flyway
 * migration V6; the starter categories by V7.
 */
@Entity
@Table(name = "categories")
public class Category {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(nullable = false, unique = true, length = 60)
	private String slug;

	@Column(nullable = false, length = 100)
	private String name;

	@Column(length = 500)
	private String description;

	/** Icon name for the frontend, e.g. {@code wrench}. */
	@Column(length = 50)
	private String icon;

	/** Lower numbers are listed first. */
	@Column(name = "sort_order", nullable = false)
	private int sortOrder;

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

	protected Category() {
		// for JPA
	}

	public Category(String slug, String name, String description, String icon, int sortOrder) {
		this.slug = slug;
		this.name = name;
		this.description = description;
		this.icon = icon;
		this.sortOrder = sortOrder;
	}

	public void setActive(boolean active) {
		this.active = active;
	}

	public UUID getId() {
		return this.id;
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

	public String getIcon() {
		return this.icon;
	}

	public int getSortOrder() {
		return this.sortOrder;
	}

	public boolean isActive() {
		return this.active;
	}

}
