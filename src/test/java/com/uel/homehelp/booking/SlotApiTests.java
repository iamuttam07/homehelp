package com.uel.homehelp.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import com.uel.homehelp.TestUsers;
import com.uel.homehelp.TestUsers.LoggedInUser;
import com.uel.homehelp.TestcontainersConfiguration;
import com.uel.homehelp.user.Role;

/**
 * End-to-end slot search: customer address in Shoreditch, providers set up directly
 * in the database. Dates are "next Monday", so the 2-hour notice never interferes.
 */
@SpringBootTest
@Import({ TestcontainersConfiguration.class, TestUsers.class })
@AutoConfigureMockMvc
class SlotApiTests {

	private static final ZoneId LONDON = ZoneId.of("Europe/London");

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private TestUsers testUsers;

	@Autowired
	private JdbcTemplate jdbc;

	private LoggedInUser customer;

	private UUID shoreditch;

	private LocalDate monday;

	@BeforeEach
	void setUp() {
		this.jdbc.update("DELETE FROM users");
		this.customer = this.testUsers.create("asha@example.com", Role.CUSTOMER);
		this.shoreditch = address(this.customer, 51.5202, -0.0716);
		this.monday = LocalDate.now(LONDON).plusDays(2).with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
	}

	@Test
	void listsSlotsForAnApprovedProviderWhoCoversTheAddress() throws Exception {
		provider("ravi@example.com", "APPROVED", "leak-repair", 51.5202, -0.0716, 5, 1, "09:00", "12:00");

		String body = slots("leak-repair", this.monday, this.shoreditch, null)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.service.slug").value("leak-repair"))
			.andExpect(jsonPath("$.service.durationMinutes").value(60))
			.andExpect(jsonPath("$.date").value(this.monday.toString()))
			.andExpect(jsonPath("$.timeZone").value("Europe/London"))
			.andExpect(jsonPath("$.addressId").value(this.shoreditch.toString()))
			.andExpect(jsonPath("$.slots", hasSize(5)))
			.andExpect(jsonPath("$.slots[0].availableProviders").value(1))
			.andReturn().getResponse().getContentAsString();

		assertThat(localTimes(body, "start", LONDON)).containsExactly("09:00", "09:30", "10:00", "10:30", "11:00");
		assertThat(localTimes(body, "end", LONDON)).first().isEqualTo("10:00");
		OffsetDateTime first = OffsetDateTime.parse(JsonPath.read(body, "$.slots[0].start"));
		assertThat(first.getOffset()).isEqualTo(LONDON.getRules().getOffset(first.toInstant()));
	}

	@Test
	void countsEveryFreeProviderPerSlot() throws Exception {
		provider("ravi@example.com", "APPROVED", "leak-repair", 51.5202, -0.0716, 5, 1, "09:00", "12:00");
		provider("sam@example.com", "APPROVED", "leak-repair", 51.5150, -0.0700, 10, 1, "10:00", "12:00");

		String body = slots("leak-repair", this.monday, this.shoreditch, null).andReturn().getResponse()
			.getContentAsString();

		List<Integer> counts = JsonPath.read(body, "$.slots[*].availableProviders");
		assertThat(localTimes(body, "start", LONDON)).containsExactly("09:00", "09:30", "10:00", "10:30", "11:00");
		assertThat(counts).containsExactly(1, 1, 2, 2, 2);
	}

	@Test
	void ignoresProvidersWhoAreNotApproved() throws Exception {
		provider("pending@example.com", "PENDING_REVIEW", "leak-repair", 51.5202, -0.0716, 5, 1, "09:00", "12:00");
		provider("onboarding@example.com", "ONBOARDING", "leak-repair", 51.5202, -0.0716, 5, 1, "09:00", "12:00");

		slots("leak-repair", this.monday, this.shoreditch, null).andExpect(jsonPath("$.slots", hasSize(0)));
	}

	@Test
	void ignoresProvidersWithoutTheSkill() throws Exception {
		provider("ravi@example.com", "APPROVED", "boiler-service", 51.5202, -0.0716, 5, 1, "09:00", "12:00");

		slots("leak-repair", this.monday, this.shoreditch, null).andExpect(jsonPath("$.slots", hasSize(0)));
		slots("boiler-service", this.monday, this.shoreditch, null).andExpect(jsonPath("$.slots", hasSize(5)));
	}

	@Test
	void ignoresProvidersWhoDoNotCoverTheAddress() throws Exception {
		provider("ravi@example.com", "APPROVED", "leak-repair", 51.5202, -0.0716, 5, 1, "09:00", "12:00");
		UUID heathrow = address(this.customer, 51.4700, -0.4543);

		slots("leak-repair", this.monday, heathrow, null).andExpect(jsonPath("$.slots", hasSize(0)));
	}

	@Test
	void dayWithoutWindowsHasNoSlots() throws Exception {
		provider("ravi@example.com", "APPROVED", "leak-repair", 51.5202, -0.0716, 5, 1, "09:00", "12:00");

		slots("leak-repair", this.monday.plusDays(1), this.shoreditch, null).andExpect(jsonPath("$.slots", hasSize(0)));
	}

	@Test
	void returnsTimesInTheRequestedTimeZone() throws Exception {
		provider("ravi@example.com", "APPROVED", "leak-repair", 51.5202, -0.0716, 5, 1, "09:00", "12:00");
		ZoneId paris = ZoneId.of("Europe/Paris");

		String body = slots("leak-repair", this.monday, this.shoreditch, "Europe/Paris")
			.andExpect(jsonPath("$.timeZone").value("Europe/Paris"))
			.andReturn().getResponse().getContentAsString();

		assertThat(localTimes(body, "start", paris)).first().isEqualTo("10:00");
		OffsetDateTime first = OffsetDateTime.parse(JsonPath.read(body, "$.slots[0].start"));
		assertThat(first.getOffset()).isEqualTo(paris.getRules().getOffset(first.toInstant()));
	}

	@Test
	void anotherUsersAddressIsNotFound() throws Exception {
		LoggedInUser other = this.testUsers.create("ravi.customer@example.com", Role.CUSTOMER);
		UUID theirs = address(other, 51.5202, -0.0716);

		slots("leak-repair", this.monday, theirs, null)
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.type").value("urn:homehelp:problem:address-not-found"));
	}

	@Test
	void unknownOrInactiveServiceIsNotFound() throws Exception {
		slots("time-travel", this.monday, this.shoreditch, null)
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.type").value("urn:homehelp:problem:service-not-found"));
	}

	@Test
	void rejectsDatesOutsideTheBookingWindow() throws Exception {
		LocalDate today = LocalDate.now(LONDON);

		slots("leak-repair", today.minusDays(1), this.shoreditch, null)
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.type").value("urn:homehelp:problem:invalid-date"))
			.andExpect(jsonPath("$.detail").value("The date is in the past."));
		slots("leak-repair", today.plusDays(31), this.shoreditch, null)
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.detail").value("Slots can be booked up to 30 days ahead."));
		slots("leak-repair", today.plusDays(30), this.shoreditch, null).andExpect(status().isOk());
	}

	@Test
	void rejectsUnknownAndFixedOffsetTimeZones() throws Exception {
		slots("leak-repair", this.monday, this.shoreditch, "Mars/Olympus_Mons")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.type").value("urn:homehelp:problem:invalid-time-zone"));
		slots("leak-repair", this.monday, this.shoreditch, "+01:00").andExpect(status().isBadRequest());
		slots("leak-repair", this.monday, this.shoreditch, "GMT+1").andExpect(status().isBadRequest());
	}

	@Test
	void rejectsMissingOrMalformedParameters() throws Exception {
		get("/api/slots?service=leak-repair&addressId=" + this.shoreditch).andExpect(status().isBadRequest());
		get("/api/slots?service=leak-repair&date=next-monday&addressId=" + this.shoreditch)
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.type").value("urn:homehelp:problem:invalid-parameter"));
		get("/api/slots?service=leak-repair&date=" + this.monday + "&addressId=not-a-uuid")
			.andExpect(status().isBadRequest());
	}

	@Test
	void needsLogin() throws Exception {
		this.mockMvc.perform(MockMvcRequestBuilders.get(
				"/api/slots?service=leak-repair&date=" + this.monday + "&addressId=" + this.shoreditch))
			.andExpect(status().isUnauthorized());
	}

	// --- helpers ---

	private ResultActions slots(String service, LocalDate date, UUID addressId, String timeZone) throws Exception {
		String path = "/api/slots?service=%s&date=%s&addressId=%s".formatted(service, date, addressId);
		return get((timeZone != null) ? path + "&timeZone=" + timeZone : path);
	}

	private ResultActions get(String path) throws Exception {
		return this.mockMvc
			.perform(MockMvcRequestBuilders.get(path).header(HttpHeaders.AUTHORIZATION, this.customer.bearer()));
	}

	private static List<String> localTimes(String body, String field, ZoneId zone) {
		List<String> values = JsonPath.read(body, "$.slots[*]." + field);
		return values.stream()
			.map((v) -> OffsetDateTime.parse(v).atZoneSameInstant(zone).toLocalTime())
			.map(LocalTime::toString)
			.toList();
	}

	private UUID address(LoggedInUser user, double latitude, double longitude) {
		return this.jdbc.queryForObject("""
				INSERT INTO addresses (user_id, line1, city, postcode, country_code, latitude, longitude)
				VALUES (?, '1 Test St', 'London', 'E1 1AA', 'GB', ?, ?) RETURNING id""", UUID.class,
				user.user().getId(), latitude, longitude);
	}

	/** A provider with one skill, a service area, and one weekly window (ISO day number). */
	private UUID provider(String email, String status, String skill, double latitude, double longitude, int radiusKm,
			int isoDay, String start, String end) {
		UUID id = this.testUsers.create(email, Role.PROVIDER).user().getId();
		this.jdbc.update("""
				UPDATE provider_profiles SET status = ?, base_latitude = ?, base_longitude = ?, service_radius_km = ?
				WHERE user_id = ?""", status, latitude, longitude, radiusKm, id);
		this.jdbc.update("INSERT INTO provider_services (provider_id, service_id) SELECT ?, id FROM services WHERE slug = ?",
				id, skill);
		this.jdbc.update("""
				INSERT INTO provider_availability (provider_id, day_of_week, start_time, end_time)
				VALUES (?, ?, ?::time, ?::time)""", id, isoDay, start, end);
		return id;
	}

}
