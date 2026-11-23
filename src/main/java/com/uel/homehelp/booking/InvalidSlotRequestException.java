package com.uel.homehelp.booking;

import org.springframework.http.HttpStatus;

import com.uel.homehelp.common.web.ApiException;

/** The slot search can't run: bad time zone or a date outside the booking window. */
class InvalidSlotRequestException extends ApiException {

	private InvalidSlotRequestException(String type, String title, String detail) {
		super(HttpStatus.BAD_REQUEST, type, title, detail);
	}

	static InvalidSlotRequestException timeZone(String id) {
		return new InvalidSlotRequestException("invalid-time-zone", "Invalid time zone",
				"'%s' is not a known time zone (use an IANA id such as Europe/London).".formatted(id));
	}

	static InvalidSlotRequestException date(String detail) {
		return new InvalidSlotRequestException("invalid-date", "Invalid date", detail);
	}

}
