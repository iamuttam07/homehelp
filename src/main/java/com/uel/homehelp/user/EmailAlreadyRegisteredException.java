package com.uel.homehelp.user;

import java.net.URI;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/**
 * Thrown when someone registers with an email that already has an account.
 * Spring MVC turns it into a 409 problem-details response.
 */
public class EmailAlreadyRegisteredException extends ErrorResponseException {

	public EmailAlreadyRegisteredException(Throwable cause) {
		super(HttpStatus.CONFLICT, problem(), cause);
	}

	private static ProblemDetail problem() {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
				"An account with this email already exists.");
		problem.setTitle("Email already registered");
		problem.setType(URI.create("urn:homehelp:problem:email-already-registered"));
		return problem;
	}

}
