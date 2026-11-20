package com.uel.homehelp.catalog;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;

import org.springframework.data.jpa.domain.Specification;

import com.uel.homehelp.common.Strings;

/**
 * Optional filters for browsing services. Every filter that is set must match.
 * Only active services in active categories are ever returned.
 *
 * @param category category slug
 * @param query text to look for in the name or description (case-insensitive)
 * @param minPrice lowest base price, inclusive
 * @param maxPrice highest base price, inclusive
 * @param maxDurationMinutes longest duration, inclusive
 */
record ServiceFilter(String category, String query, BigDecimal minPrice, BigDecimal maxPrice,
		Integer maxDurationMinutes) {

	Specification<HomeService> toSpecification() {
		return (root, cq, cb) -> {
			Join<HomeService, Category> category = root.join("category");
			List<Predicate> predicates = new ArrayList<>();
			predicates.add(cb.isTrue(root.get("active")));
			predicates.add(cb.isTrue(category.get("active")));
			String slug = Strings.trimToNull(this.category);
			if (slug != null) {
				predicates.add(cb.equal(category.get("slug"), slug.toLowerCase(Locale.ROOT)));
			}
			String text = Strings.trimToNull(this.query);
			if (text != null) {
				String pattern = "%" + escapeLike(text.toLowerCase(Locale.ROOT)) + "%";
				predicates.add(cb.or(cb.like(cb.lower(root.get("name")), pattern, '\\'),
						cb.like(cb.lower(root.get("description")), pattern, '\\')));
			}
			if (this.minPrice != null) {
				predicates.add(cb.greaterThanOrEqualTo(root.get("basePrice"), this.minPrice));
			}
			if (this.maxPrice != null) {
				predicates.add(cb.lessThanOrEqualTo(root.get("basePrice"), this.maxPrice));
			}
			if (this.maxDurationMinutes != null) {
				predicates.add(cb.lessThanOrEqualTo(root.get("durationMinutes"), this.maxDurationMinutes));
			}
			return cb.and(predicates.toArray(Predicate[]::new));
		};
	}

	/** Makes {@code %} and {@code _} in the search text match literally. */
	private static String escapeLike(String text) {
		return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
	}

}
