package com.uel.homehelp.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import com.uel.homehelp.TestUsers;
import com.uel.homehelp.TestUsers.LoggedInUser;
import com.uel.homehelp.TestcontainersConfiguration;
import com.uel.homehelp.user.Role;

@SpringBootTest
@Import({ TestcontainersConfiguration.class, TestUsers.class })
@AutoConfigureMockMvc
class ProviderReviewApiTests {

	private static final String ADMIN = "/api/admin/providers";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private TestUsers testUsers;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private ProviderReviewService reviews;

	private LoggedInUser admin;

	private LoggedInUser ravi;

	@BeforeEach
	void createUsers() throws Exception {
		this.jdbc.update("DELETE FROM bookings");
		this.jdbc.update("DELETE FROM users");
		this.admin = this.testUsers.create("admin@example.com", Role.ADMIN);
		this.ravi = submittedProvider("ravi@example.com");
	}

	@Test
	void listsTheReviewQueueOldestFirst() throws Exception {
		LoggedInUser sam = submittedProvider("sam@example.com");
		this.testUsers.create("still-onboarding@example.com", Role.PROVIDER);

		adminGet(ADMIN)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items", hasSize(2)))
			.andExpect(jsonPath("$.items[*].email").value(contains("ravi@example.com", "sam@example.com")))
			.andExpect(jsonPath("$.items[0].status").value("PENDING_REVIEW"))
			.andExpect(jsonPath("$.items[0].fullName").value("Test User"))
			.andExpect(jsonPath("$.items[0].skillCount").value(1))
			.andExpect(jsonPath("$.items[0].submittedAt").isNotEmpty())
			.andExpect(jsonPath("$.totalItems").value(2));

		adminGet(ADMIN + "?status=ONBOARDING")
			.andExpect(jsonPath("$.items[*].email").value(contains("still-onboarding@example.com")));
		assertThat(sam).isNotNull();
	}

	@Test
	void showsTheFullProfileForReview() throws Exception {
		adminGet(ADMIN + "/" + this.ravi.user().getId())
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.email").value("ravi@example.com"))
			.andExpect(jsonPath("$.status").value("PENDING_REVIEW"))
			.andExpect(jsonPath("$.checklist.skills").value(true))
			.andExpect(jsonPath("$.skills[*].slug").value(contains("leak-repair")))
			.andExpect(jsonPath("$.serviceArea.radiusKm").value(5))
			.andExpect(jsonPath("$.availability.timeZone").value("Europe/London"))
			.andExpect(jsonPath("$.availability.windows[0].dayOfWeek").value("MONDAY"));
	}

	@Test
	void approvesAPendingProvider() throws Exception {
		adminPost(ADMIN + "/" + this.ravi.user().getId() + "/approve", null)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("APPROVED"))
			.andExpect(jsonPath("$.reviewedAt").isNotEmpty());

		providerStatus(this.ravi).andExpect(jsonPath("$.status").value("APPROVED"));
		assertThat(this.jdbc.queryForObject("SELECT reviewed_by FROM provider_profiles WHERE user_id = ?", UUID.class,
				this.ravi.user().getId())).isEqualTo(this.admin.user().getId());
	}

	@Test
	void rejectionReasonIsShownToTheProvider() throws Exception {
		adminPost(ADMIN + "/" + this.ravi.user().getId() + "/reject",
				"{\"reason\": \"  Please add a bio and your Gas Safe number.  \"}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("REJECTED"))
			.andExpect(jsonPath("$.rejectionReason").value("Please add a bio and your Gas Safe number."));

		providerStatus(this.ravi)
			.andExpect(jsonPath("$.status").value("REJECTED"))
			.andExpect(jsonPath("$.rejectionReason").value("Please add a bio and your Gas Safe number."))
			.andExpect(jsonPath("$.canSubmit").value(true));
	}

	@Test
	void rejectedProviderCanResubmitAndThenBeApproved() throws Exception {
		String id = this.ravi.user().getId().toString();
		adminPost(ADMIN + "/" + id + "/reject", "{\"reason\": \"Add a bio.\"}");

		providerSubmit(this.ravi)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("PENDING_REVIEW"))
			.andExpect(jsonPath("$.rejectionReason").doesNotExist());

		adminPost(ADMIN + "/" + id + "/approve", null).andExpect(jsonPath("$.status").value("APPROVED"));
		providerSubmit(this.ravi).andExpect(status().isConflict());
	}

	@Test
	void rejectNeedsAReason() throws Exception {
		adminPost(ADMIN + "/" + this.ravi.user().getId() + "/reject", "{\"reason\": \" \"}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].field").value("reason"));
		adminPost(ADMIN + "/" + this.ravi.user().getId() + "/reject", "{}").andExpect(status().isBadRequest());
	}

	@Test
	void onlyPendingProvidersCanBeReviewed() throws Exception {
		LoggedInUser onboarding = this.testUsers.create("new@example.com", Role.PROVIDER);
		String id = this.ravi.user().getId().toString();

		adminPost(ADMIN + "/" + onboarding.user().getId() + "/approve", null)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.currentStatus").value("ONBOARDING"));

		adminPost(ADMIN + "/" + id + "/approve", null).andExpect(status().isOk());
		adminPost(ADMIN + "/" + id + "/approve", null).andExpect(status().isConflict());
		adminPost(ADMIN + "/" + id + "/reject", "{\"reason\": \"Too late.\"}").andExpect(status().isConflict());
	}

	@Test
	void unknownProviderIsNotFound() throws Exception {
		adminGet(ADMIN + "/" + UUID.randomUUID())
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.type").value("urn:homehelp:problem:provider-not-found"));
		adminPost(ADMIN + "/" + UUID.randomUUID() + "/approve", null).andExpect(status().isNotFound());
	}

	@Test
	void rejectsUnknownStatusFilter() throws Exception {
		adminGet(ADMIN + "?status=SLEEPING").andExpect(status().isBadRequest());
	}

	@Test
	void onlyAdminsCanReview() throws Exception {
		LoggedInUser customer = this.testUsers.create("asha@example.com", Role.CUSTOMER);
		String path = ADMIN + "/" + this.ravi.user().getId() + "/approve";

		for (LoggedInUser user : List.of(customer, this.ravi)) {
			this.mockMvc.perform(MockMvcRequestBuilders.get(ADMIN).header(HttpHeaders.AUTHORIZATION, user.bearer()))
				.andExpect(status().isForbidden());
			this.mockMvc.perform(post(path).header(HttpHeaders.AUTHORIZATION, user.bearer()))
				.andExpect(status().isForbidden());
		}
		providerStatus(this.ravi).andExpect(jsonPath("$.status").value("PENDING_REVIEW"));
	}

	@Test
	void twoAdminsApprovingAtOnceOnlyOneSucceeds() throws Exception {
		LoggedInUser secondAdmin = this.testUsers.create("admin2@example.com", Role.ADMIN);
		UUID providerId = this.ravi.user().getId();
		ExecutorService pool = Executors.newFixedThreadPool(2);
		CountDownLatch start = new CountDownLatch(1);
		List<Future<Boolean>> results = new ArrayList<>();
		for (UUID adminId : List.of(this.admin.user().getId(), secondAdmin.user().getId())) {
			results.add(pool.submit(() -> {
				start.await();
				try {
					this.reviews.approve(adminId, providerId);
					return true;
				}
				catch (OnboardingConflictException ex) {
					return false;
				}
			}));
		}
		start.countDown();
		List<Boolean> outcomes = new ArrayList<>();
		for (Future<Boolean> result : results) {
			outcomes.add(result.get(30, TimeUnit.SECONDS));
		}
		pool.shutdown();

		assertThat(outcomes).containsExactlyInAnyOrder(true, false);
	}

	// --- helpers ---

	private LoggedInUser submittedProvider(String email) throws Exception {
		LoggedInUser provider = this.testUsers.create(email, Role.PROVIDER);
		OnboardingFixtures.completeProfile(this.jdbc, provider.user().getId());
		providerSubmit(provider).andExpect(status().isOk());
		return provider;
	}

	private ResultActions providerSubmit(LoggedInUser provider) throws Exception {
		return this.mockMvc
			.perform(post("/api/provider/onboarding/submit").header(HttpHeaders.AUTHORIZATION, provider.bearer()));
	}

	private ResultActions providerStatus(LoggedInUser provider) throws Exception {
		return this.mockMvc.perform(MockMvcRequestBuilders.get("/api/provider/onboarding")
			.header(HttpHeaders.AUTHORIZATION, provider.bearer()));
	}

	private ResultActions adminGet(String path) throws Exception {
		return this.mockMvc.perform(MockMvcRequestBuilders.get(path).header(HttpHeaders.AUTHORIZATION, this.admin.bearer()));
	}

	private ResultActions adminPost(String path, String json) throws Exception {
		var request = post(path).header(HttpHeaders.AUTHORIZATION, this.admin.bearer());
		if (json != null) {
			request = request.contentType(MediaType.APPLICATION_JSON).content(json);
		}
		return this.mockMvc.perform(request);
	}

}
