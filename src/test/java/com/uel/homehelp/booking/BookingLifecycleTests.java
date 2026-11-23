package com.uel.homehelp.booking;

import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import com.uel.homehelp.TestUsers;
import com.uel.homehelp.TestUsers.LoggedInUser;
import com.uel.homehelp.TestcontainersConfiguration;
import com.uel.homehelp.user.Role;

/**
 * Steps 18 and 19: starting, completing, and cancelling. Bookings are inserted at
 * chosen times relative to now, so the time-based rules can be tested.
 */
@SpringBootTest
@Import({ TestcontainersConfiguration.class, TestUsers.class })
@AutoConfigureMockMvc
class BookingLifecycleTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private TestUsers testUsers;

	@Autowired
	private JdbcTemplate jdbc;

	private BookingTestSupport api;

	private LoggedInUser asha;

	private LoggedInUser ravi;

	private LoggedInUser admin;

	@BeforeEach
	void setUp() {
		this.api = new BookingTestSupport(this.mockMvc);
		BookingFixtures.clearBookingsAndUsers(this.jdbc);
		this.asha = this.testUsers.create("asha@example.com", Role.CUSTOMER);
		this.ravi = BookingFixtures.approvedProvider(this.jdbc, this.testUsers, "ravi@example.com");
		this.admin = this.testUsers.create("admin@example.com", Role.ADMIN);
	}

	@AfterEach
	void cleanUp() {
		BookingFixtures.clearBookingsAndUsers(this.jdbc);
	}

	// --- start and complete ---

	@Test
	void providerStartsAndCompletesAJob() throws Exception {
		String path = job(in(Duration.ofMinutes(20)), "CONFIRMED");

		this.api.post(this.ravi, path + "/start", null).andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("IN_PROGRESS"));
		this.api.post(this.ravi, path + "/complete", null).andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("COMPLETED"))
			.andExpect(jsonPath("$.history[*].type").value(contains("STARTED", "COMPLETED")))
			.andExpect(jsonPath("$.history[1].from").value("IN_PROGRESS"))
			.andExpect(jsonPath("$.history[1].byRole").value("PROVIDER"));
	}

	@Test
	void cannotStartMoreThanThirtyMinutesEarly() throws Exception {
		this.api.post(this.ravi, job(in(Duration.ofHours(2)), "CONFIRMED") + "/start", null)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.type").value("urn:homehelp:problem:too-early-to-start"));
	}

	@Test
	void cannotCompleteAJobThatHasNotStarted() throws Exception {
		this.api.post(this.ravi, job(in(Duration.ofMinutes(20)), "CONFIRMED") + "/complete", null)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.type").value("urn:homehelp:problem:invalid-booking-transition"))
			.andExpect(jsonPath("$.currentStatus").value("CONFIRMED"));
	}

	@Test
	void completedAndCancelledBookingsAreFinal() throws Exception {
		String completed = job(in(Duration.ofDays(-1)), "COMPLETED");
		String cancelled = job(in(Duration.ofDays(3)), "CANCELLED");

		this.api.post(this.ravi, completed + "/start", null).andExpect(status().isConflict());
		this.api.post(this.admin, "/api/admin/bookings/" + id(completed) + "/cancel", "{\"reason\": \"x\"}")
			.andExpect(status().isConflict());
		this.api.post(this.asha, "/api/bookings/" + id(cancelled) + "/cancel", null)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.currentStatus").value("CANCELLED"));
	}

	@Test
	void otherProvidersCannotTouchTheJob() throws Exception {
		LoggedInUser lee = BookingFixtures.approvedProvider(this.jdbc, this.testUsers, "lee@example.com");
		String path = job(in(Duration.ofMinutes(20)), "CONFIRMED");

		this.api.post(lee, path + "/start", null).andExpect(status().isNotFound());
		this.api.post(this.asha, path + "/start", null).andExpect(status().isForbidden());
	}

	// --- customer cancellation ---

	@Test
	void customerCancelsWellAhead() throws Exception {
		String id = id(job(in(Duration.ofDays(3)), "CONFIRMED"));

		this.api.post(this.asha, "/api/bookings/" + id + "/cancel", "{\"reason\": \"Fixed it myself\"}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("CANCELLED"))
			.andExpect(jsonPath("$.cancellation.byRole").value("CUSTOMER"))
			.andExpect(jsonPath("$.cancellation.reason").value("Fixed it myself"))
			.andExpect(jsonPath("$.cancellation.late").value(false))
			.andExpect(jsonPath("$.history[0].type").value("CANCELLED"));
	}

	@Test
	void cancellingWithinTwentyFourHoursIsLate() throws Exception {
		String id = id(job(in(Duration.ofHours(5)), "CONFIRMED"));

		this.api.post(this.asha, "/api/bookings/" + id + "/cancel", null)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.cancellation.late").value(true))
			.andExpect(jsonPath("$.cancellation.reason").doesNotExist())
			.andExpect(jsonPath("$.history[0].details").value("Late cancellation"));
	}

	@Test
	void customerCannotCancelOnceTheStartTimeHasPassed() throws Exception {
		String id = id(job(in(Duration.ofMinutes(-10)), "CONFIRMED"));

		this.api.post(this.asha, "/api/bookings/" + id + "/cancel", null)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.type").value("urn:homehelp:problem:booking-already-started"));
	}

	@Test
	void customerCannotCancelAJobInProgress() throws Exception {
		String id = id(job(in(Duration.ofMinutes(-10)), "IN_PROGRESS"));

		this.api.post(this.asha, "/api/bookings/" + id + "/cancel", null)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.currentStatus").value("IN_PROGRESS"));
	}

	@Test
	void cancellingFreesTheProvidersTime() throws Exception {
		Instant start = in(Duration.ofDays(3));
		String id = id(job(start, "CONFIRMED"));

		this.api.post(this.asha, "/api/bookings/" + id + "/cancel", null).andExpect(status().isOk());

		BookingFixtures.booking(this.jdbc, this.asha.user().getId(), this.ravi.user().getId(), start, "CONFIRMED");
	}

	// --- provider and admin cancellation ---

	@Test
	void providerMustGiveAReason() throws Exception {
		String path = job(in(Duration.ofDays(3)), "CONFIRMED");

		this.api.post(this.ravi, path + "/cancel", "{}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].field").value("reason"));
		this.api.post(this.ravi, path + "/cancel", "{\"reason\": \"Van broke down\"}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.cancellation.byRole").value("PROVIDER"))
			.andExpect(jsonPath("$.cancellation.late").value(false));
	}

	@Test
	void providerCannotCancelAJobInProgress() throws Exception {
		this.api.post(this.ravi, job(in(Duration.ofMinutes(-10)), "IN_PROGRESS") + "/cancel", "{\"reason\": \"x\"}")
			.andExpect(status().isConflict());
	}

	@Test
	void adminCanCancelAJobInProgress() throws Exception {
		String id = id(job(in(Duration.ofMinutes(-10)), "IN_PROGRESS"));

		this.api.post(this.admin, "/api/admin/bookings/" + id + "/cancel", "{\"reason\": \"Safety issue reported\"}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("CANCELLED"))
			.andExpect(jsonPath("$.cancellation.byRole").value("ADMIN"));
		this.api.post(this.asha, "/api/admin/bookings/" + id + "/cancel", "{\"reason\": \"x\"}")
			.andExpect(status().isForbidden());
	}

	// --- helpers ---

	private static Instant in(Duration fromNow) {
		return Instant.now().plus(fromNow).truncatedTo(java.time.temporal.ChronoUnit.MINUTES);
	}

	/** Inserts a booking for Asha with Ravi and returns its provider path. */
	private String job(Instant start, String status) {
		UUID id = BookingFixtures.booking(this.jdbc, this.asha.user().getId(), this.ravi.user().getId(), start, status);
		return "/api/provider/bookings/" + id;
	}

	private static String id(String providerPath) {
		return providerPath.substring(providerPath.lastIndexOf('/') + 1);
	}

}
