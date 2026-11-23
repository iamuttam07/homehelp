package com.uel.homehelp.provider;

import java.util.List;

import org.springframework.http.HttpStatus;

import com.uel.homehelp.common.web.ApiException;

/** The provider tried to submit before setting skills, service area, and availability. */
class ProfileIncompleteException extends ApiException {

	ProfileIncompleteException(List<String> missing) {
		super(HttpStatus.CONFLICT, "profile-incomplete", "Profile incomplete",
				"Add the following before submitting: " + String.join(", ", missing) + ".");
		getBody().setProperty("missing", missing);
	}

}
