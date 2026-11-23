package com.uel.homehelp.booking;

import org.springframework.http.HttpStatus;

import com.uel.homehelp.common.web.ApiException;

/** No provider is free for the chosen time any more (or it was never a valid slot). */
class SlotUnavailableException extends ApiException {

	SlotUnavailableException() {
		super(HttpStatus.CONFLICT, "slot-unavailable", "Slot unavailable",
				"This time is no longer available. Please pick another slot.");
	}

}
