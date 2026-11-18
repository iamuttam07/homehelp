package com.uel.homehelp.user;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import com.uel.homehelp.TestUsers.LoggedInUser;
import com.uel.homehelp.TestcontainersConfiguration;

@SpringBootTest
@Import({ TestcontainersConfiguration.class, TestUsers.class })
@AutoConfigureMockMvc
class CurrentUserApiTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private TestUsers testUsers;

	@Autowired
	private JdbcTemplate jdbc;

	private LoggedInUser asha;

	@BeforeEach
	void createUser() {
		this.jdbc.update("DELETE FROM users");
		this.asha = this.testUsers.create("asha@example.com", Role.CUSTOMER);
	}

	@Test
	void updatesNameAndPhone() throws Exception {
		update("""
				{"fullName": "  Asha P. Patel ", "phone": "+447700900123"}
				""")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.fullName").value("Asha P. Patel"))
			.andExpect(jsonPath("$.phone").value("+447700900123"))
			.andExpect(jsonPath("$.email").value("asha@example.com"))
			.andExpect(jsonPath("$.role").value("CUSTOMER"));

		this.mockMvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, this.asha.bearer()))
			.andExpect(jsonPath("$.fullName").value("Asha P. Patel"));
	}

	@Test
	void omittingPhoneRemovesIt() throws Exception {
		update("{\"fullName\": \"Asha\", \"phone\": \"+447700900123\"}");

		update("{\"fullName\": \"Asha\"}").andExpect(status().isOk()).andExpect(jsonPath("$.phone").doesNotExist());
	}

	@Test
	void cannotChangeEmailOrRoleThisWay() throws Exception {
		update("""
				{"fullName": "Asha", "email": "evil@example.com", "role": "ADMIN"}
				""")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.email").value("asha@example.com"))
			.andExpect(jsonPath("$.role").value("CUSTOMER"));
	}

	@Test
	void rejectsInvalidDetails() throws Exception {
		update("{\"fullName\": \" \"}").andExpect(status().isBadRequest());
		update("{\"fullName\": \"Asha\", \"phone\": \"07700 900123\"}").andExpect(status().isBadRequest());
	}

	private ResultActions update(String json) throws Exception {
		return this.mockMvc.perform(put("/api/users/me").header(HttpHeaders.AUTHORIZATION, this.asha.bearer())
			.contentType(MediaType.APPLICATION_JSON)
			.content(json));
	}

}
