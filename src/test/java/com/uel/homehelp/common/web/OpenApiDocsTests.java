package com.uel.homehelp.common.web;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import com.uel.homehelp.TestcontainersConfiguration;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
class OpenApiDocsTests {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void openApiSpecIsPublicAndDescribesTheApi() throws Exception {
		this.mockMvc.perform(get("/v3/api-docs"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.info.title").value("HomeHelp API"))
			.andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
			.andExpect(jsonPath("$.components.securitySchemes.bearerAuth.bearerFormat").value("JWT"))
			.andExpect(jsonPath("$.paths['/api/auth/login'].post.summary").value("Log in"))
			.andExpect(jsonPath("$.paths['/api/auth/register'].post").exists())
			.andExpect(jsonPath("$.paths['/api/users/me/addresses'].post").exists())
			.andExpect(jsonPath("$.paths['/api/provider/profile'].put").exists());
	}

	@Test
	void authEndpointsNeedNoTokenButOthersDo() throws Exception {
		this.mockMvc.perform(get("/v3/api-docs"))
			.andExpect(jsonPath("$.paths['/api/auth/login'].post.security", hasSize(0)))
			.andExpect(jsonPath("$.paths['/api/auth/register'].post.security", hasSize(0)))
			.andExpect(jsonPath("$.paths['/api/users/me'].get.security").doesNotExist())
			.andExpect(jsonPath("$.security[0].bearerAuth").exists());
	}

	@Test
	void swaggerUiIsPublic() throws Exception {
		this.mockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
	}

}
