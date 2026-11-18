package com.uel.homehelp.auth;

import org.springframework.http.HttpStatus;

import com.uel.homehelp.common.web.ApiException;

/**
 * Wrong email or password. The message never says which, so it can't be used
 * to find out whether an account exists.
 */
class InvalidCredentialsException extends ApiException {

	InvalidCredentialsException() {
		super(HttpStatus.UNAUTHORIZED, "invalid-credentials", "Invalid credentials",
				"The email or password is incorrect.");
	}

}
