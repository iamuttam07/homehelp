package com.uel.homehelp.auth;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * {@code homehelp.security.jwt.*} settings.
 *
 * @param secret HMAC key for signing access tokens; at least 32 bytes
 * @param issuer value of the {@code iss} claim, checked on every request
 * @param accessTokenTtl how long an access token is valid
 * @param refreshTokenTtl how long a refresh token is valid
 */
@Validated
@ConfigurationProperties("homehelp.security.jwt")
public record JwtProperties(
		@NotBlank String secret,
		@NotBlank String issuer,
		@NotNull Duration accessTokenTtl,
		@NotNull Duration refreshTokenTtl) {

	/** HS256 needs a key of at least 256 bits. */
	@AssertTrue(message = "must be at least 32 bytes")
	public boolean isSecretLongEnough() {
		return this.secret != null && this.secret.getBytes(StandardCharsets.UTF_8).length >= 32;
	}

	boolean usesDevelopmentSecret() {
		return this.secret.startsWith("dev-only-");
	}

}
