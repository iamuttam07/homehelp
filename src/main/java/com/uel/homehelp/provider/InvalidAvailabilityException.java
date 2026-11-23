package com.uel.homehelp.provider;

import java.util.List;

import org.springframework.http.HttpStatus;

import com.uel.homehelp.common.web.ApiException;

/** The weekly schedule breaks one or more rules (overlaps, off-quarter times, ...). */
class InvalidAvailabilityException extends ApiException {

	InvalidAvailabilityException(List<String> problems) {
		super(HttpStatus.BAD_REQUEST, "invalid-availability", "Invalid availability", String.join(" ", problems));
		getBody().setProperty("problems", problems);
	}

}
