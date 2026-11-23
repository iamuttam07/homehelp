package com.uel.homehelp.provider;

import java.math.BigDecimal;

import com.uel.homehelp.catalog.Category;
import com.uel.homehelp.catalog.HomeService;

/** One service a provider offers. */
record SkillResponse(String slug, String name, String categorySlug, String categoryName, BigDecimal basePrice,
		String currency, int durationMinutes) {

	static SkillResponse from(HomeService service) {
		Category category = service.getCategory();
		return new SkillResponse(service.getSlug(), service.getName(), category.getSlug(), category.getName(),
				service.getBasePrice(), service.getCurrency(), service.getDurationMinutes());
	}

}
