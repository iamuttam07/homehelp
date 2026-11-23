package com.uel.homehelp.user;

import java.nio.charset.StandardCharsets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.uel.homehelp.common.Strings;

/**
 * Creates the first admin account at startup, since admins can't self-register.
 *
 * <p>Runs only when both {@code HOMEHELP_ADMIN_EMAIL} and {@code HOMEHELP_ADMIN_PASSWORD}
 * are set, and only if no account with that email exists yet. It never changes an
 * existing account. Once the admin exists, the variables can be removed.
 */
@Component
class AdminBootstrap implements ApplicationRunner {

	static final int MIN_PASSWORD_LENGTH = 12;

	private static final Logger logger = LoggerFactory.getLogger(AdminBootstrap.class);

	private final UserRepository users;

	private final PasswordEncoder passwordEncoder;

	private final String email;

	private final String password;

	AdminBootstrap(UserRepository users, PasswordEncoder passwordEncoder,
			@Value("${homehelp.admin.email:}") String email, @Value("${homehelp.admin.password:}") String password) {
		this.users = users;
		this.passwordEncoder = passwordEncoder;
		this.email = Strings.trimToNull(email);
		this.password = (password == null || password.isEmpty()) ? null : password;
	}

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		if (this.email == null && this.password == null) {
			return;
		}
		if (this.email == null || this.password == null) {
			throw new IllegalStateException("Set both HOMEHELP_ADMIN_EMAIL and HOMEHELP_ADMIN_PASSWORD, or neither.");
		}
		int passwordBytes = this.password.getBytes(StandardCharsets.UTF_8).length;
		if (this.password.length() < MIN_PASSWORD_LENGTH || passwordBytes > 72) {
			throw new IllegalStateException(
					"HOMEHELP_ADMIN_PASSWORD must be at least %d characters (and at most 72 bytes)."
						.formatted(MIN_PASSWORD_LENGTH));
		}
		String normalized = Emails.normalize(this.email);
		User existing = this.users.findByEmail(normalized).orElse(null);
		if (existing != null) {
			if (existing.getRole() != Role.ADMIN) {
				logger.warn("Admin bootstrap skipped: {} already exists with role {} and is left unchanged.", normalized,
						existing.getRole());
			}
			return;
		}
		this.users.save(new User(normalized, this.passwordEncoder.encode(this.password), "Administrator", null,
				Role.ADMIN));
		logger.info("Created admin account {}.", normalized);
	}

}
