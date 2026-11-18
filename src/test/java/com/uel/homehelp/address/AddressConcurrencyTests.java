package com.uel.homehelp.address;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import com.uel.homehelp.TestUsers;
import com.uel.homehelp.TestcontainersConfiguration;
import com.uel.homehelp.user.Role;

/**
 * Fires many requests for the same user at the same moment and checks that the
 * address rules (one default, at most ten) still hold.
 */
@SpringBootTest
@Import({ TestcontainersConfiguration.class, TestUsers.class })
class AddressConcurrencyTests {

	private static final int THREADS = 8;

	@Autowired
	private AddressService service;

	@Autowired
	private TestUsers testUsers;

	@Autowired
	private JdbcTemplate jdbc;

	private final ExecutorService pool = Executors.newFixedThreadPool(THREADS);

	private UUID userId;

	@BeforeEach
	void createUser() {
		this.jdbc.update("DELETE FROM users");
		this.userId = this.testUsers.create("asha@example.com", Role.CUSTOMER).user().getId();
	}

	@AfterEach
	void stopPool() {
		this.pool.shutdownNow();
	}

	@Test
	void concurrentFirstAddressesProduceExactlyOneDefault() throws Exception {
		List<Outcome> outcomes = runConcurrently(THREADS, (i) -> this.service.create(this.userId, request("Place " + i)));

		assertThat(outcomes).allMatch(Outcome::succeeded);
		assertThat(count("SELECT count(*) FROM addresses WHERE user_id = ?")).isEqualTo(THREADS);
		assertThat(count("SELECT count(*) FROM addresses WHERE user_id = ? AND is_default")).isEqualTo(1);
	}

	@Test
	void concurrentCreatesNeverExceedTheLimit() throws Exception {
		for (int i = 0; i < AddressService.MAX_ADDRESSES_PER_USER - 2; i++) {
			this.service.create(this.userId, request("Existing " + i));
		}

		List<Outcome> outcomes = runConcurrently(THREADS, (i) -> this.service.create(this.userId, request("New " + i)));

		assertThat(outcomes).filteredOn(Outcome::succeeded).hasSize(2);
		assertThat(outcomes).filteredOn((o) -> !o.succeeded())
			.allMatch((o) -> o.error() instanceof AddressLimitReachedException);
		assertThat(count("SELECT count(*) FROM addresses WHERE user_id = ?"))
			.isEqualTo(AddressService.MAX_ADDRESSES_PER_USER);
	}

	@Test
	void concurrentMakeDefaultLeavesExactlyOneDefault() throws Exception {
		List<UUID> ids = new ArrayList<>();
		for (int i = 0; i < THREADS; i++) {
			ids.add(this.service.create(this.userId, request("Place " + i)).getId());
		}

		List<Outcome> outcomes = runConcurrently(THREADS, (i) -> this.service.makeDefault(this.userId, ids.get(i)));

		assertThat(outcomes).allMatch(Outcome::succeeded);
		assertThat(count("SELECT count(*) FROM addresses WHERE user_id = ? AND is_default")).isEqualTo(1);
	}

	// --- helpers ---

	private List<Outcome> runConcurrently(int tasks, Task task) throws Exception {
		CountDownLatch start = new CountDownLatch(1);
		List<Future<Outcome>> futures = new ArrayList<>();
		for (int i = 0; i < tasks; i++) {
			int index = i;
			Callable<Outcome> call = () -> {
				start.await();
				try {
					task.run(index);
					return new Outcome(null);
				}
				catch (Exception ex) {
					return new Outcome(ex);
				}
			};
			futures.add(this.pool.submit(call));
		}
		start.countDown();
		List<Outcome> outcomes = new ArrayList<>();
		for (Future<Outcome> future : futures) {
			outcomes.add(future.get(30, TimeUnit.SECONDS));
		}
		return outcomes;
	}

	private Integer count(String sql) {
		return this.jdbc.queryForObject(sql, Integer.class, this.userId);
	}

	private static AddressRequest request(String label) {
		return new AddressRequest(label, "12 Brick Lane", null, "London", "E1 6AN", "GB", 51.5202, -0.0716);
	}

	@FunctionalInterface
	interface Task {

		void run(int index) throws Exception;

	}

	record Outcome(Exception error) {

		boolean succeeded() {
			return this.error == null;
		}

	}

}
