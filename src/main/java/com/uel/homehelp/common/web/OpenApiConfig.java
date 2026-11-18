package com.uel.homehelp.common.web;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * API description served at {@code /v3/api-docs} and shown in Swagger UI at
 * {@code /swagger-ui.html}. Every endpoint needs a bearer token unless it is
 * marked with {@code @SecurityRequirements} (no requirements), like the auth endpoints.
 */
@Configuration(proxyBeanMethods = false)
class OpenApiConfig {

	static final String BEARER_AUTH = "bearerAuth";

	@Bean
	OpenAPI homeHelpOpenApi(@Value("${info.app.version:dev}") String version) {
		return new OpenAPI()
			.info(new Info().title("HomeHelp API")
				.version(version)
				.description("""
						Home services marketplace API (learning project).

						**To try protected endpoints:** call `POST /api/auth/login`, copy the \
						`accessToken`, click **Authorize**, and paste it (without "Bearer").

						Errors are returned as [problem details](https://www.rfc-editor.org/rfc/rfc9457) \
						(`application/problem+json`).""")
				.license(new License().name("MIT").url("https://opensource.org/licenses/MIT")))
			.components(new Components().addSecuritySchemes(BEARER_AUTH,
					new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
			.addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH));
	}

}
