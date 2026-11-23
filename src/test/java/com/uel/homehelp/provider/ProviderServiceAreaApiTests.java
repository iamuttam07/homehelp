package com.uel.homehelp.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import com.uel.homehelp.TestUsers;
import com.uel.homehelp.TestUsers.LoggedInUser;
import com.uel.homehelp.TestcontainersConfiguration;
import com.uel.homehelp.user.Role;

@SpringBootTest
@Import({ TestcontainersConfiguration.class, TestUsers.class })
@AutoConfigureMockMvc
class ProviderServiceAreaApiTests {

	private static final String AREA = "/api/provider/service-area";

	/** Shoreditch, London. */
	private static final String SHOREDITCH_5KM = "{\"latitude\": 51.5202, \"longitude\": -0.0716, \"radiusKm\": 5}";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private TestUsers testUsers;

	@Autowired
	private JdbcTemplate jdbc;

	private LoggedInUser provider;

	@BeforeEach
	void createProvider() {
		this.jdbc.update("DELETE FROM bookings");
		this.jdbc.update("DELETE FROM users");
		this.provider = this.testUsers.create("ravi@example.com", Role.PROVIDER);
	}

	@Test
	void areaIsNotFoundUntilSet() throws Exception {
		get(this.provider)
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.type").value("urn:homehelp:problem:service-area-not-found"));
	}

	@Test
	void setsAndReadsTheArea() throws Exception {
		set(this.provider, SHOREDITCH_5KM)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.latitude").value(51.5202))
			.andExpect(jsonPath("$.longitude").value(-0.0716))
			.andExpect(jsonPath("$.radiusKm").value(5));

		get(this.provider).andExpect(status().isOk()).andExpect(jsonPath("$.radiusKm").value(5));
	}

	@Test
	void replacesThePreviousArea() throws Exception {
		set(this.provider, SHOREDITCH_5KM);

		set(this.provider, "{\"latitude\": 51.4700, \"longitude\": -0.4543, \"radiusKm\": 20}")
			.andExpect(jsonPath("$.latitude").value(51.47))
			.andExpect(jsonPath("$.radiusKm").value(20));
	}

	@Test
	void databaseCanAnswerWhetherAProviderCoversAnAddress() throws Exception {
		set(this.provider, SHOREDITCH_5KM);

		assertThat(covers(this.provider.user().getId(), 51.5054, -0.0235)).as("Canary Wharf, ~4 km").isTrue();
		assertThat(covers(this.provider.user().getId(), 51.4700, -0.4543)).as("Heathrow, ~27 km").isFalse();
	}

	@Test
	void rejectsInvalidAreas() throws Exception {
		set(this.provider, "{\"latitude\": 91, \"longitude\": -0.07, \"radiusKm\": 5}").andExpect(status().isBadRequest());
		set(this.provider, "{\"latitude\": 51.5, \"longitude\": 181, \"radiusKm\": 5}").andExpect(status().isBadRequest());
		set(this.provider, "{\"latitude\": 51.5, \"longitude\": -0.07, \"radiusKm\": 0}").andExpect(status().isBadRequest());
		set(this.provider, "{\"latitude\": 51.5, \"longitude\": -0.07, \"radiusKm\": 51}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].field").value("radiusKm"));
		set(this.provider, "{\"latitude\": 51.5, \"longitude\": -0.07}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].field").value("radiusKm"));

		get(this.provider).andExpect(status().isNotFound());
	}

	@Test
	void customersCannotSetAnArea() throws Exception {
		LoggedInUser customer = this.testUsers.create("asha@example.com", Role.CUSTOMER);

		get(customer).andExpect(status().isForbidden());
		set(customer, SHOREDITCH_5KM).andExpect(status().isForbidden());
	}

	/** The kind of query matching (Step 21) will run against {@code base_location}. */
	private boolean covers(UUID providerId, double latitude, double longitude) {
		return this.jdbc.queryForObject("""
				SELECT ST_DWithin(base_location,
				                  ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography,
				                  service_radius_km * 1000)
				FROM provider_profiles WHERE user_id = ?""", Boolean.class, longitude, latitude, providerId);
	}

	private ResultActions get(LoggedInUser user) throws Exception {
		return this.mockMvc.perform(MockMvcRequestBuilders.get(AREA).header(HttpHeaders.AUTHORIZATION, user.bearer()));
	}

	private ResultActions set(LoggedInUser user, String json) throws Exception {
		return this.mockMvc.perform(put(AREA).header(HttpHeaders.AUTHORIZATION, user.bearer())
			.contentType(MediaType.APPLICATION_JSON)
			.content(json));
	}

}
