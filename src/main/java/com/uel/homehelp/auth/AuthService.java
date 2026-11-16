package com.uel.homehelp.auth;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.uel.homehelp.user.Emails;
import com.uel.homehelp.user.User;
import com.uel.homehelp.user.UserRepository;

@Service
class AuthService {

	private final UserRepository users;

	private final PasswordEncoder passwordEncoder;

	private final AccessTokenIssuer accessTokens;

	private final RefreshTokenService refreshTokens;

	private final JwtProperties properties;

	/**
	 * Checked when the email is unknown, so a failed login takes about as long
	 * whether or not the account exists.
	 */
	private final String dummyPasswordHash;

	AuthService(UserRepository users, PasswordEncoder passwordEncoder, AccessTokenIssuer accessTokens,
			RefreshTokenService refreshTokens, JwtProperties properties) {
		this.users = users;
		this.passwordEncoder = passwordEncoder;
		this.accessTokens = accessTokens;
		this.refreshTokens = refreshTokens;
		this.properties = properties;
		this.dummyPasswordHash = passwordEncoder.encode("dummy-password-for-timing");
	}

	@Transactional
	TokenResponse login(LoginRequest request) {
		User user = this.users.findByEmail(Emails.normalize(request.email())).orElse(null);
		String hash = (user != null) ? user.getPasswordHash() : this.dummyPasswordHash;
		boolean matches = this.passwordEncoder.matches(request.password(), hash);
		if (user == null || !matches) {
			throw new InvalidCredentialsException();
		}
		return tokensFor(user, this.refreshTokens.issue(user));
	}

	@Transactional(noRollbackFor = InvalidRefreshTokenException.class)
	TokenResponse refresh(String refreshToken) {
		RefreshTokenService.Rotation rotation = this.refreshTokens.rotate(refreshToken);
		return tokensFor(rotation.user(), rotation.refreshToken());
	}

	void logout(String refreshToken) {
		this.refreshTokens.revoke(refreshToken);
	}

	private TokenResponse tokensFor(User user, String refreshToken) {
		return new TokenResponse(this.accessTokens.issue(user), "Bearer",
				this.properties.accessTokenTtl().toSeconds(), refreshToken,
				this.properties.refreshTokenTtl().toSeconds());
	}

}
