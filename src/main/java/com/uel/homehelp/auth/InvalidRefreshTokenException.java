package com.uel.homehelp.auth;

import java.net.URI;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/** The refresh token is unknown, expired, or has already been used. */
class InvalidRefreshTokenException extends ErrorResponseException {

	InvalidRefreshTokenException() {
		super(HttpStatus.UNAUTHORIZED, problem(), null);
	}

	private static ProblemDetail problem() {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED,
				"The refresh token is invalid or has expired. Please log in again.");
		problem.setTitle("Invalid refresh token");
		problem.setType(URI.create("urn:homehelp:problem:invalid-refresh-token"));
		return problem;
	}

}
