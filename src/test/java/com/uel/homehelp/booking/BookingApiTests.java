package com.uel.homehelp.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import com.uel.homehelp.TestUsers;
import com.uel.homehelp.TestUsers.LoggedInUser;
import com.uel.homehelp.TestcontainersConfiguration;
import com.uel.homehelp.user.Role;

/** Step 18: creating bookings from holds, reading them, and the no-overlap guarantee. */
@SpringBootTest
@Import({ TestcontainersConfiguration.class, TestUsers.class })
@AutoConfigureMockMvc
class BookingApiTests {

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
		this.jdbc.update("UPDATE services SET base_price = 75.00 WHERE slug = 'leak-repair'");
		BookingFixtures.clearBookingsAndUsers(this.jdbc);
	}

	@Test
	void booksAHeldSlot() throws Exception {
		String holdId = this.api.holdId(this.asha, this.ashaHome, this.monday, "09:00");

		String body = this.api.post(this.asha, "/api/bookings",
				"{\"holdId\": \"%s\", \"notes\": \"  Leak under the sink.  \"}".formatted(holdId))
			.andExpect(status().isCreated())
			.andExpect(header().string("Location", startsWith("/api/bookings/")))
			.andExpect(jsonPath("$.status").value("CONFIRMED"))
			.andExpect(jsonPath("$.service.slug").value("leak-repair"))
			.andExpect(jsonPath("$.price.amount").value(75.0))
			.andExpect(jsonPath("$.price.currency").value("GBP"))
			.andExpect(jsonPath("$.address.line1").value("1 Test St"))
			.andExpect(jsonPath("$.customer.id").value(this.asha.user().getId().toString()))
			.andExpect(jsonPath("$.provider.id").value(this.ravi.user().getId().toString()))
			.andExpect(jsonPath("$.provider.phone").doesNotExist())
			.andExpect(jsonPath("$.notes").value("Leak under the sink."))
			.andExpect(jsonPath("$.rescheduleCount").value(0))
			.andExpect(jsonPath("$.cancellation").doesNotExist())
			.andExpect(jsonPath("$.history", hasSize(1)))
			.andExpect(jsonPath("$.history[0].type").value("CREATED"))
			.andExpect(jsonPath("$.history[0].to").value("CONFIRMED"))
			.andExpect(jsonPath("$.history[0].byRole").value("CUSTOMER"))
			.andReturn().getResponse().getContentAsString();

		assertThat(BookingTestSupport.londonTimeOf(body, "$.start")).isEqualTo("09:00");
		assertThat(BookingTestSupport.londonTimeOf(body, "$.end")).isEqualTo("10:00");
		this.api.get(this.asha, "/api/slot-holds/current").andExpect(status().isNotFound());
		assertThat(this.redis.keys("homehelp:hold:*")).isEmpty();
	}

	@Test
	void bookedTimeIsNoLongerOffered() throws Exception {
		this.api.holdAndBook(this.asha, this.ashaHome, this.monday, "09:00");

		assertThat(this.api.slotTimes(this.sam, this.samHome, this.monday)).containsExactly("10:00", "10:30", "11:00");
		assertThat(this.api.slotTimes(this.asha, this.ashaHome, this.monday)).containsExactly("10:00", "10:30", "11:00");
	}

	@Test
	void aHoldCanOnlyBeUsedByItsOwnerAndOnlyOnce() throws Exception {
		String holdId = this.api.holdId(this.asha, this.ashaHome, this.monday, "09:00");

		this.api.book(this.sam, holdId)
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.type").value("urn:homehelp:problem:slot-hold-not-found"));
		this.api.book(this.asha, holdId).andExpect(status().isCreated());
		this.api.book(this.asha, holdId).andExpect(status().isNotFound());
		this.api.book(this.asha, UUID.randomUUID().toString()).andExpect(status().isNotFound());
	}

	@Test
	void providerWhoIsNoLongerApprovedCannotBeBooked() throws Exception {
		String holdId = this.api.holdId(this.asha, this.ashaHome, this.monday, "09:00");
		this.jdbc.update("UPDATE provider_profiles SET status = 'PENDING_REVIEW' WHERE user_id = ?",
				this.ravi.user().getId());

		this.api.book(this.asha, holdId)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.type").value("urn:homehelp:problem:slot-unavailable"));
		assertThat(this.jdbc.queryForObject("SELECT count(*) FROM bookings", Integer.class)).isZero();
	}

	@Test
	void keepsACopyOfServicePriceAndAddress() throws Exception {
		String id = this.api.holdAndBook(this.asha, this.ashaHome, this.monday, "09:00");

		this.jdbc.update("UPDATE services SET base_price = 99.00 WHERE slug = 'leak-repair'");
		this.mockMvc.perform(MockMvcRequestBuilders.delete("/api/users/me/addresses/" + this.ashaHome)
			.header("Authorization", this.asha.bearer())).andExpect(status().isNoContent());

		this.api.get(this.asha, "/api/bookings/" + id)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.price.amount").value(75.0))
			.andExpect(jsonPath("$.address.line1").value("1 Test St"));
	}

	@Test
	void databaseRejectsOverlappingActiveBookingsForAProvider() {
		UUID provider = this.ravi.user().getId();
		var nine = BookingFixtures.londonTime(this.monday, "09:00");
		BookingFixtures.booking(this.jdbc, this.asha.user().getId(), provider, nine, "CONFIRMED");

		assertThatThrownBy(() -> BookingFixtures.booking(this.jdbc, this.sam.user().getId(), provider,
				nine.plusSeconds(1800), "CONFIRMED"))
			.isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("ex_bookings_provider_overlap");
		// Back-to-back is fine, and cancelled bookings don't block anything.
		BookingFixtures.booking(this.jdbc, this.sam.user().getId(), provider, nine.plusSeconds(3600), "CONFIRMED");
		BookingFixtures.booking(this.jdbc, this.sam.user().getId(), provider, nine.plusSeconds(1800), "CANCELLED");
	}

	@Test
	void onlyThePeopleOnABookingAndAdminsCanSeeIt() throws Exception {
		String path = "/api/bookings/" + this.api.holdAndBook(this.asha, this.ashaHome, this.monday, "09:00");
		LoggedInUser admin = this.testUsers.create("admin@example.com", Role.ADMIN);

		this.api.get(this.asha, path).andExpect(status().isOk());
		this.api.get(this.ravi, path).andExpect(status().isOk()).andExpect(jsonPath("$.customer.fullName").value("Test User"));
		this.api.get(admin, path).andExpect(status().isOk());
		this.api.get(this.sam, path)
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.type").value("urn:homehelp:problem:booking-not-found"));
	}

	@Test
	void customersAndProvidersListTheirOwnBookings() throws Exception {
		this.api.holdAndBook(this.asha, this.ashaHome, this.monday, "10:00");
		this.api.holdAndBook(this.sam, this.samHome, this.monday, "09:00");

		this.api.get(this.asha, "/api/bookings")
			.andExpect(jsonPath("$.items", hasSize(1)))
			.andExpect(jsonPath("$.items[0].history").doesNotExist())
			.andExpect(jsonPath("$.totalItems").value(1));
		this.api.get(this.asha, "/api/bookings?status=CANCELLED").andExpect(jsonPath("$.items", hasSize(0)));
		String jobs = this.api.get(this.ravi, "/api/provider/bookings")
			.andExpect(jsonPath("$.items", hasSize(2)))
			.andReturn().getResponse().getContentAsString();
		assertThat(BookingTestSupport.londonTimeOf(jobs, "$.items[0].start")).isEqualTo("09:00");
		this.api.get(this.asha, "/api/provider/bookings").andExpect(status().isForbidden());
	}

	@Test
	void rejectsInvalidRequests() throws Exception {
		this.api.post(this.asha, "/api/bookings", "{}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].field").value("holdId"));
		this.mockMvc.perform(MockMvcRequestBuilders.get("/api/bookings")).andExpect(status().isUnauthorized());
	}

}
