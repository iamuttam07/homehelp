package com.uel.homehelp.provider;

import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import com.uel.homehelp.TestUsers;
import com.uel.homehelp.TestUsers.LoggedInUser;
import com.uel.homehelp.TestcontainersConfiguration;
import com.uel.homehelp.user.Role;

@SpringBootTest
@Import({ TestcontainersConfiguration.class, TestUsers.class })
@AutoConfigureMockMvc
class ProviderOnboardingApiTests {

	private static final String ONBOARDING = "/api/provider/onboarding";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private TestUsers testUsers;

	@Autowired
	private JdbcTemplate jdbc;

	private LoggedInUser provider;

	private UUID providerId;

	@BeforeEach
	void createProvider() {
		this.jdbc.update("DELETE FROM bookings");
		this.jdbc.update("DELETE FROM users");
		this.provider = this.testUsers.create("ravi@example.com", Role.PROVIDER);
		this.providerId = this.provider.user().getId();
	}

	@AfterEach
	void reactivateCatalog() {
		this.jdbc.update("UPDATE services SET active = true");
	}

	@Test
	void newProviderIsOnboardingWithNothingDone() throws Exception {
		onboardingStatus(this.provider)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("ONBOARDING"))
			.andExpect(jsonPath("$.checklist.skills").value(false))
			.andExpect(jsonPath("$.checklist.serviceArea").value(false))
			.andExpect(jsonPath("$.checklist.availability").value(false))
			.andExpect(jsonPath("$.canSubmit").value(false))
			.andExpect(jsonPath("$.submittedAt").doesNotExist());
	}

	@Test
	void checklistFollowsProgress() throws Exception {
		OnboardingFixtures.addSkill(this.jdbc, this.providerId, "leak-repair");
		OnboardingFixtures.setServiceArea(this.jdbc, this.providerId);

		onboardingStatus(this.provider)
			.andExpect(jsonPath("$.checklist.skills").value(true))
			.andExpect(jsonPath("$.checklist.serviceArea").value(true))
			.andExpect(jsonPath("$.checklist.availability").value(false))
			.andExpect(jsonPath("$.canSubmit").value(false));

		OnboardingFixtures.addAvailability(this.jdbc, this.providerId);
		onboardingStatus(this.provider).andExpect(jsonPath("$.canSubmit").value(true));
	}

	@Test
	void cannotSubmitAnIncompleteProfile() throws Exception {
		OnboardingFixtures.setServiceArea(this.jdbc, this.providerId);

		submit(this.provider)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.type").value("urn:homehelp:problem:profile-incomplete"))
			.andExpect(jsonPath("$.missing").value(contains("skills", "availability")));
		onboardingStatus(this.provider).andExpect(jsonPath("$.status").value("ONBOARDING"));
	}

	@Test
	void skillsForDeactivatedServicesDoNotCount() throws Exception {
		OnboardingFixtures.completeProfile(this.jdbc, this.providerId);
		this.jdbc.update("UPDATE services SET active = false WHERE slug = 'leak-repair'");

		submit(this.provider).andExpect(status().isConflict()).andExpect(jsonPath("$.missing").value(contains("skills")));
	}

	@Test
	void submitsACompleteProfileForReview() throws Exception {
		OnboardingFixtures.completeProfile(this.jdbc, this.providerId);

		submit(this.provider)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("PENDING_REVIEW"))
			.andExpect(jsonPath("$.submittedAt").isNotEmpty())
			.andExpect(jsonPath("$.canSubmit").value(false));
	}

	@Test
	void cannotSubmitTwice() throws Exception {
		OnboardingFixtures.completeProfile(this.jdbc, this.providerId);
		submit(this.provider);

		submit(this.provider)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.type").value("urn:homehelp:problem:onboarding-conflict"))
			.andExpect(jsonPath("$.currentStatus").value("PENDING_REVIEW"));
	}

	@Test
	void customersHaveNoOnboarding() throws Exception {
		LoggedInUser customer = this.testUsers.create("asha@example.com", Role.CUSTOMER);

		onboardingStatus(customer).andExpect(status().isForbidden());
		submit(customer).andExpect(status().isForbidden());
	}

	private ResultActions onboardingStatus(LoggedInUser user) throws Exception {
		return this.mockMvc
			.perform(MockMvcRequestBuilders.get(ONBOARDING).header(HttpHeaders.AUTHORIZATION, user.bearer()));
	}

	private ResultActions submit(LoggedInUser user) throws Exception {
		return this.mockMvc.perform(post(ONBOARDING + "/submit").header(HttpHeaders.AUTHORIZATION, user.bearer()));
	}

}
