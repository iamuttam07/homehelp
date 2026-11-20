package com.uel.homehelp.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import com.uel.homehelp.TestcontainersConfiguration;

/**
 * Checks the seed data from V7 and the rules the database enforces from V6.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class CatalogSchemaTests {

	@Autowired
	private CategoryRepository categories;

	@Autowired
	private HomeServiceRepository services;

	@Autowired
	private JdbcTemplate jdbc;

	@Test
	void seedsSixCategoriesInDisplayOrder() {
		List<String> slugs = this.jdbc.queryForList("SELECT slug FROM categories ORDER BY sort_order", String.class);

		assertThat(slugs).containsExactly("cleaning", "plumbing", "electrical", "salon-at-home", "appliance-repair",
				"painting");
	}

	@Test
	void seedsEighteenActiveServicesAcrossAllCategories() {
		List<Map<String, Object>> perCategory = this.jdbc.queryForList("""
				SELECT c.slug, count(s.id) AS services FROM categories c
				LEFT JOIN services s ON s.category_id = c.id AND s.active
				GROUP BY c.slug""");

		assertThat(this.jdbc.queryForObject("SELECT count(*) FROM services WHERE active", Integer.class)).isEqualTo(18);
		assertThat(perCategory).allSatisfy((row) -> assertThat((Long) row.get("services")).isGreaterThanOrEqualTo(2));
	}

	@Test
	@Transactional
	void mapsSeedDataToEntities() {
		HomeService leakRepair = this.services.findBySlug("leak-repair").orElseThrow();

		assertThat(leakRepair.getName()).isEqualTo("Leak repair");
		assertThat(leakRepair.getBasePrice()).isEqualByComparingTo(new BigDecimal("75.00"));
		assertThat(leakRepair.getCurrency()).isEqualTo("GBP");
		assertThat(leakRepair.getDurationMinutes()).isEqualTo(60);
		assertThat(leakRepair.getCategory().getSlug()).isEqualTo("plumbing");
		assertThat(this.categories.findBySlug("plumbing")).get()
			.extracting(Category::getIcon)
			.isEqualTo("wrench");
	}

	@Test
	void rejectsDurationsThatAreNotWholeQuarterHours() {
		assertThatThrownBy(() -> insertService("odd-duration", "10.00", 50))
			.isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("ck_services_duration");
		assertThatThrownBy(() -> insertService("too-long", "10.00", 735))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void rejectsNegativePrices() {
		assertThatThrownBy(() -> insertService("negative-price", "-1.00", 60))
			.isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("ck_services_base_price");
	}

	@Test
	void rejectsDuplicateAndMalformedSlugs() {
		assertThatThrownBy(() -> insertService("leak-repair", "10.00", 60))
			.isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("uq_services_slug");
		assertThatThrownBy(() -> insertService("Not A Slug", "10.00", 60))
			.isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("ck_services_slug");
	}

	private void insertService(String slug, String price, int minutes) {
		this.jdbc.update("""
				INSERT INTO services (category_id, slug, name, base_price, duration_minutes)
				SELECT id, ?, 'Test', ?::numeric, ? FROM categories WHERE slug = 'plumbing'""", slug, price, minutes);
	}

}
