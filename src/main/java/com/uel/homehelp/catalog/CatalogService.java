package com.uel.homehelp.catalog;

import java.util.List;
import java.util.Locale;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.uel.homehelp.catalog.CatalogResponses.CategoryResponse;
import com.uel.homehelp.catalog.CatalogResponses.ServiceResponse;
import com.uel.homehelp.common.web.PageResponse;
import com.uel.homehelp.common.web.ResourceNotFoundException;

/** Read-only browsing of active categories and services. */
@Service
@Transactional(readOnly = true)
class CatalogService {

	private final CategoryRepository categories;

	private final HomeServiceRepository services;

	CatalogService(CategoryRepository categories, HomeServiceRepository services) {
		this.categories = categories;
		this.services = services;
	}

	List<CategoryResponse> listCategories() {
		return this.categories.findActiveWithServiceCounts()
			.stream()
			.map((row) -> CategoryResponse.from(row.category(), row.serviceCount()))
			.toList();
	}

	CategoryResponse getCategory(String slug) {
		Category category = this.categories.findBySlugAndActiveTrue(slug.toLowerCase(Locale.ROOT))
			.orElseThrow(() -> new ResourceNotFoundException("Category"));
		return CategoryResponse.from(category, this.categories.countActiveServices(category));
	}

	PageResponse<ServiceResponse> searchServices(ServiceFilter filter, ServiceSort sort, int page, int size) {
		if (filter.minPrice() != null && filter.maxPrice() != null
				&& filter.minPrice().compareTo(filter.maxPrice()) > 0) {
			throw new InvalidPriceRangeException();
		}
		Page<HomeService> result = this.services.findAll(filter.toSpecification(),
				PageRequest.of(page, size, sort.sort()));
		return PageResponse.from(result, ServiceResponse::from);
	}

	ServiceResponse getService(String slug) {
		return this.services.findBySlugAndActiveTrueAndCategoryActiveTrue(slug.toLowerCase(Locale.ROOT))
			.map(ServiceResponse::from)
			.orElseThrow(() -> new ResourceNotFoundException("Service"));
	}

}
