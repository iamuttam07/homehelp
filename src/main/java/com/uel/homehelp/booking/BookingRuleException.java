package com.uel.homehelp.booking;

import org.springframework.http.HttpStatus;

import com.uel.homehelp.common.web.ApiException;

/** A booking change that the rules don't allow right now. */
class BookingRuleException extends ApiException {

	private BookingRuleException(HttpStatus status, String type, String title, String detail) {
		super(status, type, title, detail);
	}

	static BookingRuleException invalidTransition(BookingStatus current, String action) {
		BookingRuleException ex = new BookingRuleException(HttpStatus.CONFLICT, "invalid-booking-transition",
				"Not allowed in this status", "Cannot %s a booking that is %s.".formatted(action, current));
		ex.getBody().setProperty("currentStatus", current);
		return ex;
	}

	static BookingRuleException alreadyStarted() {
		return new BookingRuleException(HttpStatus.CONFLICT, "booking-already-started", "Booking time has passed",
				"The booking's start time has passed. Please contact support.");
	}

	static BookingRuleException tooEarlyToStart(int minutes) {
		return new BookingRuleException(HttpStatus.CONFLICT, "too-early-to-start", "Too early to start",
				"A job can be started at most %d minutes before its booked time.".formatted(minutes));
	}

	static BookingRuleException tooLateToReschedule(long hours) {
		return new BookingRuleException(HttpStatus.CONFLICT, "too-late-to-reschedule", "Too late to reschedule",
				"Bookings can be rescheduled up to %d hours before they start. You can still cancel.".formatted(hours));
	}

	static BookingRuleException rescheduleLimitReached(int limit) {
		return new BookingRuleException(HttpStatus.CONFLICT, "reschedule-limit-reached", "Reschedule limit reached",
				"A booking can be rescheduled at most %d times. You can still cancel.".formatted(limit));
	}

	static BookingRuleException sameTime() {
		return new BookingRuleException(HttpStatus.BAD_REQUEST, "same-time", "Same time",
				"The new time is the same as the current one.");
	}

}
