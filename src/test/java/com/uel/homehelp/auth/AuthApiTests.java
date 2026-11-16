package com.uel.homehelp.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import javax.crypto.spec.SecretKeySpec;

import com.jayway.jsonpath.JsonPath;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.uel.homehelp.TestcontainersConfiguration;
import com.uel.homehelp.user.Role;
import com.uel.homehelp.user.User;
import com.uel.homehelp.user.UserRepository;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
class AuthApiTests {

	private static final String PASSWORD = "correct-horse-1";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserRepository users;

	@Autowired
	private RefreshTokenRepository refreshTokens;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private JwtEncoder jwtEncoder;

	@Autowired
	private JdbcTemplate jdbc;

	private User asha;

	@BeforeEach
	void createUser() {
		this.refreshTokens.deleteAll();
		this.users.deleteAll();
		this.asha = this.users.save(new User("asha@example.com", this.passwordEncoder.encode(PASSWORD),
				"Asha Patel", null, Role.CUSTOMER));
	}

	// --- login ---

	@Test
	void loginReturnsAccessAndRefreshTokens() throws Exception {
		login("Asha@Example.com", PASSWORD)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.accessToken").isNotEmpty())
			.andExpect(jsonPath("$.tokenType").value("Bearer"))
			.andExpect(jsonPath("$.expiresIn").value(900))
			.andExpect(jsonPath("$.refreshToken").isNotEmpty())
			.andExpect(jsonPath("$.refreshExpiresIn").value(14 * 24 * 3600));
	}

	@Test
	void refreshTokenIsStoredOnlyAsHash() throws Exception {
		String refreshToken = field(login("asha@example.com", PASSWORD), "$.refreshToken");

		List<String> stored = this.jdbc.queryForList("SELECT token_hash FROM refresh_tokens", String.class);
		assertThat(stored).containsExactly(RefreshTokenService.hash(refreshToken)).doesNotContain(refreshToken);
	}

	@Test
	void loginWithWrongPasswordIsRejected() throws Exception {
		login("asha@example.com", "wrong-password")
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.title").value("Invalid credentials"));
	}

	@Test
	void loginWithUnknownEmailGivesSameError() throws Exception {
		login("nobody@example.com", PASSWORD)
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.title").value("Invalid credentials"));
	}

	@Test
	void loginWithMissingFieldsIsBadRequest() throws Exception {
		postJson("/api/auth/login", "{\"email\": \"asha@example.com\"}").andExpect(status().isBadRequest());
	}

	// --- access token ---

	@Test
	void accessTokenIdentifiesCurrentUser() throws Exception {
		String accessToken = field(login("asha@example.com", PASSWORD), "$.accessToken");

		this.mockMvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(this.asha.getId().toString()))
			.andExpect(jsonPath("$.email").value("asha@example.com"))
			.andExpect(jsonPath("$.role").value("CUSTOMER"));
	}

	@Test
	void protectedEndpointWithoutTokenIsUnauthorized() throws Exception {
		this.mockMvc.perform(get("/api/users/me")).andExpect(status().isUnauthorized());
	}

	@Test
	void malformedTokenIsUnauthorized() throws Exception {
		this.mockMvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void expiredTokenIsUnauthorized() throws Exception {
		Instant past = Instant.now().minus(2, ChronoUnit.HOURS);
		String token = sign(this.jwtEncoder, claims(past, past.plus(15, ChronoUnit.MINUTES), "homehelp"));

		this.mockMvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void tokenSignedWithAnotherKeyIsUnauthorized() throws Exception {
		JwtEncoder otherKey = new NimbusJwtEncoder(new ImmutableSecret<>(new SecretKeySpec(
				"some-other-secret-that-is-at-least-32-bytes".getBytes(StandardCharsets.UTF_8), "HmacSHA256")));
		Instant now = Instant.now();
		String token = sign(otherKey, claims(now, now.plus(15, ChronoUnit.MINUTES), "homehelp"));

		this.mockMvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void tokenFromAnotherIssuerIsUnauthorized() throws Exception {
		Instant now = Instant.now();
		String token = sign(this.jwtEncoder, claims(now, now.plus(15, ChronoUnit.MINUTES), "someone-else"));

		this.mockMvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
			.andExpect(status().isUnauthorized());
	}

	// --- refresh ---

	@Test
	void refreshIssuesNewTokensAndUsesUpTheOldOne() throws Exception {
		String first = field(login("asha@example.com", PASSWORD), "$.refreshToken");

		ResultActions refreshed = refresh(first).andExpect(status().isOk())
			.andExpect(jsonPath("$.accessToken").isNotEmpty());
		String second = field(refreshed, "$.refreshToken");
		assertThat(second).isNotEqualTo(first);

		refresh(first).andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.title").value("Invalid refresh token"));
	}

	@Test
	void reusingAnOldRefreshTokenRevokesAllSessions() throws Exception {
		String first = field(login("asha@example.com", PASSWORD), "$.refreshToken");
		String second = field(refresh(first), "$.refreshToken");

		// Someone replays the old token: treat it as stolen.
		refresh(first).andExpect(status().isUnauthorized());

		// The newest token is now revoked too.
		refresh(second).andExpect(status().isUnauthorized());
	}

	@Test
	void unknownRefreshTokenIsUnauthorized() throws Exception {
		refresh("made-up-token").andExpect(status().isUnauthorized());
	}

	// --- logout ---

	@Test
	void logoutRevokesRefreshToken() throws Exception {
		String refreshToken = field(login("asha@example.com", PASSWORD), "$.refreshToken");

		postJson("/api/auth/logout", refreshBody(refreshToken)).andExpect(status().isNoContent());

		refresh(refreshToken).andExpect(status().isUnauthorized());
	}

	@Test
	void logoutWithUnknownTokenStillSucceeds() throws Exception {
		postJson("/api/auth/logout", refreshBody("made-up-token")).andExpect(status().isNoContent());
	}

	// --- helpers ---

	private ResultActions login(String email, String password) throws Exception {
		return postJson("/api/auth/login", """
				{"email": "%s", "password": "%s"}
				""".formatted(email, password));
	}

	private ResultActions refresh(String refreshToken) throws Exception {
		return postJson("/api/auth/refresh", refreshBody(refreshToken));
	}

	private static String refreshBody(String refreshToken) {
		return "{\"refreshToken\": \"%s\"}".formatted(refreshToken);
	}

	private ResultActions postJson(String path, String json) throws Exception {
		return this.mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(json));
	}

	private static String field(ResultActions result, String path) throws Exception {
		return JsonPath.read(result.andReturn().getResponse().getContentAsString(), path);
	}

	private JwtClaimsSet claims(Instant issuedAt, Instant expiresAt, String issuer) {
		return JwtClaimsSet.builder()
			.issuer(issuer)
			.subject(this.asha.getId().toString())
			.issuedAt(issuedAt)
			.expiresAt(expiresAt)
			.claim("roles", List.of("CUSTOMER"))
			.build();
	}

	private static String sign(JwtEncoder encoder, JwtClaimsSet claims) {
		return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
			.getTokenValue();
	}

}
