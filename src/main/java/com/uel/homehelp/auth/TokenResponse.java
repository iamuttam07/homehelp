package com.uel.homehelp.auth;

/**
 * Tokens returned by login and refresh.
 *
 * @param accessToken JWT to send as {@code Authorization: Bearer <token>}
 * @param tokenType always {@code Bearer}
 * @param expiresIn seconds until the access token expires
 * @param refreshToken single-use token for {@code POST /api/auth/refresh}
 * @param refreshExpiresIn seconds until the refresh token expires
 */
record TokenResponse(String accessToken, String tokenType, long expiresIn, String refreshToken,
		long refreshExpiresIn) {
}
