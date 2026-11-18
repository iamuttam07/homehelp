package com.uel.homehelp.provider;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.uel.homehelp.TestUsers;
import com.uel.homehelp.TestcontainersConfiguration;
import com.uel.homehelp.user.Role;

@SpringBootTest
@Import({ TestcontainersConfiguration.class, TestUsers.class })
@AutoConfigureMockMvc
class ProviderProfileApiTests {

	private static final String PROFILE = "/api/provider/profile";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private TestUsers testUsers;

	@Autowired
	private JdbcTemplate jdbc;

	@BeforeEach
	void clearUsers() {
		this.jdbc.update("DELETE FROM users");
	}

	@Test
	void registeringAsProviderCreatesEmptyProfile() throws Exception {
		String bearer = registerAndLogin("ravi@example.com", "PROVIDER");

		this.mockMvc.perform(get(PROFILE).header(HttpHeaders.AUTHORIZATION, bearer))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.userId").isNotEmpty())
			.andExpect(jsonPath("$.bio").doesNotExist())
			.andExpect(jsonPath("$.yearsOfExperience").doesNotExist());
	}

	@Test
	void registeringAsCustomerCreatesNoProviderProfile() throws Exception {
		registerAndLogin("asha@example.com", "CUSTOMER");

		Integer profiles = this.jdbc.queryForObject("SELECT count(*) FROM provider_profiles", Integer.class);
		org.assertj.core.api.Assertions.assertThat(profiles).isZero();
	}

	@Test
	void providerCanUpdateProfile() throws Exception {
		String bearer = registerAndLogin("ravi@example.com", "PROVIDER");

		update(bearer, """
				{"bio": "  Licensed plumber, boilers and leaks.  ", "yearsOfExperience": 12}
				""")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.bio").value("Licensed plumber, boilers and leaks."))
			.andExpect(jsonPath("$.yearsOfExperience").value(12));

		this.mockMvc.perform(get(PROFILE).header(HttpHeaders.AUTHORIZATION, bearer))
			.andExpect(jsonPath("$.yearsOfExperience").value(12));
	}

	@Test
	void rejectsInvalidProfile() throws Exception {
		String bearer = registerAndLogin("ravi@example.com", "PROVIDER");

		update(bearer, "{\"yearsOfExperience\": 61}").andExpect(status().isBadRequest());
		update(bearer, "{\"yearsOfExperience\": -1}").andExpect(status().isBadRequest());
		update(bearer, "{\"bio\": \"%s\"}".formatted("x".repeat(1001))).andExpect(status().isBadRequest());
	}

	@Test
	void customersCannotUseProviderProfile() throws Exception {
		String bearer = this.testUsers.create("asha@example.com", Role.CUSTOMER).bearer();

		this.mockMvc.perform(get(PROFILE).header(HttpHeaders.AUTHORIZATION, bearer))
			.andExpect(status().isForbidden());
		update(bearer, "{\"bio\": \"hi\"}").andExpect(status().isForbidden());
	}

	private ResultActions update(String bearer, String json) throws Exception {
		return this.mockMvc.perform(
				put(PROFILE).header(HttpHeaders.AUTHORIZATION, bearer).contentType(MediaType.APPLICATION_JSON).content(json));
	}

	/** Goes through the real registration and login endpoints. */
	private String registerAndLogin(String email, String role) throws Exception {
		this.mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
				{"email": "%s", "password": "correct-horse-1", "fullName": "Test User", "role": "%s"}
				""".formatted(email, role))).andExpect(status().isCreated());
		String body = this.mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
				{"email": "%s", "password": "correct-horse-1"}
				""".formatted(email))).andReturn().getResponse().getContentAsString();
		return "Bearer " + JsonPath.read(body, "$.accessToken");
	}

}
