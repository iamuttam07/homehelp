package com.uel.homehelp.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.uel.homehelp.user.User;

/**
 * Issues, rotates, and revokes refresh tokens.
 *
 * <p>Refresh tokens are random opaque strings (not JWTs). Each one can be used once:
 * a refresh revokes it and issues a new one. If a revoked token is presented again,
 * it has probably been stolen, so every active token of that user is revoked and they
 * must log in again.
 */
@Service
class RefreshTokenService {

	private static final int TOKEN_BYTES = 32;

	private final SecureRandom random = new SecureRandom();

	private final RefreshTokenRepository tokens;

	private final JwtProperties properties;

	private final Clock clock;

	RefreshTokenService(RefreshTokenRepository tokens, JwtProperties properties, Clock clock) {
		this.tokens = tokens;
		this.properties = properties;
		this.clock = clock;
	}

	/** Creates a new refresh token for the user and returns its raw value. */
	@Transactional
	String issue(User user) {
		byte[] bytes = new byte[TOKEN_BYTES];
		this.random.nextBytes(bytes);
		String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
		Instant now = this.clock.instant();
		this.tokens.save(new RefreshToken(user, hash(raw), now, now.plus(this.properties.refreshTokenTtl())));
		return raw;
	}

	/**
	 * Uses up a refresh token and issues a replacement.
	 * @throws InvalidRefreshTokenException if the token is unknown, expired, or already used
	 */
	@Transactional(noRollbackFor = InvalidRefreshTokenException.class)
	Rotation rotate(String raw) {
		RefreshToken token = this.tokens.findByTokenHashForUpdate(hash(raw))
			.orElseThrow(InvalidRefreshTokenException::new);
		Instant now = this.clock.instant();
		if (token.isRevoked()) {
			// Reuse of an old token: kept committed despite the exception (noRollbackFor).
			this.tokens.revokeAllActiveForUser(token.getUser().getId(), now);
			throw new InvalidRefreshTokenException();
		}
		if (token.isExpired(now)) {
			throw new InvalidRefreshTokenException();
		}
		token.revoke(now);
		User user = token.getUser();
		return new Rotation(user, issue(user));
	}

	/** Revokes the token if it exists. Unknown or already-revoked tokens are ignored. */
	@Transactional
	void revoke(String raw) {
		this.tokens.findByTokenHashForUpdate(hash(raw)).ifPresent((token) -> token.revoke(this.clock.instant()));
	}

	static String hash(String raw) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 is always available", ex);
		}
	}

	record Rotation(User user, String refreshToken) {
	}

}
