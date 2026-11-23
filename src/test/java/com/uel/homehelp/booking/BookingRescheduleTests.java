package com.uel.homehelp.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.uel.homehelp.TestUsers;
import com.uel.homehelp.TestUsers.LoggedInUser;
import com.uel.homehelp.TestcontainersConfiguration;
import com.uel.homehelp.user.Role;

/** Step 19: rescheduling rules. Providers work Mondays 09:00-12:00. */
@SpringBootTest
@Import({ TestcontainersConfiguration.class, TestUsers.class })
@AutoConfigureMockMvc
class BookingRescheduleTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private TestUsers testUsers;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private StringRedisTemplate redis;

	private BookingTestSupport api;

	private LocalDate monday;

	private LoggedInUser asha;

	private UUID ashaHome;

	private LoggedInUser sam;

	private UUID samHome;

	private LoggedInUser ravi;

	@BeforeEach
	void setUp() {
		this.api = new BookingTestSupport(this.mockMvc);
		BookingFixtures.flushRedis(this.redis);
		BookingFixtures.clearBookingsAndUsers(this.jdbc);
		this.monday = BookingFixtures.nextMonday();
		this.asha = this.testUsers.create("asha@example.com", Role.CUSTOMER);
		this.ashaHome = BookingFixtures.address(this.jdbc, this.asha, 51.5202, -0.0716);
		this.sam = this.testUsers.create("sam@example.com", Role.CUSTOMER);
		this.samHome = BookingFixtures.address(this.jdbc, this.sam, 51.5150, -0.0700);
		this.ravi = BookingFixtures.approvedProvider(this.jdbc, this.testUsers, "ravi@example.com");
	}

	@AfterEach
	void cleanUp() {
		BookingFixtures.clearBookingsAndUsers(this.jdbc);
	}

	@Test
	void movesToAFreeTimeAndFreesTheOldOne() throws Exception {
		String id = this.api.holdAndBook(this.asha, this.ashaHome, this.monday, "09:00");

		String body = reschedule(this.asha, id, "11:00")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("CONFIRMED"))
			.andExpect(jsonPath("$.rescheduleCount").value(1))
			.andExpect(jsonPath("$.provider.id").value(this.ravi.user().getId().toString()))
			.andExpect(jsonPath("$.history[1].type").value("RESCHEDULED"))
			.andExpect(jsonPath("$.history[1].details", containsString("Moved from")))
			.andReturn().getResponse().getContentAsString();

		assertThat(BookingTestSupport.londonTimeOf(body, "$.start")).isEqualTo("11:00");
		assertThat(this.api.slotTimes(this.sam, this.samHome, this.monday)).containsExactly("09:00", "09:30", "10:00");
	}

	@Test
	void canMoveIntoTimeItAlreadyOverlaps() throws Exception {
		String id = this.api.holdAndBook(this.asha, this.ashaHome, this.monday, "09:00");

		reschedule(this.asha, id, "09:30").andExpect(status().isOk());
	}

	@Test
	void cannotMoveOntoSomeoneElsesBooking() throws Exception {
		String id = this.api.holdAndBook(this.asha, this.ashaHome, this.monday, "09:00");
		this.api.holdAndBook(this.sam, this.samHome, this.monday, "11:00");

		reschedule(this.asha, id, "10:30")
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.type").value("urn:homehelp:problem:slot-unavailable"));
		String body = this.api.get(this.asha, "/api/bookings/" + id).andExpect(jsonPath("$.rescheduleCount").value(0))
			.andReturn().getResponse().getContentAsString();
		assertThat(BookingTestSupport.londonTimeOf(body, "$.start")).isEqualTo("09:00");
	}

	@Test
	void keepsTheSameProviderWhenTheyAreFree() throws Exception {
		BookingFixtures.approvedProvider(this.jdbc, this.testUsers, "lee@example.com");
		String body = this.api.book(this.asha, this.api.holdId(this.asha, this.ashaHome, this.monday, "09:00"))
			.andReturn().getResponse().getContentAsString();
		String id = JsonPath.read(body, "$.id");
		String provider = JsonPath.read(body, "$.provider.id");

		reschedule(this.asha, id, "10:00").andExpect(jsonPath("$.provider.id").value(provider));
	}

	@Test
	void switchesProviderWhenTheCurrentOneIsBusy() throws Exception {
		BookingFixtures.approvedProvider(this.jdbc, this.testUsers, "lee@example.com");
		String body = this.api.book(this.asha, this.api.holdId(this.asha, this.ashaHome, this.monday, "09:00"))
			.andReturn().getResponse().getContentAsString();
		String id = JsonPath.read(body, "$.id");
		UUID provider = UUID.fromString(JsonPath.read(body, "$.provider.id"));
		BookingFixtures.booking(this.jdbc, this.sam.user().getId(), provider,
				BookingFixtures.londonTime(this.monday, "11:00"), "CONFIRMED");

		String moved = reschedule(this.asha, id, "11:00").andExpect(status().isOk())
			.andExpect(jsonPath("$.history[1].details", containsString("another provider")))
			.andReturn().getResponse().getContentAsString();
		assertThat((String) JsonPath.read(moved, "$.provider.id")).isNotEqualTo(provider.toString());
	}

	@Test
	void rejectsTimesThatAreNotSlots() throws Exception {
		String id = this.api.holdAndBook(this.asha, this.ashaHome, this.monday, "09:00");

		reschedule(this.asha, id, "09:15").andExpect(status().isConflict());
		rescheduleAt(this.asha, id, BookingFixtures.londonTime(this.monday.plusDays(1), "09:00"))
			.andExpect(status().isConflict());
		reschedule(this.asha, id, "09:00")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.type").value("urn:homehelp:problem:same-time"));
	}

	@Test
	void allowsAtMostTwoReschedules() throws Exception {
		String id = this.api.holdAndBook(this.asha, this.ashaHome, this.monday, "09:00");

		reschedule(this.asha, id, "09:30").andExpect(status().isOk());
		reschedule(this.asha, id, "10:00").andExpect(status().isOk()).andExpect(jsonPath("$.rescheduleCount").value(2));
		reschedule(this.asha, id, "10:30")
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.type").value("urn:homehelp:problem:reschedule-limit-reached"));
	}

	@Test
	void cannotRescheduleWithinTwentyFourHours() throws Exception {
		Instant soon = Instant.now().plus(Duration.ofHours(5)).truncatedTo(java.time.temporal.ChronoUnit.MINUTES);
		UUID id = BookingFixtures.booking(this.jdbc, this.asha.user().getId(), this.ravi.user().getId(), soon,
				"CONFIRMED");

		reschedule(this.asha, id.toString(), "10:00")
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.type").value("urn:homehelp:problem:too-late-to-reschedule"));
	}

	@Test
	void cancelledBookingsCannotBeRescheduled() throws Exception {
		String id = this.api.holdAndBook(this.asha, this.ashaHome, this.monday, "09:00");
		this.api.post(this.asha, "/api/bookings/" + id + "/cancel", null).andExpect(status().isOk());

		reschedule(this.asha, id, "10:00")
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.type").value("urn:homehelp:problem:invalid-booking-transition"));
	}

	@Test
	void onlyTheCustomerCanReschedule() throws Exception {
		String id = this.api.holdAndBook(this.asha, this.ashaHome, this.monday, "09:00");

		reschedule(this.ravi, id, "10:00").andExpect(status().isNotFound());
		reschedule(this.sam, id, "10:00").andExpect(status().isNotFound());
		this.api.post(this.asha, "/api/bookings/" + id + "/reschedule", "{}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].field").value("start"));
	}

	private ResultActions reschedule(LoggedInUser user, String id, String londonTime) throws Exception {
		return rescheduleAt(user, id, BookingFixtures.londonTime(this.monday, londonTime));
	}

	private ResultActions rescheduleAt(LoggedInUser user, String id, Instant start) throws Exception {
		return this.api.post(user, "/api/bookings/" + id + "/reschedule", "{\"start\": \"%s\"}".formatted(start));
	}

}
