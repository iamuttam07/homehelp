package com.uel.homehelp.catalog;

import java.util.Locale;

import org.springframework.data.domain.Sort;

/** Sort orders for the service list. Ties are broken by name, then id, so pages are stable. */
enum ServiceSort {

	NAME(Sort.by("name")),

	PRICE_ASC(Sort.by(Sort.Order.asc("basePrice"), Sort.Order.asc("name"))),

	PRICE_DESC(Sort.by(Sort.Order.desc("basePrice"), Sort.Order.asc("name"))),

	DURATION(Sort.by(Sort.Order.asc("durationMinutes"), Sort.Order.asc("name")));

	/** Values accepted by the {@code sort} query parameter. */
	static final String PATTERN = "name|price_asc|price_desc|duration";

	private final Sort sort;

	ServiceSort(Sort sort) {
		this.sort = sort.and(Sort.by("id"));
	}

	Sort sort() {
		return this.sort;
	}

	static ServiceSort fromParameter(String value) {
		return (value == null) ? NAME : valueOf(value.toUpperCase(Locale.ROOT));
	}

}
