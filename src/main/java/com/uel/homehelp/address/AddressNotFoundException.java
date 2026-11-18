package com.uel.homehelp.address;

import java.net.URI;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/**
 * The address doesn't exist or belongs to someone else. Both cases look the same,
 * so the API doesn't reveal which ids exist.
 */
class AddressNotFoundException extends ErrorResponseException {

	AddressNotFoundException() {
		super(HttpStatus.NOT_FOUND, problem(), null);
	}

	private static ProblemDetail problem() {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "Address not found.");
		problem.setTitle("Address not found");
		problem.setType(URI.create("urn:homehelp:problem:address-not-found"));
		return problem;
	}

}
