package com.uel.homehelp.booking;

import static com.uel.homehelp.booking.BookingFixtures.LONDON;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import com.uel.homehelp.TestUsers;
import com.uel.homehelp.TestUsers.LoggedInUser;
import com.uel.homehelp.TestcontainersConfiguration;
import com.uel.homehelp.booking.SlotHoldDtos.HoldRequest;
import com.uel.homehelp.user.Role;

@SpringBootTest
@Import({ TestcontainersConfiguration.class, TestUsers.class })
@AutoConfigureMockMvc
class SlotHoldApiTests {

	private static final String HOLDS = "/api/slot-holds";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private TestUsers testUsers;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private StringRedisTemplate redis;

	@Autowired
	private SlotHoldService holdService;

	private LocalDate monday;

	private LoggedInUser asha;

	private UUID ashaHome;

	private LoggedInUser sam;

	private UUID samHome;

	@BeforeEach
	void setUp() {
		BookingFixtures.flushRedis(this.redis);
		this.jdbc.update("DELETE FROM users");
		this.monday = BookingFixtures.nextMonday();
		this.asha = this.testUsers.create("asha@example.com", Role.CUSTOMER);
		this.ashaHome = BookingFixtures.address(this.jdbc, this.asha, 51.5202, -0.0716);
		this.sam = this.testUsers.create("sam@example.com", Role.CUSTOMER);
		this.samHome = BookingFixtures.address(this.jdbc, this.sam, 51.5150, -0.0700);
		BookingFixtures.approvedProvider(this.jdbc, this.testUsers, "ravi@example.com");
	}

	@Test
	void holdsASlot() throws Exception {
		Instant beforeRequest = Instant.now();

		String body = hold(this.asha, this.ashaHome, "09:00")
			.andExpect(status().isCreated())
			.andExpect(header().string(HttpHeaders.LOCATION, startsWith(HOLDS + "/")))
			.andExpect(jsonPath("$.holdId").isNotEmpty())
			.andExpect(jsonPath("$.service.slug").value("leak-repair"))
			.andExpect(jsonPath("$.addressId").value(this.ashaHome.toString()))
			.andExpect(jsonPath("$.providerId").doesNotExist())
			.andReturn().getResponse().getContentAsString();

		assertThat(OffsetDateTime.parse(JsonPath.read(body, "$.start")).toInstant())
			.isEqualTo(BookingFixtures.londonTime(this.monday, "09:00"));
		assertThat(OffsetDateTime.parse(JsonPath.read(body, "$.end")).toInstant())
			.isEqualTo(BookingFixtures.londonTime(this.monday, "10:00"));
		Instant expiresAt = Instant.parse(JsonPath.read(body, "$.expiresAt"));
		assertThat(expiresAt).isBetween(beforeRequest.plus(Duration.ofMinutes(10)).minusSeconds(5),
				Instant.now().plus(Duration.ofMinutes(10)));
	}

	@Test
	void everyHoldKeyExpiresInTenMinutes() throws Exception {
		String holdId = JsonPath.read(hold(this.asha, this.ashaHome, "09:00").andReturn().getResponse()
			.getContentAsString(), "$.holdId");

		List<String> keys = new ArrayList<>(this.redis.keys("homehelp:hold:*"));
		assertThat(keys).contains("homehelp:hold:" + holdId, "homehelp:hold:user:" + this.asha.user().getId());
		assertThat(keys).filteredOn((k) -> k.contains(":cell:")).hasSize(4);
		for (String key : keys) {
			assertThat(this.redis.getExpire(key, TimeUnit.SECONDS)).as(key).isBetween(590L, 600L);
		}
	}

	@Test
	void heldTimeDisappearsForOthersButNotForTheHolder() throws Exception {
		hold(this.asha, this.ashaHome, "09:00").andExpect(status().isCreated());

		// 09:00 and 09:30 both overlap the held 09:00-10:00.
		assertThat(slotTimes(this.sam, this.samHome)).containsExactly("10:00", "10:30", "11:00");
		assertThat(slotTimes(this.asha, this.ashaHome)).containsExactly("09:00", "09:30", "10:00", "10:30", "11:00");
	}

	@Test
	void anotherProviderCanStillTakeTheSameTime() throws Exception {
		BookingFixtures.approvedProvider(this.jdbc, this.testUsers, "lee@example.com");
		hold(this.asha, this.ashaHome, "09:00");

		String body = slots(this.sam, this.samHome).andReturn().getResponse().getContentAsString();
		List<Integer> counts = JsonPath.read(body, "$.slots[*].availableProviders");
		assertThat(counts).containsExactly(1, 1, 2, 2, 2);

		hold(this.sam, this.samHome, "09:00").andExpect(status().isCreated());
		hold(this.testCustomer("kim@example.com"), null, "09:00").andExpect(status().isConflict());
	}

	@Test
	void cannotHoldATimeSomeoneElseHolds() throws Exception {
		hold(this.asha, this.ashaHome, "09:00");

		hold(this.sam, this.samHome, "09:30")
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.type").value("urn:homehelp:problem:slot-unavailable"));
		hold(this.sam, this.samHome, "10:00").andExpect(status().isCreated());
	}

	@Test
	void holdingAgainReplacesTheOldHold() throws Exception {
		hold(this.asha, this.ashaHome, "09:00");

		// Overlaps her own hold: allowed, and the old hold goes away.
		String second = JsonPath.read(hold(this.asha, this.ashaHome, "09:30").andExpect(status().isCreated())
			.andReturn().getResponse().getContentAsString(), "$.holdId");

		current(this.asha).andExpect(status().isOk()).andExpect(jsonPath("$.holdId").value(second));
		assertThat(slotTimes(this.sam, this.samHome)).containsExactly("10:30", "11:00");

		hold(this.asha, this.ashaHome, "11:00");
		assertThat(slotTimes(this.sam, this.samHome)).containsExactly("09:00", "09:30", "10:00");
		assertThat(this.redis.keys("homehelp:hold:*")).filteredOn((k) -> !k.contains(":cell:")).hasSize(2);
	}

	@Test
	void releasingFreesTheSlotStraightAway() throws Exception {
		String holdId = JsonPath.read(hold(this.asha, this.ashaHome, "09:00").andReturn().getResponse()
			.getContentAsString(), "$.holdId");

		release(this.asha, holdId).andExpect(status().isNoContent());

		current(this.asha).andExpect(status().isNotFound())
			.andExpect(jsonPath("$.type").value("urn:homehelp:problem:slot-hold-not-found"));
		assertThat(slotTimes(this.sam, this.samHome)).hasSize(5);
		assertThat(this.redis.keys("homehelp:hold:*")).isEmpty();
	}

	@Test
	void cannotReleaseSomeoneElsesHold() throws Exception {
		String holdId = JsonPath.read(hold(this.asha, this.ashaHome, "09:00").andReturn().getResponse()
			.getContentAsString(), "$.holdId");

		release(this.sam, holdId).andExpect(status().isNotFound());
		current(this.asha).andExpect(status().isOk());
	}

	@Test
	void currentIsNotFoundWithoutAHold() throws Exception {
		current(this.asha).andExpect(status().isNotFound());
	}

	@Test
	void rejectsTimesThatAreNotSlots() throws Exception {
		hold(this.asha, this.ashaHome, "09:15").andExpect(status().isConflict());
		hold(this.asha, this.ashaHome, "11:30").andExpect(status().isConflict());
		holdAt(this.asha, this.ashaHome, BookingFixtures.londonTime(this.monday.plusDays(1), "09:00"))
			.andExpect(status().isConflict());
		holdAt(this.asha, this.ashaHome, Instant.now().plus(Duration.ofMinutes(30))).andExpect(status().isConflict());
		holdAt(this.asha, this.ashaHome, BookingFixtures.londonTime(this.monday.plusDays(35), "09:00"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.type").value("urn:homehelp:problem:invalid-date"));
		assertThat(this.redis.keys("homehelp:hold:*")).isEmpty();
	}

	@Test
	void rejectsUnknownServiceAndOtherUsersAddress() throws Exception {
		post(this.asha, """
				{"service": "time-travel", "addressId": "%s", "start": "%s"}
				""".formatted(this.ashaHome, BookingFixtures.londonTime(this.monday, "09:00")))
			.andExpect(status().isNotFound());
		hold(this.asha, this.samHome, "09:00")
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.type").value("urn:homehelp:problem:address-not-found"));
	}

	@Test
	void rejectsInvalidRequests() throws Exception {
		post(this.asha, "{\"service\": \"leak-repair\", \"addressId\": \"%s\"}".formatted(this.ashaHome))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].field").value("start"));
		post(this.asha, """
				{"service": "leak-repair", "addressId": "%s", "start": "%s", "timeZone": "+01:00"}
				""".formatted(this.ashaHome, BookingFixtures.londonTime(this.monday, "09:00")))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.type").value("urn:homehelp:problem:invalid-time-zone"));
	}

	@Test
	void needsLogin() throws Exception {
		this.mockMvc.perform(MockMvcRequestBuilders.get(HOLDS + "/current")).andExpect(status().isUnauthorized());
	}

	@Test
	void onlyOneOfManySimultaneousHoldsWins() throws Exception {
		int customers = 6;
		List<UUID> ids = new ArrayList<>();
		List<UUID> homes = new ArrayList<>();
		for (int i = 0; i < customers; i++) {
			LoggedInUser customer = this.testUsers.create("racer" + i + "@example.com", Role.CUSTOMER);
			ids.add(customer.user().getId());
			homes.add(BookingFixtures.address(this.jdbc, customer, 51.5202, -0.0716));
		}
		OffsetDateTime start = BookingFixtures.londonTime(this.monday, "09:00").atZone(LONDON).toOffsetDateTime();
		ExecutorService pool = Executors.newFixedThreadPool(customers);
		CountDownLatch go = new CountDownLatch(1);
		List<Future<Boolean>> results = new ArrayList<>();
		for (int i = 0; i < customers; i++) {
			int n = i;
			results.add(pool.submit(() -> {
				go.await();
				try {
					this.holdService.hold(ids.get(n), new HoldRequest("leak-repair", homes.get(n), start, null));
					return true;
				}
				catch (SlotUnavailableException ex) {
					return false;
				}
			}));
		}
		go.countDown();
		int winners = 0;
		for (Future<Boolean> result : results) {
			winners += result.get(30, TimeUnit.SECONDS) ? 1 : 0;
		}
		pool.shutdown();

		assertThat(winners).isEqualTo(1);
		assertThat(this.redis.keys("homehelp:hold:*")).filteredOn((k) -> k.contains(":cell:")).hasSize(4);
	}

	// --- helpers ---

	private LoggedInUser testCustomer(String email) {
		LoggedInUser customer = this.testUsers.create(email, Role.CUSTOMER);
		this.lastAddress = BookingFixtures.address(this.jdbc, customer, 51.5202, -0.0716);
		return customer;
	}

	private UUID lastAddress;

	private ResultActions hold(LoggedInUser user, UUID addressId, String londonTime) throws Exception {
		return holdAt(user, addressId, BookingFixtures.londonTime(this.monday, londonTime));
	}

	private ResultActions holdAt(LoggedInUser user, UUID addressId, Instant start) throws Exception {
		UUID address = (addressId != null) ? addressId : this.lastAddress;
		return post(user, "{\"service\": \"leak-repair\", \"addressId\": \"%s\", \"start\": \"%s\"}".formatted(address,
				start));
	}

	private ResultActions post(LoggedInUser user, String json) throws Exception {
		return this.mockMvc.perform(MockMvcRequestBuilders.post(HOLDS)
			.header(HttpHeaders.AUTHORIZATION, user.bearer())
			.contentType(MediaType.APPLICATION_JSON)
			.content(json));
	}

	private ResultActions current(LoggedInUser user) throws Exception {
		return this.mockMvc
			.perform(MockMvcRequestBuilders.get(HOLDS + "/current").header(HttpHeaders.AUTHORIZATION, user.bearer()));
	}

	private ResultActions release(LoggedInUser user, String holdId) throws Exception {
		return this.mockMvc.perform(delete(HOLDS + "/" + holdId).header(HttpHeaders.AUTHORIZATION, user.bearer()));
	}

	private ResultActions slots(LoggedInUser user, UUID addressId) throws Exception {
		return this.mockMvc.perform(MockMvcRequestBuilders
			.get("/api/slots?service=leak-repair&date=%s&addressId=%s".formatted(this.monday, addressId))
			.header(HttpHeaders.AUTHORIZATION, user.bearer()));
	}

	private List<String> slotTimes(LoggedInUser user, UUID addressId) throws Exception {
		List<String> starts = JsonPath.read(slots(user, addressId).andReturn().getResponse().getContentAsString(),
				"$.slots[*].start");
		return starts.stream()
			.map((s) -> OffsetDateTime.parse(s).atZoneSameInstant(LONDON).toLocalTime().toString())
			.toList();
	}

}
