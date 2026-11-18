package com.uel.homehelp.address;

import org.springframework.http.HttpStatus;

import com.uel.homehelp.common.web.ApiException;

/** The user already has the maximum number of addresses. */
class AddressLimitReachedException extends ApiException {

	AddressLimitReachedException(int limit) {
		super(HttpStatus.CONFLICT, "address-limit-reached", "Address limit reached",
				"You can save up to %d addresses. Delete one before adding another.".formatted(limit));
		getBody().setProperty("limit", limit);
	}

}
