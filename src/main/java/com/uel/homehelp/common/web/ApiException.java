package com.uel.homehelp.common.web;

import java.net.URI;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/**
 * Base class for expected API errors. Spring MVC renders them as RFC 9457 problem
 * details, for example:
 *
 * <pre>{@code
 * {"type": "urn:homehelp:problem:address-not-found", "title": "Address not found",
 *  "status": 404, "detail": "Address not found.", "instance": "/api/users/me/addresses/..."}
 * }</pre>
 *
 * The {@code type} is a stable identifier clients can switch on; {@code title} and
 * {@code detail} are for people.
 */
public abstract class ApiException extends ErrorResponseException {

	/** Prefix for all problem {@code type} URIs. */
	public static final String TYPE_PREFIX = "urn:homehelp:problem:";

	protected ApiException(HttpStatus status, String type, String title, String detail) {
		this(status, type, title, detail, null);
	}

	protected ApiException(HttpStatus status, String type, String title, String detail, Throwable cause) {
		super(status, problem(status, type, title, detail), cause);
	}

	/** Builds a problem with HomeHelp's conventions. */
	public static ProblemDetail problem(HttpStatus status, String type, String title, String detail) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
		problem.setTitle(title);
		problem.setType(URI.create(TYPE_PREFIX + type));
		return problem;
	}

}
