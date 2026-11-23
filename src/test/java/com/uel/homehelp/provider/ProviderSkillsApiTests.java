package com.uel.homehelp.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
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

/** Uses the seed catalog from V7. */
@SpringBootTest
@Import({ TestcontainersConfiguration.class, TestUsers.class })
@AutoConfigureMockMvc
class ProviderSkillsApiTests {

	private static final String SKILLS = "/api/provider/skills";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private TestUsers testUsers;

	@Autowired
	private JdbcTemplate jdbc;

	private String provider;

	@BeforeEach
	void createProvider() throws Exception {
		this.jdbc.update("DELETE FROM bookings");
		this.jdbc.update("DELETE FROM users");
		this.provider = registerProvider("ravi@example.com");
	}

	@AfterEach
	void reactivateCatalog() {
		this.jdbc.update("UPDATE services SET active = true");
		this.jdbc.update("UPDATE categories SET active = true");
	}

	@Test
	void newProviderHasNoSkills() throws Exception {
		get(this.provider).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
	}

	@Test
	void setsSkillsAndReturnsThemInCatalogOrder() throws Exception {
		replace(this.provider, "[\"tap-replacement\", \"leak-repair\", \"socket-installation\"]")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[*].slug").value(contains("leak-repair", "tap-replacement", "socket-installation")))
			.andExpect(jsonPath("$[0].name").value("Leak repair"))
			.andExpect(jsonPath("$[0].categorySlug").value("plumbing"))
			.andExpect(jsonPath("$[0].categoryName").value("Plumbing"))
			.andExpect(jsonPath("$[0].basePrice").value(75.0))
			.andExpect(jsonPath("$[0].durationMinutes").value(60));

		get(this.provider).andExpect(jsonPath("$", hasSize(3)));
	}

	@Test
	void replacingRemovesSkillsNotInTheNewList() throws Exception {
		replace(this.provider, "[\"leak-repair\", \"tap-replacement\"]");

		replace(this.provider, "[\"boiler-service\"]").andExpect(jsonPath("$[*].slug").value(contains("boiler-service")));
		get(this.provider).andExpect(jsonPath("$[*].slug").value(contains("boiler-service")));
	}

	@Test
	void emptyListRemovesAllSkills() throws Exception {
		replace(this.provider, "[\"leak-repair\"]");

		replace(this.provider, "[]").andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
		assertThat(this.jdbc.queryForObject("SELECT count(*) FROM provider_services", Integer.class)).isZero();
	}

	@Test
	void ignoresDuplicatesAndCase() throws Exception {
		replace(this.provider, "[\"leak-repair\", \" LEAK-REPAIR \", \"leak-repair\"]")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(1)));
	}

	@Test
	void rejectsUnknownServicesAndKeepsExistingSkills() throws Exception {
		replace(this.provider, "[\"leak-repair\"]");

		replace(this.provider, "[\"boiler-service\", \"time-travel\", \"dog-walking\"]")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.type").value("urn:homehelp:problem:unknown-services"))
			.andExpect(jsonPath("$.unknownSlugs").value(contains("time-travel", "dog-walking")));

		get(this.provider).andExpect(jsonPath("$[*].slug").value(contains("leak-repair")));
	}

	@Test
	void rejectsInactiveServices() throws Exception {
		this.jdbc.update("UPDATE services SET active = false WHERE slug = 'leak-repair'");

		replace(this.provider, "[\"leak-repair\"]")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.unknownSlugs").value(contains("leak-repair")));
	}

	@Test
	void hidesSkillsWhoseServiceWasDeactivated() throws Exception {
		replace(this.provider, "[\"leak-repair\", \"boiler-service\"]");
		this.jdbc.update("UPDATE services SET active = false WHERE slug = 'leak-repair'");

		get(this.provider).andExpect(jsonPath("$[*].slug").value(contains("boiler-service")));
	}

	@Test
	void rejectsMoreThanTwentySkills() throws Exception {
		String slugs = IntStream.rangeClosed(1, 21)
			.mapToObj((i) -> "\"service-" + i + "\"")
			.collect(Collectors.joining(",", "[", "]"));

		replace(this.provider, slugs)
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].field").value("serviceSlugs"));
	}

	@Test
	void rejectsMissingOrBlankSlugs() throws Exception {
		this.mockMvc.perform(put(SKILLS).header(HttpHeaders.AUTHORIZATION, this.provider)
				.contentType(MediaType.APPLICATION_JSON).content("{}"))
			.andExpect(status().isBadRequest());
		replace(this.provider, "[\"leak-repair\", \" \"]").andExpect(status().isBadRequest());
	}

	@Test
	void eachProviderHasTheirOwnSkills() throws Exception {
		String other = registerProvider("sam@example.com");
		replace(this.provider, "[\"leak-repair\"]");
		replace(other, "[\"mens-haircut\"]");

		get(this.provider).andExpect(jsonPath("$[*].slug").value(contains("leak-repair")));
		get(other).andExpect(jsonPath("$[*].slug").value(contains("mens-haircut")));
	}

	@Test
	void customersCannotManageSkills() throws Exception {
		String customer = this.testUsers.create("asha@example.com", Role.CUSTOMER).bearer();

		get(customer).andExpect(status().isForbidden());
		replace(customer, "[\"leak-repair\"]").andExpect(status().isForbidden());
	}

	// --- helpers ---

	private ResultActions get(String bearer) throws Exception {
		return this.mockMvc.perform(
				org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(SKILLS)
					.header(HttpHeaders.AUTHORIZATION, bearer));
	}

	private ResultActions replace(String bearer, String slugsJson) throws Exception {
		return this.mockMvc.perform(put(SKILLS).header(HttpHeaders.AUTHORIZATION, bearer)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"serviceSlugs\": " + slugsJson + "}"));
	}

	/** Registers through the API so the provider profile is created. */
	private String registerProvider(String email) throws Exception {
		this.mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
				{"email": "%s", "password": "correct-horse-1", "fullName": "Test Provider", "role": "PROVIDER"}
				""".formatted(email))).andExpect(status().isCreated());
		String body = this.mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
				{"email": "%s", "password": "correct-horse-1"}
				""".formatted(email))).andReturn().getResponse().getContentAsString();
		return "Bearer " + JsonPath.read(body, "$.accessToken");
	}

}
