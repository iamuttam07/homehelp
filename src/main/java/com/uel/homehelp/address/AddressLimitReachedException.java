package com.uel.homehelp.address;

import java.net.URI;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/** The user already has the maximum number of addresses. */
class AddressLimitReachedException extends ErrorResponseException {

	AddressLimitReachedException(int limit) {
		super(HttpStatus.CONFLICT, problem(limit), null);
	}

	private static ProblemDetail problem(int limit) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
				"You can save up to %d addresses. Delete one before adding another.".formatted(limit));
		problem.setTitle("Address limit reached");
		problem.setType(URI.create("urn:homehelp:problem:address-limit-reached"));
		problem.setProperty("limit", limit);
		return problem;
	}

}
