package com.uel.homehelp.catalog;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.uel.homehelp.TestcontainersConfiguration;

/**
 * Browses the seed catalog from V7 (6 categories, 18 services) without logging in.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
class CatalogApiTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbc;

	@AfterEach
	void reactivateEverything() {
		this.jdbc.update("UPDATE services SET active = true");
		this.jdbc.update("UPDATE categories SET active = true");
	}

	// --- categories ---

	@Test
	void listsCategoriesInDisplayOrderWithServiceCounts() throws Exception {
		get("/api/catalog/categories")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(6)))
			.andExpect(jsonPath("$[*].slug").value(contains("cleaning", "plumbing", "electrical", "salon-at-home",
					"appliance-repair", "painting")))
			.andExpect(jsonPath("$[1].name").value("Plumbing"))
			.andExpect(jsonPath("$[1].icon").value("wrench"))
			.andExpect(jsonPath("$[1].serviceCount").value(4));
	}

	@Test
	void getsOneCategory() throws Exception {
		get("/api/catalog/categories/plumbing")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.name").value("Plumbing"))
			.andExpect(jsonPath("$.serviceCount").value(4));
	}

	@Test
	void unknownCategoryIsNotFound() throws Exception {
		get("/api/catalog/categories/gardening")
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.type").value("urn:homehelp:problem:category-not-found"));
	}

	// --- services: listing and paging ---

	@Test
	void listsAllServicesSortedByNameByDefault() throws Exception {
		get("/api/catalog/services")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items", hasSize(18)))
			.andExpect(jsonPath("$.items[0].name").value("Boiler service"))
			.andExpect(jsonPath("$.items[0].category.slug").value("plumbing"))
			.andExpect(jsonPath("$.items[0].basePrice").value(90.0))
			.andExpect(jsonPath("$.items[0].currency").value("GBP"))
			.andExpect(jsonPath("$.items[0].durationMinutes").value(60))
			.andExpect(jsonPath("$.page").value(0))
			.andExpect(jsonPath("$.size").value(20))
			.andExpect(jsonPath("$.totalItems").value(18))
			.andExpect(jsonPath("$.totalPages").value(1))
			.andExpect(jsonPath("$.hasNext").value(false));
	}

	@Test
	void pagesThroughResults() throws Exception {
		get("/api/catalog/services?size=5")
			.andExpect(jsonPath("$.items", hasSize(5)))
			.andExpect(jsonPath("$.totalItems").value(18))
			.andExpect(jsonPath("$.totalPages").value(4))
			.andExpect(jsonPath("$.hasNext").value(true));
		get("/api/catalog/services?size=5&page=3")
			.andExpect(jsonPath("$.items", hasSize(3)))
			.andExpect(jsonPath("$.page").value(3))
			.andExpect(jsonPath("$.hasNext").value(false));
		get("/api/catalog/services?size=5&page=10")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items", hasSize(0)));
	}

	// --- services: filters and sorting ---

	@Test
	void filtersByCategory() throws Exception {
		get("/api/catalog/services?category=plumbing")
			.andExpect(jsonPath("$.items[*].name")
				.value(contains("Boiler service", "Leak repair", "Tap replacement", "Toilet repair")));
	}

	@Test
	void unknownCategoryFilterGivesEmptyPage() throws Exception {
		get("/api/catalog/services?category=gardening")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items", hasSize(0)))
			.andExpect(jsonPath("$.totalItems").value(0));
	}

	@Test
	void searchesNameAndDescriptionIgnoringCase() throws Exception {
		get("/api/catalog/services?q=REPAIR")
			.andExpect(jsonPath("$.items[*].slug").value(containsInAnyOrder("leak-repair", "toilet-repair",
					"washing-machine-repair", "fridge-repair")));
		// "deposit" only appears in a description.
		get("/api/catalog/services?q=deposit")
			.andExpect(jsonPath("$.items[*].slug").value(contains("end-of-tenancy-clean")));
	}

	@Test
	void searchTreatsWildcardsLiterally() throws Exception {
		get("/api/catalog/services?q=%25").andExpect(jsonPath("$.items", hasSize(0)));
		get("/api/catalog/services?q=_").andExpect(jsonPath("$.items", hasSize(0)));
	}

	@Test
	void filtersByPriceRange() throws Exception {
		get("/api/catalog/services?maxPrice=50")
			.andExpect(jsonPath("$.items[*].slug").value(containsInAnyOrder("mens-haircut", "manicure", "womens-haircut")));
		get("/api/catalog/services?minPrice=200")
			.andExpect(jsonPath("$.items[*].slug").value(containsInAnyOrder("end-of-tenancy-clean", "room-painting")));
		get("/api/catalog/services?minPrice=60&maxPrice=60")
			.andExpect(jsonPath("$.items[*].slug").value(containsInAnyOrder("standard-home-clean", "socket-installation")));
	}

	@Test
	void filtersByMaxDuration() throws Exception {
		get("/api/catalog/services?maxDuration=45")
			.andExpect(jsonPath("$.items[*].slug").value(contains("manicure", "mens-haircut")));
	}

	@Test
	void combinesFilters() throws Exception {
		get("/api/catalog/services?category=cleaning&maxPrice=100&sort=price_asc")
			.andExpect(jsonPath("$.items[*].slug").value(contains("oven-clean", "standard-home-clean")));
	}

	@Test
	void sortsByPriceAndDuration() throws Exception {
		get("/api/catalog/services?sort=price_asc").andExpect(jsonPath("$.items[0].slug").value("mens-haircut"));
		get("/api/catalog/services?sort=price_desc").andExpect(jsonPath("$.items[0].slug").value("room-painting"));
		get("/api/catalog/services?sort=duration").andExpect(jsonPath("$.items[0].slug").value("mens-haircut"))
			.andExpect(jsonPath("$.items[17].slug").value("room-painting"));
	}

	// --- one service ---

	@Test
	void getsOneServiceBySlug() throws Exception {
		get("/api/catalog/services/leak-repair")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.name").value("Leak repair"))
			.andExpect(jsonPath("$.description").value("Find and fix a leak under a sink, bath, or appliance."))
			.andExpect(jsonPath("$.category.slug").value("plumbing"))
			.andExpect(jsonPath("$.category.name").value("Plumbing"))
			.andExpect(jsonPath("$.basePrice").value(75.0))
			.andExpect(jsonPath("$.durationMinutes").value(60));
	}

	@Test
	void unknownServiceIsNotFound() throws Exception {
		get("/api/catalog/services/time-travel")
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.type").value("urn:homehelp:problem:service-not-found"));
	}

	// --- inactive items are hidden ---

	@Test
	void inactiveServicesAreHidden() throws Exception {
		this.jdbc.update("UPDATE services SET active = false WHERE slug = 'leak-repair'");

		get("/api/catalog/services?category=plumbing")
			.andExpect(jsonPath("$.items[*].slug").value(not(hasItem("leak-repair"))))
			.andExpect(jsonPath("$.items", hasSize(3)));
		get("/api/catalog/services/leak-repair").andExpect(status().isNotFound());
		get("/api/catalog/categories/plumbing").andExpect(jsonPath("$.serviceCount").value(3));
	}

	@Test
	void inactiveCategoriesHideTheirServices() throws Exception {
		this.jdbc.update("UPDATE categories SET active = false WHERE slug = 'painting'");

		get("/api/catalog/categories").andExpect(jsonPath("$", hasSize(5)));
		get("/api/catalog/categories/painting").andExpect(status().isNotFound());
		get("/api/catalog/services").andExpect(jsonPath("$.totalItems").value(16));
		get("/api/catalog/services/room-painting").andExpect(status().isNotFound());
	}

	// --- invalid parameters ---

	@Test
	void rejectsOutOfRangePaging() throws Exception {
		get("/api/catalog/services?size=51&page=-1")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.type").value("urn:homehelp:problem:validation-failed"))
			.andExpect(jsonPath("$.errors[*].field").value(contains("page", "size")));
		get("/api/catalog/services?size=0").andExpect(status().isBadRequest());
	}

	@Test
	void rejectsUnknownSort() throws Exception {
		get("/api/catalog/services?sort=popularity")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].field").value("sort"))
			.andExpect(jsonPath("$.errors[0].message").value("must be one of: name, price_asc, price_desc, duration"));
	}

	@Test
	void rejectsInvalidPriceFilters() throws Exception {
		get("/api/catalog/services?minPrice=100&maxPrice=50")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.type").value("urn:homehelp:problem:invalid-price-range"));
		get("/api/catalog/services?minPrice=-5")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].field").value("minPrice"));
		get("/api/catalog/services?minPrice=cheap")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.type").value("urn:homehelp:problem:invalid-parameter"));
	}

	@Test
	void rejectsInvalidDurationFilter() throws Exception {
		get("/api/catalog/services?maxDuration=10").andExpect(status().isBadRequest());
	}

	private ResultActions get(String path) throws Exception {
		return this.mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(path));
	}

}
