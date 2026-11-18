package com.uel.homehelp.common.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.uel.homehelp.TestUsers;
import com.uel.homehelp.TestcontainersConfiguration;
import com.uel.homehelp.user.Role;

/**
 * Every kind of error comes back as a problem detail with the same shape.
 */
@SpringBootTest
@Import({ TestcontainersConfiguration.class, TestUsers.class, ErrorHandlingTests.FailingEndpoints.class })
@AutoConfigureMockMvc
class ErrorHandlingTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private TestUsers testUsers;

	@Autowired
	private JdbcTemplate jdbc;

	private String bearer;

	@BeforeEach
	void createUser() {
		this.jdbc.update("DELETE FROM users");
		this.bearer = this.testUsers.create("asha@example.com", Role.CUSTOMER).bearer();
	}

	@Test
	void validationErrorsListEveryInvalidField() throws Exception {
		postJson("/api/auth/register", """
				{"email": "not-an-email", "password": "short", "fullName": "", "role": "ADMIN"}
				""")
			.andExpect(status().isBadRequest())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.type").value("urn:homehelp:problem:validation-failed"))
			.andExpect(jsonPath("$.title").value("Validation failed"))
			.andExpect(jsonPath("$.status").value(400))
			.andExpect(jsonPath("$.instance").value("/api/auth/register"))
			.andExpect(jsonPath("$.errors[?(@.field == 'email')].message").value("must be a well-formed email address"))
			.andExpect(jsonPath("$.errors[?(@.field == 'password')]").exists())
			.andExpect(jsonPath("$.errors[?(@.field == 'fullName')]").exists())
			.andExpect(jsonPath("$.errors[?(@.field == 'role')].message").value("must be CUSTOMER or PROVIDER"));
	}

	@Test
	void malformedJsonIsBadRequestWithoutParserDetails() throws Exception {
		postJson("/api/auth/login", "{\"email\": ")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.type").value("urn:homehelp:problem:malformed-request"))
			.andExpect(jsonPath("$.detail").value("The request body is missing or is not valid JSON."));
	}

	@Test
	void invalidPathParameterIsBadRequest() throws Exception {
		authGet("/api/users/me/addresses/not-a-uuid")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.type").value("urn:homehelp:problem:invalid-parameter"))
			.andExpect(jsonPath("$.detail").value("Parameter 'id' has an invalid value."));
	}

	@Test
	void notFoundUsesSharedFormat() throws Exception {
		authGet("/api/users/me/addresses/00000000-0000-0000-0000-000000000000")
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.type").value("urn:homehelp:problem:address-not-found"))
			.andExpect(jsonPath("$.title").value("Address not found"));
	}

	@Test
	void unknownPathIsNotFound() throws Exception {
		authGet("/api/does-not-exist")
			.andExpect(status().isNotFound())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
	}

	@Test
	void unsupportedMethodIsMethodNotAllowed() throws Exception {
		this.mockMvc.perform(delete("/api/users/me").header(HttpHeaders.AUTHORIZATION, this.bearer))
			.andExpect(status().isMethodNotAllowed())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
	}

	@Test
	void missingTokenIsUnauthorizedProblem() throws Exception {
		this.mockMvc.perform(get("/api/users/me"))
			.andExpect(status().isUnauthorized())
			.andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, containsString("Bearer")))
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.type").value("urn:homehelp:problem:unauthorized"))
			.andExpect(jsonPath("$.status").value(401))
			.andExpect(jsonPath("$.instance").value("/api/users/me"));
	}

	@Test
	void invalidTokenIsUnauthorizedProblem() throws Exception {
		this.mockMvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt"))
			.andExpect(status().isUnauthorized())
			.andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, containsString("invalid_token")))
			.andExpect(jsonPath("$.type").value("urn:homehelp:problem:invalid-token"))
			.andExpect(jsonPath("$.title").value("Invalid access token"));
	}

	@Test
	void wrongRoleOnUrlRuleIsForbiddenProblem() throws Exception {
		authGet("/api/provider/profile")
			.andExpect(status().isForbidden())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.type").value("urn:homehelp:problem:forbidden"))
			.andExpect(jsonPath("$.status").value(403));
	}

	@Test
	void wrongRoleOnPreAuthorizeIsForbiddenNotServerError() throws Exception {
		authGet("/api/test/admins-only")
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.type").value("urn:homehelp:problem:forbidden"));
	}

	@Test
	void optimisticLockConflictIsConflict() throws Exception {
		authGet("/api/test/optimistic-lock")
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.type").value("urn:homehelp:problem:concurrent-update"));
	}

	@Test
	void databaseConstraintViolationIsConflict() throws Exception {
		authGet("/api/test/data-integrity")
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.type").value("urn:homehelp:problem:data-conflict"))
			.andExpect(jsonPath("$.detail", not(containsString("uq_secret_constraint"))));
	}

	@Test
	void unexpectedErrorIsGenericWithErrorId() throws Exception {
		authGet("/api/test/boom")
			.andExpect(status().isInternalServerError())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.type").value("urn:homehelp:problem:internal-error"))
			.andExpect(jsonPath("$.errorId").isNotEmpty())
			.andExpect(content().string(not(containsString("database password is hunter2"))))
			.andExpect(content().string(not(containsString("IllegalStateException"))));
	}

	@Test
	void businessErrorKeepsExtraProperties() throws Exception {
		postJson("/api/auth/login", "{\"email\": \"asha@example.com\", \"password\": \"wrong-password\"}")
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.type").value("urn:homehelp:problem:invalid-credentials"))
			.andExpect(jsonPath("$.instance").value("/api/auth/login"));
	}

	@Test
	void validationErrorFieldsAreSortedAndComplete() throws Exception {
		postJson("/api/auth/login", "{}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors", hasSize(2)))
			.andExpect(jsonPath("$.errors[0].field").value("email"))
			.andExpect(jsonPath("$.errors[1].field").value("password"));
	}

	private ResultActions authGet(String path) throws Exception {
		return this.mockMvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, this.bearer));
	}

	private ResultActions postJson(String path, String json) throws Exception {
		return this.mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(json));
	}

	@RestController
	static class FailingEndpoints {

		@GetMapping("/api/test/boom")
		String boom() {
			throw new IllegalStateException("database password is hunter2");
		}

		@GetMapping("/api/test/optimistic-lock")
		String optimisticLock() {
			throw new ObjectOptimisticLockingFailureException(Object.class, "some-id");
		}

		@GetMapping("/api/test/data-integrity")
		String dataIntegrity() {
			throw new DataIntegrityViolationException("duplicate key violates uq_secret_constraint");
		}

		@GetMapping("/api/test/admins-only")
		@PreAuthorize("hasRole('ADMIN')")
		String adminsOnly() {
			return "admin";
		}

	}

}
