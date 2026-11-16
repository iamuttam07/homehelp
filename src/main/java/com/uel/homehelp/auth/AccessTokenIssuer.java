package com.uel.homehelp.auth;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

import com.uel.homehelp.user.User;

/**
 * Creates short-lived, signed access tokens. The {@code sub} claim is the user id.
 */
@Component
public class AccessTokenIssuer {

	private final JwtEncoder encoder;

	private final JwtProperties properties;

	private final Clock clock;

	AccessTokenIssuer(JwtEncoder encoder, JwtProperties properties, Clock clock) {
		this.encoder = encoder;
		this.properties = properties;
		this.clock = clock;
	}

	public String issue(User user) {
		Instant now = this.clock.instant();
		JwtClaimsSet claims = JwtClaimsSet.builder()
			.issuer(this.properties.issuer())
			.subject(user.getId().toString())
			.id(UUID.randomUUID().toString())
			.issuedAt(now)
			.expiresAt(now.plus(this.properties.accessTokenTtl()))
			.claim("email", user.getEmail())
			.claim(SecurityConfig.ROLES_CLAIM, List.of(user.getRole().name()))
			.build();
		JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
		return this.encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
	}

}
