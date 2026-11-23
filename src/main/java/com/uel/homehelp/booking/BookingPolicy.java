package com.uel.homehelp.booking;

import java.time.Duration;

/** The timing rules for changing a booking, in one place. */
final class BookingPolicy {

	/**
	 * Rescheduling is allowed until this long before the start. Customer cancellations
	 * after this point are marked late (a fee may apply once payments exist).
	 */
	static final Duration FREE_CHANGE_CUTOFF = Duration.ofHours(24);

	static final int MAX_RESCHEDULES = 2;

	/** How early a provider may start a job. */
	static final Duration START_EARLY_WINDOW = Duration.ofMinutes(30);

	private BookingPolicy() {
	}

}
