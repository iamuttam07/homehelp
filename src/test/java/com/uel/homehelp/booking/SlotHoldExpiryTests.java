package com.uel.homehelp.booking;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import com.uel.homehelp.TestUsers;
import com.uel.homehelp.TestUsers.LoggedInUser;
import com.uel.homehelp.TestcontainersConfiguration;
import com.uel.homehelp.booking.SlotHoldDtos.HoldRequest;
import com.uel.homehelp.common.web.ResourceNotFoundException;
import com.uel.homehelp.user.Role;

/** Uses a one-second hold so expiry can be observed for real. */
@SpringBootTest(properties = "homehelp.booking.hold-ttl=1s")
@Import({ TestcontainersConfiguration.class, TestUsers.class })
class SlotHoldExpiryTests {

	@Autowired
	private SlotHoldService holds;

	@Autowired
	private TestUsers testUsers;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private StringRedisTemplate redis;

	@Test
	void holdExpiresOnItsOwnAndTheSlotIsFreeAgain() throws Exception {
		BookingFixtures.flushRedis(this.redis);
		this.jdbc.update("DELETE FROM users");
		LoggedInUser asha = this.testUsers.create("asha@example.com", Role.CUSTOMER);
		LoggedInUser sam = this.testUsers.create("sam@example.com", Role.CUSTOMER);
		UUID ashaHome = BookingFixtures.address(this.jdbc, asha, 51.5202, -0.0716);
		UUID samHome = BookingFixtures.address(this.jdbc, sam, 51.5202, -0.0716);
		BookingFixtures.approvedProvider(this.jdbc, this.testUsers, "ravi@example.com");
		OffsetDateTime nine = BookingFixtures.londonTime(BookingFixtures.nextMonday(), "09:00")
			.atZone(BookingFixtures.LONDON)
			.toOffsetDateTime();

		this.holds.hold(asha.user().getId(), new HoldRequest("leak-repair", ashaHome, nine, null));
		Thread.sleep(1500);

		assertThat(this.redis.keys("homehelp:hold:*")).isEmpty();
		org.assertj.core.api.Assertions.assertThatThrownBy(() -> this.holds.current(asha.user().getId()))
			.isInstanceOf(ResourceNotFoundException.class);
		assertThat(this.holds.hold(sam.user().getId(), new HoldRequest("leak-repair", samHome, nine, null)).start())
			.isEqualTo(nine.toInstant());
	}

}
