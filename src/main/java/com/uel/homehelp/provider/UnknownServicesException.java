package com.uel.homehelp.provider;

import java.util.List;

import org.springframework.http.HttpStatus;

import com.uel.homehelp.common.web.ApiException;

/** Some requested skills aren't active catalog services. */
class UnknownServicesException extends ApiException {

	UnknownServicesException(List<String> unknownSlugs) {
		super(HttpStatus.BAD_REQUEST, "unknown-services", "Unknown services",
				"These services don't exist or are no longer offered: " + String.join(", ", unknownSlugs) + ".");
		getBody().setProperty("unknownSlugs", unknownSlugs);
	}

}
