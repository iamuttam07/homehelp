package com.uel.homehelp.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.uel.homehelp.TestcontainersConfiguration;
import com.uel.homehelp.user.Role;
import com.uel.homehelp.user.User;
import com.uel.homehelp.user.UserRepository;

/**
 * Checks the role rules in {@link SecurityConfig} against test-only endpoints,
 * using real access tokens.
 */
@SpringBootTest
@Import({ TestcontainersConfiguration.class, RoleAccessTests.ProtectedEndpoints.class })
@AutoConfigureMockMvc
class RoleAccessTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserRepository users;

	@Autowired
	private RefreshTokenRepository refreshTokens;

	@Autowired
	private AccessTokenIssuer accessTokens;

	private String customer;

	private String provider;

	private String admin;

	@BeforeEach
	void createUsers() {
		this.refreshTokens.deleteAll();
		this.users.deleteAll();
		this.customer = tokenFor("customer@example.com", Role.CUSTOMER);
		this.provider = tokenFor("provider@example.com", Role.PROVIDER);
		this.admin = tokenFor("admin@example.com", Role.ADMIN);
	}

	@Test
	void adminPathsAreForAdminsOnly() throws Exception {
		call("/api/admin/ping", this.admin).andExpect(status().isOk());
		call("/api/admin/ping", this.provider).andExpect(status().isForbidden());
		call("/api/admin/ping", this.customer).andExpect(status().isForbidden());
		call("/api/admin/ping", null).andExpect(status().isUnauthorized());
	}

	@Test
	void providerPathsAreForProvidersOnly() throws Exception {
		call("/api/provider/ping", this.provider).andExpect(status().isOk());
		call("/api/provider/ping", this.customer).andExpect(status().isForbidden());
		call("/api/provider/ping", this.admin).andExpect(status().isForbidden());
	}

	@Test
	void otherApiPathsNeedAnyLoggedInUser() throws Exception {
		call("/api/test/ping", this.customer).andExpect(status().isOk());
		call("/api/test/ping", this.provider).andExpect(status().isOk());
		call("/api/test/ping", null).andExpect(status().isUnauthorized());
	}

	@Test
	void preAuthorizeOnMethodsIsEnforced() throws Exception {
		call("/api/test/customers-only", this.customer).andExpect(status().isOk());
		call("/api/test/customers-only", this.provider).andExpect(status().isForbidden());
	}

	@Test
	void healthStaysPublic() throws Exception {
		call("/actuator/health", null).andExpect(status().isOk());
	}

	private String tokenFor(String email, Role role) {
		return this.accessTokens.issue(this.users.save(new User(email, "{noop}unused", "Test User", null, role)));
	}

	private ResultActions call(String path, String token) throws Exception {
		return (token != null)
				? this.mockMvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				: this.mockMvc.perform(get(path));
	}

	@RestController
	static class ProtectedEndpoints {

		@GetMapping("/api/admin/ping")
		String admin() {
			return "admin";
		}

		@GetMapping("/api/provider/ping")
		String provider() {
			return "provider";
		}

		@GetMapping("/api/test/ping")
		String anyUser() {
			return "ok";
		}

		@GetMapping("/api/test/customers-only")
		@PreAuthorize("hasRole('CUSTOMER')")
		String customersOnly() {
			return "customer";
		}

	}

}
