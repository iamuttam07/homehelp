package com.uel.homehelp.auth;

import java.nio.charset.StandardCharsets;
import java.time.Clock;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Stateless bearer-token security.
 *
 * <ul>
 * <li>Clients send {@code Authorization: Bearer <access token>}; no sessions or cookies,
 * so CSRF protection is not needed.</li>
 * <li>Access tokens are HS256-signed JWTs whose {@code roles} claim becomes
 * {@code ROLE_*} authorities.</li>
 * <li>URL rules below give coarse access by role; {@code @PreAuthorize} is enabled for
 * finer rules on individual methods.</li>
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
@EnableConfigurationProperties(JwtProperties.class)
class SecurityConfig {

	static final String ROLES_CLAIM = "roles";

	private static final Logger logger = LoggerFactory.getLogger(SecurityConfig.class);

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationConverter jwtAuthenticationConverter,
			ProblemDetailsSecurityHandler problemDetails) throws Exception {
		http.csrf(AbstractHttpConfigurer::disable)
			.httpBasic(AbstractHttpConfigurer::disable)
			.formLogin(AbstractHttpConfigurer::disable)
			.logout(AbstractHttpConfigurer::disable)
			.sessionManagement((session) -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.authorizeHttpRequests((requests) -> requests
				.requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
				.requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login", "/api/auth/refresh",
						"/api/auth/logout")
				.permitAll()
				.requestMatchers("/error").permitAll()
				.requestMatchers(HttpMethod.GET, "/api/catalog/**").permitAll()
				.requestMatchers("/v3/api-docs", "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**").permitAll()
				.requestMatchers("/api/admin/**").hasRole("ADMIN")
				.requestMatchers("/api/provider/**").hasRole("PROVIDER")
				.anyRequest().authenticated())
			.oauth2ResourceServer((resourceServer) -> resourceServer
				.jwt((jwt) -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter))
				.authenticationEntryPoint(problemDetails)
				.accessDeniedHandler(problemDetails))
			.exceptionHandling((exceptions) -> exceptions
				.authenticationEntryPoint(problemDetails)
				.accessDeniedHandler(problemDetails));
		return http.build();
	}

	@Bean
	JwtAuthenticationConverter jwtAuthenticationConverter() {
		JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
		authorities.setAuthoritiesClaimName(ROLES_CLAIM);
		authorities.setAuthorityPrefix("ROLE_");
		JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
		converter.setJwtGrantedAuthoritiesConverter(authorities);
		return converter;
	}

	@Bean
	JwtEncoder jwtEncoder(JwtProperties properties) {
		return new NimbusJwtEncoder(new ImmutableSecret<>(signingKey(properties)));
	}

	@Bean
	JwtDecoder jwtDecoder(JwtProperties properties) {
		if (properties.usesDevelopmentSecret()) {
			logger.warn("Using the development JWT secret. Set JWT_SECRET outside local development.");
		}
		NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(signingKey(properties))
			.macAlgorithm(MacAlgorithm.HS256)
			.build();
		// Checks expiry/not-before (with a small clock-skew allowance) and the issuer.
		decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(properties.issuer()));
		return decoder;
	}

	@Bean
	Clock clock() {
		return Clock.systemUTC();
	}

	private static SecretKey signingKey(JwtProperties properties) {
		return new SecretKeySpec(properties.secret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
	}

}
