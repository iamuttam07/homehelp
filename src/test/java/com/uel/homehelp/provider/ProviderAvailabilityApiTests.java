package com.uel.homehelp.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
class ProviderAvailabilityApiTests {

	private static final String AVAILABILITY = "/api/provider/availability";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private TestUsers testUsers;

	@Autowired
	private JdbcTemplate jdbc;

	private LoggedInUser provider;

	@BeforeEach
	void createProvider() {
		this.jdbc.update("DELETE FROM users");
		this.provider = this.testUsers.create("ravi@example.com", Role.PROVIDER);
	}

	@Test
	void newProviderHasNoWindowsAndLondonTime() throws Exception {
		get(this.provider)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.timeZone").value("Europe/London"))
			.andExpect(jsonPath("$.windows", hasSize(0)));
	}

	@Test
	void savesTheWeekSortedByDayAndTime() throws Exception {
		replace(this.provider, "Europe/London", """
				{"dayOfWeek": "SATURDAY", "start": "10:00", "end": "14:00"},
				{"dayOfWeek": "MONDAY",   "start": "14:00", "end": "18:00"},
				{"dayOfWeek": "MONDAY",   "start": "09:00", "end": "12:00"},
				{"dayOfWeek": "TUESDAY",  "start": "09:00", "end": "17:00"}
				""")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.timeZone").value("Europe/London"))
			.andExpect(jsonPath("$.windows[*].dayOfWeek").value(contains("MONDAY", "MONDAY", "TUESDAY", "SATURDAY")))
			.andExpect(jsonPath("$.windows[0].start").value("09:00"))
			.andExpect(jsonPath("$.windows[0].end").value("12:00"))
			.andExpect(jsonPath("$.windows[1].start").value("14:00"));

		get(this.provider).andExpect(jsonPath("$.windows", hasSize(4)))
			.andExpect(jsonPath("$.windows[3].dayOfWeek").value("SATURDAY"));
		assertThat(this.jdbc.queryForList("SELECT DISTINCT day_of_week FROM provider_availability ORDER BY 1",
				Integer.class)).containsExactly(1, 2, 6);
	}

	@Test
	void replacingRemovesTheOldWeek() throws Exception {
		replace(this.provider, "Europe/London", "{\"dayOfWeek\": \"MONDAY\", \"start\": \"09:00\", \"end\": \"17:00\"}");

		replace(this.provider, "Europe/Paris", "{\"dayOfWeek\": \"FRIDAY\", \"start\": \"08:00\", \"end\": \"12:00\"}")
			.andExpect(jsonPath("$.timeZone").value("Europe/Paris"))
			.andExpect(jsonPath("$.windows[*].dayOfWeek").value(contains("FRIDAY")));
		get(this.provider).andExpect(jsonPath("$.windows", hasSize(1)));
	}

	@Test
	void emptyWeekClearsAvailability() throws Exception {
		replace(this.provider, "Europe/London", "{\"dayOfWeek\": \"MONDAY\", \"start\": \"09:00\", \"end\": \"17:00\"}");

		replace(this.provider, "Europe/London", "").andExpect(status().isOk()).andExpect(jsonPath("$.windows", hasSize(0)));
	}

	@Test
	void touchingWindowsAreAllowed() throws Exception {
		replace(this.provider, "Europe/London", """
				{"dayOfWeek": "MONDAY", "start": "09:00", "end": "12:00"},
				{"dayOfWeek": "MONDAY", "start": "12:00", "end": "17:00"}
				""").andExpect(status().isOk());
	}

	@Test
	void sameTimesOnDifferentDaysAreAllowed() throws Exception {
		replace(this.provider, "Europe/London", """
				{"dayOfWeek": "MONDAY",  "start": "09:00", "end": "17:00"},
				{"dayOfWeek": "TUESDAY", "start": "09:00", "end": "17:00"}
				""").andExpect(status().isOk());
	}

	@Test
	void rejectsOverlappingWindowsAndKeepsTheOldWeek() throws Exception {
		replace(this.provider, "Europe/London", "{\"dayOfWeek\": \"FRIDAY\", \"start\": \"09:00\", \"end\": \"17:00\"}");

		replace(this.provider, "Europe/London", """
				{"dayOfWeek": "MONDAY", "start": "09:00", "end": "13:00"},
				{"dayOfWeek": "MONDAY", "start": "12:00", "end": "17:00"}
				""")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.type").value("urn:homehelp:problem:invalid-availability"))
			.andExpect(jsonPath("$.problems[0]").value("MONDAY: 12:00-17:00 overlaps 09:00-13:00."));

		get(this.provider).andExpect(jsonPath("$.windows[*].dayOfWeek").value(contains("FRIDAY")));
	}

	@Test
	void rejectsTimesOffTheQuarterHour() throws Exception {
		replace(this.provider, "Europe/London", "{\"dayOfWeek\": \"MONDAY\", \"start\": \"09:10\", \"end\": \"17:00\"}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.problems[0]", containsString("quarter hour")));
	}

	@Test
	void rejectsWindowsThatEndBeforeTheyStart() throws Exception {
		replace(this.provider, "Europe/London", "{\"dayOfWeek\": \"MONDAY\", \"start\": \"17:00\", \"end\": \"09:00\"}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.problems[0]", containsString("end must be after the start")));
		replace(this.provider, "Europe/London", "{\"dayOfWeek\": \"MONDAY\", \"start\": \"09:00\", \"end\": \"09:00\"}")
			.andExpect(status().isBadRequest());
	}

	@Test
	void rejectsMoreThanFourWindowsADay() throws Exception {
		replace(this.provider, "Europe/London", """
				{"dayOfWeek": "MONDAY", "start": "08:00", "end": "09:00"},
				{"dayOfWeek": "MONDAY", "start": "10:00", "end": "11:00"},
				{"dayOfWeek": "MONDAY", "start": "12:00", "end": "13:00"},
				{"dayOfWeek": "MONDAY", "start": "14:00", "end": "15:00"},
				{"dayOfWeek": "MONDAY", "start": "16:00", "end": "17:00"}
				""")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.problems[0]").value("MONDAY: at most 4 windows per day."));
	}

	@Test
	void reportsEveryProblemAtOnce() throws Exception {
		replace(this.provider, "Mars/Olympus_Mons", """
				{"dayOfWeek": "MONDAY", "start": "09:10", "end": "17:00"},
				{"dayOfWeek": "TUESDAY", "start": "17:00", "end": "09:00"}
				""")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.problems", hasSize(3)))
			.andExpect(jsonPath("$.problems", hasItem(containsString("not a known time zone"))));
	}

	@Test
	void rejectsMalformedTimesAndDays() throws Exception {
		replace(this.provider, "Europe/London", "{\"dayOfWeek\": \"MONDAY\", \"start\": \"9am\", \"end\": \"17:00\"}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.type").value("urn:homehelp:problem:malformed-request"));
		replace(this.provider, "Europe/London", "{\"dayOfWeek\": \"FUNDAY\", \"start\": \"09:00\", \"end\": \"17:00\"}")
			.andExpect(status().isBadRequest());
		replace(this.provider, "Europe/London", "{\"dayOfWeek\": \"MONDAY\", \"start\": \"09:00\"}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.type").value("urn:homehelp:problem:validation-failed"));
	}

	@Test
	void customersCannotSetAvailability() throws Exception {
		LoggedInUser customer = this.testUsers.create("asha@example.com", Role.CUSTOMER);

		get(customer).andExpect(status().isForbidden());
		replace(customer, "Europe/London", "").andExpect(status().isForbidden());
	}

	private ResultActions get(LoggedInUser user) throws Exception {
		return this.mockMvc
			.perform(MockMvcRequestBuilders.get(AVAILABILITY).header(HttpHeaders.AUTHORIZATION, user.bearer()));
	}

	private ResultActions replace(LoggedInUser user, String timeZone, String windowsJson) throws Exception {
		return this.mockMvc.perform(put(AVAILABILITY).header(HttpHeaders.AUTHORIZATION, user.bearer())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"timeZone\": \"%s\", \"windows\": [%s]}".formatted(timeZone, windowsJson)));
	}

}
