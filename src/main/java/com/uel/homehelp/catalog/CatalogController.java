package com.uel.homehelp.catalog;

import java.math.BigDecimal;
import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.uel.homehelp.catalog.CatalogResponses.CategoryResponse;
import com.uel.homehelp.catalog.CatalogResponses.ServiceResponse;
import com.uel.homehelp.common.web.PageResponse;

/** Public catalog browsing; no login needed (see SecurityConfig). */
@RestController
@RequestMapping("/api/catalog")
@Tag(name = "Catalog", description = "Browse service categories and services (public)")
@SecurityRequirements
class CatalogController {

	static final int MAX_PAGE_SIZE = 50;

	private final CatalogService catalog;

	CatalogController(CatalogService catalog) {
		this.catalog = catalog;
	}

	@GetMapping("/categories")
	@Operation(summary = "List categories", description = "Active categories in display order, with their number of active services.")
	List<CategoryResponse> categories() {
		return this.catalog.listCategories();
	}

	@GetMapping("/categories/{slug}")
	@Operation(summary = "Get a category")
	CategoryResponse category(@PathVariable String slug) {
		return this.catalog.getCategory(slug);
	}

	@GetMapping("/services")
	@Operation(summary = "Search services", description = "All filters are optional and combine with AND.")
	PageResponse<ServiceResponse> services(
			@Parameter(description = "Category slug, e.g. plumbing")
			@RequestParam(name = "category", required = false) @Size(max = 60) String category,
			@Parameter(description = "Text to find in the name or description")
			@RequestParam(name = "q", required = false) @Size(max = 100) String query,
			@Parameter(description = "Lowest base price, inclusive")
			@RequestParam(name = "minPrice", required = false) @DecimalMin("0") BigDecimal minPrice,
			@Parameter(description = "Highest base price, inclusive")
			@RequestParam(name = "maxPrice", required = false) @DecimalMin("0") BigDecimal maxPrice,
			@Parameter(description = "Longest duration in minutes, inclusive")
			@RequestParam(name = "maxDuration", required = false) @Min(15) @Max(720) Integer maxDuration,
			@Parameter(description = "name (default), price_asc, price_desc, or duration")
			@RequestParam(name = "sort", required = false) @Pattern(regexp = ServiceSort.PATTERN,
					message = "must be one of: name, price_asc, price_desc, duration") String sort,
			@Parameter(description = "Zero-based page number")
			@RequestParam(name = "page", defaultValue = "0") @Min(0) int page,
			@Parameter(description = "Results per page, 1 to 50")
			@RequestParam(name = "size", defaultValue = "20") @Min(1) @Max(MAX_PAGE_SIZE) int size) {
		ServiceFilter filter = new ServiceFilter(category, query, minPrice, maxPrice, maxDuration);
		return this.catalog.searchServices(filter, ServiceSort.fromParameter(sort), page, size);
	}

	@GetMapping("/services/{slug}")
	@Operation(summary = "Get a service")
	ServiceResponse service(@PathVariable String slug) {
		return this.catalog.getService(slug);
	}

}
