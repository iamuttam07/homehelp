package com.uel.homehelp;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.jdbc.core.JdbcTemplate;

import com.uel.homehelp.auth.AccessTokenIssuer;
import com.uel.homehelp.user.Role;
import com.uel.homehelp.user.User;
import com.uel.homehelp.user.UserRepository;

/**
 * Creates users directly in the database and returns a real access token for them.
 * Providers also get an empty provider profile, as registration would create.
 * Import it with {@code @Import(TestUsers.class)}.
 */
@TestComponent
public class TestUsers {

	private final UserRepository users;

	private final AccessTokenIssuer accessTokens;

	private final JdbcTemplate jdbc;

	TestUsers(UserRepository users, AccessTokenIssuer accessTokens, JdbcTemplate jdbc) {
		this.users = users;
		this.accessTokens = accessTokens;
		this.jdbc = jdbc;
	}

	public LoggedInUser create(String email, Role role) {
		User user = this.users.save(new User(email, "{noop}unused", "Test User", null, role));
		if (role == Role.PROVIDER) {
			this.jdbc.update("INSERT INTO provider_profiles (user_id) VALUES (?)", user.getId());
		}
		return new LoggedInUser(user, this.accessTokens.issue(user));
	}

	public record LoggedInUser(User user, String accessToken) {

		public String bearer() {
			return "Bearer " + this.accessToken;
		}

	}

}
