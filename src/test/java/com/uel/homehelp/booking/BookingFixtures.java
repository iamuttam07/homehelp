package com.uel.homehelp.booking;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.UUID;

import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import com.uel.homehelp.TestUsers;
import com.uel.homehelp.TestUsers.LoggedInUser;
import com.uel.homehelp.user.Role;

/** Shared setup for booking tests: addresses, approved providers, dates. */
final class BookingFixtures {

	static final ZoneId LONDON = ZoneId.of("Europe/London");

	private BookingFixtures() {
	}

	/** Next Monday at least two days away, so the 2-hour notice never interferes. */
	static LocalDate nextMonday() {
		return LocalDate.now(LONDON).plusDays(2).with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
	}

	static Instant londonTime(LocalDate date, String time) {
		return date.atTime(LocalTime.parse(time)).atZone(LONDON).toInstant();
	}

	static UUID address(JdbcTemplate jdbc, LoggedInUser user, double latitude, double longitude) {
		return jdbc.queryForObject("""
				INSERT INTO addresses (user_id, line1, city, postcode, country_code, latitude, longitude)
				VALUES (?, '1 Test St', 'London', 'E1 1AA', 'GB', ?, ?) RETURNING id""", UUID.class,
				user.user().getId(), latitude, longitude);
	}

	/** An approved provider in Shoreditch (5 km) offering leak-repair, Mondays 09:00-12:00. */
	static LoggedInUser approvedProvider(JdbcTemplate jdbc, TestUsers users, String email) {
		LoggedInUser provider = users.create(email, Role.PROVIDER);
		UUID id = provider.user().getId();
		jdbc.update("""
				UPDATE provider_profiles SET status = 'APPROVED', base_latitude = 51.5202, base_longitude = -0.0716,
				service_radius_km = 5 WHERE user_id = ?""", id);
		jdbc.update("INSERT INTO provider_services (provider_id, service_id) SELECT ?, id FROM services WHERE slug = 'leak-repair'",
				id);
		jdbc.update("""
				INSERT INTO provider_availability (provider_id, day_of_week, start_time, end_time)
				VALUES (?, 1, '09:00', '12:00')""", id);
		return provider;
	}

	/**
	 * Inserts a leak-repair booking directly, so tests can choose any time and status
	 * (including ones the API wouldn't allow to be created, e.g. in an hour).
	 */
	static UUID booking(JdbcTemplate jdbc, UUID customerId, UUID providerId, Instant start, String status) {
		return jdbc.queryForObject("""
				INSERT INTO bookings (customer_id, provider_id, service_id, service_slug, service_name, duration_minutes,
				    price_amount, currency, address_line1, address_city, address_postcode, address_country_code,
				    latitude, longitude, start_at, end_at, status, cancelled_at)
				SELECT ?, ?, id, slug, name, 60, base_price, currency, '1 Test St', 'London', 'E1 1AA', 'GB',
				    51.5202, -0.0716, ?, ?, ?, CASE WHEN ? = 'CANCELLED' THEN now() END
				FROM services WHERE slug = 'leak-repair' RETURNING id""", UUID.class, customerId, providerId,
				java.sql.Timestamp.from(start), java.sql.Timestamp.from(start.plusSeconds(3600)), status, status);
	}

	/** Bookings reference users, so they must go first. */
	static void clearBookingsAndUsers(JdbcTemplate jdbc) {
		jdbc.update("DELETE FROM bookings");
		jdbc.update("DELETE FROM users");
	}

	static void flushRedis(StringRedisTemplate redis) {
		redis.execute((RedisCallback<Object>) (RedisConnection connection) -> {
			connection.serverCommands().flushDb();
			return null;
		});
	}

}
