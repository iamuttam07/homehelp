package com.uel.homehelp.provider;

import org.springframework.http.HttpStatus;

import com.uel.homehelp.common.web.ApiException;

/** The requested onboarding change isn't allowed from the current status. */
class OnboardingConflictException extends ApiException {

	OnboardingConflictException(String action, OnboardingStatus current) {
		super(HttpStatus.CONFLICT, "onboarding-conflict", "Onboarding conflict",
				"Cannot %s while the profile is %s.".formatted(action, current));
		getBody().setProperty("currentStatus", current);
	}

}
