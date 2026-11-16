package com.uel.homehelp.auth;

import java.net.URI;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/**
 * Wrong email or password. The message never says which, so it can't be used
 * to find out whether an account exists.
 */
class InvalidCredentialsException extends ErrorResponseException {

	InvalidCredentialsException() {
		super(HttpStatus.UNAUTHORIZED, problem(), null);
	}

	private static ProblemDetail problem() {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED,
				"The email or password is incorrect.");
		problem.setTitle("Invalid credentials");
		problem.setType(URI.create("urn:homehelp:problem:invalid-credentials"));
		return problem;
	}

}
