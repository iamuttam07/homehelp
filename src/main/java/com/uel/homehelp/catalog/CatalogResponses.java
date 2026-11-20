package com.uel.homehelp.catalog;

import java.math.BigDecimal;
import java.util.UUID;

/** JSON shapes returned by the catalog API. */
final class CatalogResponses {

	private CatalogResponses() {
	}

	record CategoryResponse(UUID id, String slug, String name, String description, String icon, long serviceCount) {

		static CategoryResponse from(Category category, long serviceCount) {
			return new CategoryResponse(category.getId(), category.getSlug(), category.getName(),
					category.getDescription(), category.getIcon(), serviceCount);
		}

	}

	/** The category as shown inside a service. */
	record CategorySummary(String slug, String name) {
	}

	record ServiceResponse(UUID id, String slug, String name, String description, CategorySummary category,
			BigDecimal basePrice, String currency, int durationMinutes) {

		static ServiceResponse from(HomeService service) {
			Category category = service.getCategory();
			return new ServiceResponse(service.getId(), service.getSlug(), service.getName(), service.getDescription(),
					new CategorySummary(category.getSlug(), category.getName()), service.getBasePrice(),
					service.getCurrency(), service.getDurationMinutes());
		}

	}

}
