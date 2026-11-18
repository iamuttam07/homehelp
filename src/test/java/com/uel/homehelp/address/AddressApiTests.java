package com.uel.homehelp.address;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import com.jayway.jsonpath.JsonPath;
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

import com.uel.homehelp.TestUsers;
import com.uel.homehelp.TestUsers.LoggedInUser;
import com.uel.homehelp.TestcontainersConfiguration;
import com.uel.homehelp.user.Role;

@SpringBootTest
@Import({ TestcontainersConfiguration.class, TestUsers.class })
@AutoConfigureMockMvc
class AddressApiTests {

	private static final String ADDRESSES = "/api/users/me/addresses";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private TestUsers testUsers;

	@Autowired
	private JdbcTemplate jdbc;

	private LoggedInUser asha;

	private LoggedInUser ravi;

	@BeforeEach
	void createUsers() {
		this.jdbc.update("DELETE FROM users");
		this.asha = this.testUsers.create("asha@example.com", Role.CUSTOMER);
		this.ravi = this.testUsers.create("ravi@example.com", Role.CUSTOMER);
	}

	@Test
	void firstAddressBecomesDefault() throws Exception {
		create(this.asha, home())
			.andExpect(status().isCreated())
			.andExpect(header().string(HttpHeaders.LOCATION, org.hamcrest.Matchers.startsWith(ADDRESSES + "/")))
			.andExpect(jsonPath("$.id").isNotEmpty())
			.andExpect(jsonPath("$.label").value("Home"))
			.andExpect(jsonPath("$.postcode").value("E1 6AN"))
			.andExpect(jsonPath("$.countryCode").value("GB"))
			.andExpect(jsonPath("$.latitude").value(51.5202))
			.andExpect(jsonPath("$.longitude").value(-0.0716))
			.andExpect(jsonPath("$.isDefault").value(true));
	}

	@Test
	void laterAddressesAreNotDefaultAndListShowsDefaultFirst() throws Exception {
		create(this.asha, home());
		create(this.asha, work()).andExpect(jsonPath("$.isDefault").value(false));

		list(this.asha)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(2)))
			.andExpect(jsonPath("$[0].label").value("Home"))
			.andExpect(jsonPath("$[0].isDefault").value(true))
			.andExpect(jsonPath("$[1].label").value("Work"));
	}

	@Test
	void databaseDerivesPostgisLocationFromCoordinates() throws Exception {
		String id = id(create(this.asha, home()));

		Map<String, Object> point = this.jdbc.queryForMap(
				"SELECT ST_Y(location::geometry) AS lat, ST_X(location::geometry) AS lon FROM addresses WHERE id = ?::uuid",
				id);
		assertThat((Double) point.get("lat")).isCloseTo(51.5202, within(1e-9));
		assertThat((Double) point.get("lon")).isCloseTo(-0.0716, within(1e-9));

		// Shoreditch to Canary Wharf is roughly 4 km.
		String other = id(create(this.asha, work()));
		Double metres = this.jdbc.queryForObject("""
				SELECT ST_Distance(a.location, b.location) FROM addresses a, addresses b
				WHERE a.id = ?::uuid AND b.id = ?::uuid""", Double.class, id, other);
		assertThat(metres).isBetween(3_000.0, 5_500.0);
	}

	@Test
	void updateReplacesFieldsAndLocationFollows() throws Exception {
		String id = id(create(this.asha, home()));

		this.mockMvc.perform(put(ADDRESSES + "/" + id).header(HttpHeaders.AUTHORIZATION, this.asha.bearer())
				.contentType(MediaType.APPLICATION_JSON).content(work()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.label").value("Work"))
			.andExpect(jsonPath("$.line2").doesNotExist())
			.andExpect(jsonPath("$.isDefault").value(true));

		Double lat = this.jdbc.queryForObject("SELECT ST_Y(location::geometry) FROM addresses WHERE id = ?::uuid",
				Double.class, id);
		assertThat(lat).isCloseTo(51.5054, within(1e-9));
	}

	@Test
	void makeDefaultSwitchesTheDefault() throws Exception {
		String homeId = id(create(this.asha, home()));
		String workId = id(create(this.asha, work()));

		this.mockMvc.perform(put(ADDRESSES + "/" + workId + "/default")
				.header(HttpHeaders.AUTHORIZATION, this.asha.bearer()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.isDefault").value(true));

		list(this.asha).andExpect(jsonPath("$[0].id").value(workId))
			.andExpect(jsonPath("$[1].id").value(homeId))
			.andExpect(jsonPath("$[1].isDefault").value(false));
	}

	@Test
	void deletingTheDefaultPromotesTheOldestRemaining() throws Exception {
		String homeId = id(create(this.asha, home()));
		String workId = id(create(this.asha, work()));
		create(this.asha, home().replace("Home", "Gym"));

		this.mockMvc.perform(delete(ADDRESSES + "/" + homeId).header(HttpHeaders.AUTHORIZATION, this.asha.bearer()))
			.andExpect(status().isNoContent());

		list(this.asha).andExpect(jsonPath("$", hasSize(2)))
			.andExpect(jsonPath("$[0].id").value(workId))
			.andExpect(jsonPath("$[0].isDefault").value(true))
			.andExpect(jsonPath("$[1].isDefault").value(false));
	}

	@Test
	void usersCannotSeeOrChangeEachOthersAddresses() throws Exception {
		String ashasHome = id(create(this.asha, home()));
		String path = ADDRESSES + "/" + ashasHome;

		list(this.ravi).andExpect(jsonPath("$", hasSize(0)));
		this.mockMvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, this.ravi.bearer()))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.title").value("Address not found"));
		this.mockMvc.perform(put(path).header(HttpHeaders.AUTHORIZATION, this.ravi.bearer())
				.contentType(MediaType.APPLICATION_JSON).content(work()))
			.andExpect(status().isNotFound());
		this.mockMvc.perform(put(path + "/default").header(HttpHeaders.AUTHORIZATION, this.ravi.bearer()))
			.andExpect(status().isNotFound());
		this.mockMvc.perform(delete(path).header(HttpHeaders.AUTHORIZATION, this.ravi.bearer()))
			.andExpect(status().isNotFound());

		this.mockMvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, this.asha.bearer()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.label").value("Home"));
	}

	@Test
	void unknownAddressIsNotFound() throws Exception {
		this.mockMvc.perform(get(ADDRESSES + "/00000000-0000-0000-0000-000000000000")
				.header(HttpHeaders.AUTHORIZATION, this.asha.bearer()))
			.andExpect(status().isNotFound());
	}

	@Test
	void rejectsInvalidAddress() throws Exception {
		create(this.asha, """
				{"line1": "", "city": "London", "postcode": "E1 6AN", "countryCode": "GBR",
				 "latitude": 95.0, "longitude": -0.07}
				""").andExpect(status().isBadRequest());
		create(this.asha, """
				{"line1": "1 High St", "city": "London", "postcode": "E1 6AN", "countryCode": "GB"}
				""").andExpect(status().isBadRequest());

		list(this.asha).andExpect(jsonPath("$", hasSize(0)));
	}

	@Test
	void normalizesCountryCodeAndPostcode() throws Exception {
		create(this.asha, home().replace("\"GB\"", "\" gb \"").replace("E1 6AN", "e1 6an"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.countryCode").value("GB"))
			.andExpect(jsonPath("$.postcode").value("E1 6AN"));
	}

	@Test
	void limitsEachUserToTenAddresses() throws Exception {
		for (int i = 1; i <= 10; i++) {
			create(this.asha, home().replace("Home", "Place " + i)).andExpect(status().isCreated());
		}

		create(this.asha, work())
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.title").value("Address limit reached"))
			.andExpect(jsonPath("$.limit").value(10));
		list(this.asha).andExpect(jsonPath("$", hasSize(10)));

		// The limit is per user.
		create(this.ravi, work()).andExpect(status().isCreated());
	}

	@Test
	void deletingAnAddressFreesASlot() throws Exception {
		String first = id(create(this.asha, home()));
		for (int i = 2; i <= 10; i++) {
			create(this.asha, home().replace("Home", "Place " + i));
		}
		create(this.asha, work()).andExpect(status().isConflict());

		this.mockMvc.perform(delete(ADDRESSES + "/" + first).header(HttpHeaders.AUTHORIZATION, this.asha.bearer()))
			.andExpect(status().isNoContent());

		create(this.asha, work()).andExpect(status().isCreated());
	}

	@Test
	void addressesNeedLogin() throws Exception {
		this.mockMvc.perform(get(ADDRESSES)).andExpect(status().isUnauthorized());
	}

	@Test
	void deletingUserDeletesTheirAddresses() throws Exception {
		create(this.asha, home());
		this.jdbc.update("DELETE FROM users WHERE id = ?", this.asha.user().getId());

		assertThat(this.jdbc.queryForObject("SELECT count(*) FROM addresses", Integer.class)).isZero();
	}

	// --- helpers ---

	private ResultActions create(LoggedInUser user, String json) throws Exception {
		return this.mockMvc.perform(post(ADDRESSES).header(HttpHeaders.AUTHORIZATION, user.bearer())
			.contentType(MediaType.APPLICATION_JSON)
			.content(json));
	}

	private ResultActions list(LoggedInUser user) throws Exception {
		return this.mockMvc.perform(get(ADDRESSES).header(HttpHeaders.AUTHORIZATION, user.bearer()));
	}

	private static String id(ResultActions result) throws Exception {
		return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id");
	}

	/** Shoreditch, London. */
	private static String home() {
		return """
				{"label": "Home", "line1": "12 Brick Lane", "line2": "Flat 3", "city": "London",
				 "postcode": "E1 6AN", "countryCode": "GB", "latitude": 51.5202, "longitude": -0.0716}
				""";
	}

	/** Canary Wharf, London. */
	private static String work() {
		return """
				{"label": "Work", "line1": "1 Canada Square", "city": "London",
				 "postcode": "E14 5AB", "countryCode": "GB", "latitude": 51.5054, "longitude": -0.0235}
				""";
	}

}
