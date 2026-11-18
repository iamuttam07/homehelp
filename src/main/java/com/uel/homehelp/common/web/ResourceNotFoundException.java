package com.uel.homehelp.common.web;

import java.util.Locale;

import org.springframework.http.HttpStatus;

/**
 * Something doesn't exist, or the caller isn't allowed to know it exists.
 * {@code new ResourceNotFoundException("Address")} gives type {@code address-not-found}
 * and title "Address not found".
 */
public class ResourceNotFoundException extends ApiException {

	public ResourceNotFoundException(String resource) {
		super(HttpStatus.NOT_FOUND, resource.toLowerCase(Locale.ROOT).replace(' ', '-') + "-not-found",
				resource + " not found", resource + " not found.");
	}

}
