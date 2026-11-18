package com.uel.homehelp.auth;

import org.springframework.http.HttpStatus;

import com.uel.homehelp.common.web.ApiException;

/** The refresh token is unknown, expired, or has already been used. */
class InvalidRefreshTokenException extends ApiException {

	InvalidRefreshTokenException() {
		super(HttpStatus.UNAUTHORIZED, "invalid-refresh-token", "Invalid refresh token",
				"The refresh token is invalid or has expired. Please log in again.");
	}

}
