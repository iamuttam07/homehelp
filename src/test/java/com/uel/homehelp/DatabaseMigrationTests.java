package com.uel.homehelp;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class DatabaseMigrationTests {

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void flywayAppliedFirstMigration() {
		Integer applied = jdbcTemplate.queryForObject(
				"SELECT count(*) FROM flyway_schema_history WHERE version = '1' AND success",
				Integer.class);
		assertThat(applied).isEqualTo(1);
	}

	@Test
	void postgisExtensionIsAvailable() {
		String version = jdbcTemplate.queryForObject("SELECT postgis_version()", String.class);
		assertThat(version).isNotBlank();
	}

}
