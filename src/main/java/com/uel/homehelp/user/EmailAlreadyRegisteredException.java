package com.uel.homehelp.user;

import org.springframework.http.HttpStatus;

import com.uel.homehelp.common.web.ApiException;

/** Someone registers with an email that already has an account. */
public class EmailAlreadyRegisteredException extends ApiException {

	public EmailAlreadyRegisteredException(Throwable cause) {
		super(HttpStatus.CONFLICT, "email-already-registered", "Email already registered",
				"An account with this email already exists.", cause);
	}

}
