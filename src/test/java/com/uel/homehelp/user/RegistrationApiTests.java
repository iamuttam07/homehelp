package com.uel.homehelp.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.uel.homehelp.TestcontainersConfiguration;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
class RegistrationApiTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserRepository users;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private JdbcTemplate jdbc;

	@BeforeEach
	void clearUsers() {
		this.jdbc.update("DELETE FROM refresh_tokens");
		this.users.deleteAll();
	}

	@Test
	void registersCustomerAndHashesPassword() throws Exception {
		register("""
				{"email": "Asha.Patel@Example.com", "password": "correct-horse-1",
				 "fullName": "Asha Patel", "phone": "+447700900123", "role": "CUSTOMER"}
				""")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.id").isNotEmpty())
			.andExpect(jsonPath("$.email").value("asha.patel@example.com"))
			.andExpect(jsonPath("$.fullName").value("Asha Patel"))
			.andExpect(jsonPath("$.phone").value("+447700900123"))
			.andExpect(jsonPath("$.role").value("CUSTOMER"))
			.andExpect(jsonPath("$.createdAt").isNotEmpty())
			.andExpect(jsonPath("$.password").doesNotExist())
			.andExpect(jsonPath("$.passwordHash").doesNotExist());

		User saved = this.users.findByEmail("asha.patel@example.com").orElseThrow();
		assertThat(saved.getPasswordHash()).startsWith("{bcrypt}").doesNotContain("correct-horse-1");
		assertThat(this.passwordEncoder.matches("correct-horse-1", saved.getPasswordHash())).isTrue();
	}

	@Test
	void registersProviderWithoutPhone() throws Exception {
		register("""
				{"email": "ravi@example.com", "password": "plumbing-pro-9",
				 "fullName": "Ravi Kumar", "role": "PROVIDER"}
				""")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.role").value("PROVIDER"))
			.andExpect(jsonPath("$.phone").doesNotExist());
	}

	@Test
	void rejectsDuplicateEmailIgnoringCase() throws Exception {
		register(customer("sam@example.com")).andExpect(status().isCreated());

		register(customer("SAM@example.com"))
			.andExpect(status().isConflict())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.title").value("Email already registered"));

		assertThat(this.users.count()).isEqualTo(1);
	}

	@Test
	void rejectsInvalidFields() throws Exception {
		register("""
				{"email": "not-an-email", "password": "short",
				 "fullName": " ", "phone": "07700 900123", "role": "CUSTOMER"}
				""")
			.andExpect(status().isBadRequest())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

		assertThat(this.users.count()).isZero();
	}

	@Test
	void rejectsSelfRegistrationAsAdmin() throws Exception {
		register("""
				{"email": "eve@example.com", "password": "let-me-in-please",
				 "fullName": "Eve", "role": "ADMIN"}
				""")
			.andExpect(status().isBadRequest());

		assertThat(this.users.count()).isZero();
	}

	@Test
	void rejectsPasswordLongerThan72Bytes() throws Exception {
		// 40 characters, but 80 bytes in UTF-8
		String password = "é".repeat(40);
		register("""
				{"email": "long@example.com", "password": "%s",
				 "fullName": "Long Password", "role": "CUSTOMER"}
				""".formatted(password))
			.andExpect(status().isBadRequest());

		assertThat(this.users.count()).isZero();
	}

	@Test
	void rejectsMissingBody() throws Exception {
		this.mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON))
			.andExpect(status().isBadRequest());
	}

	private ResultActions register(String json) throws Exception {
		return this.mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(json));
	}

	private static String customer(String email) {
		return """
				{"email": "%s", "password": "correct-horse-1", "fullName": "Sam Lee", "role": "CUSTOMER"}
				""".formatted(email);
	}

}
