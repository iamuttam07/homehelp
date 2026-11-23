package com.uel.homehelp.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.uel.homehelp.TestcontainersConfiguration;

/**
 * Runs {@link AdminBootstrap} directly with different settings, against the real database.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class AdminBootstrapTests {

	private static final String PASSWORD = "a-long-admin-password";

	@Autowired
	private UserRepository users;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private JdbcTemplate jdbc;

	@BeforeEach
	void clearUsers() {
		this.jdbc.update("DELETE FROM bookings");
		this.jdbc.update("DELETE FROM users");
	}

	@Test
	void doesNothingWhenNotConfigured() {
		bootstrap("", "").run(null);

		assertThat(this.users.count()).isZero();
	}

	@Test
	void createsTheAdminOnce() {
		bootstrap("  Admin@Example.com ", PASSWORD).run(null);
		bootstrap("admin@example.com", PASSWORD).run(null);

		User admin = this.users.findByEmail("admin@example.com").orElseThrow();
		assertThat(admin.getRole()).isEqualTo(Role.ADMIN);
		assertThat(admin.getFullName()).isEqualTo("Administrator");
		assertThat(this.passwordEncoder.matches(PASSWORD, admin.getPasswordHash())).isTrue();
		assertThat(this.users.count()).isEqualTo(1);
	}

	@Test
	void neverChangesAnExistingAccount() {
		this.users.save(new User("asha@example.com", "{noop}her-own-password", "Asha", null, Role.CUSTOMER));

		bootstrap("asha@example.com", PASSWORD).run(null);

		User asha = this.users.findByEmail("asha@example.com").orElseThrow();
		assertThat(asha.getRole()).isEqualTo(Role.CUSTOMER);
		assertThat(asha.getPasswordHash()).isEqualTo("{noop}her-own-password");
	}

	@Test
	void refusesHalfAConfiguration() {
		assertThatThrownBy(() -> bootstrap("admin@example.com", "").run(null))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("both");
		assertThatThrownBy(() -> bootstrap("", PASSWORD).run(null)).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void refusesShortPasswords() {
		assertThatThrownBy(() -> bootstrap("admin@example.com", "short").run(null))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("at least 12");
		assertThat(this.users.count()).isZero();
	}

	private AdminBootstrap bootstrap(String email, String password) {
		return new AdminBootstrap(this.users, this.passwordEncoder, email, password);
	}

}
