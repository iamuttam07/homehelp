package com.uel.homehelp.booking;

import java.util.EnumSet;
import java.util.Set;

/**
 * Where a booking is in its life. Allowed changes:
 *
 * <pre>
 * CONFIRMED ──start──► IN_PROGRESS ──complete──► COMPLETED
 *     │                    │
 *     └──cancel──► CANCELLED ◄──cancel (admin only)
 * </pre>
 *
 * COMPLETED and CANCELLED are final.
 */
public enum BookingStatus {

	CONFIRMED,

	IN_PROGRESS,

	COMPLETED,

	CANCELLED;

	/** Statuses that occupy the provider's time. */
	static final Set<BookingStatus> ACTIVE = EnumSet.of(CONFIRMED, IN_PROGRESS);

	boolean canMoveTo(BookingStatus next) {
		return switch (this) {
			case CONFIRMED -> next == IN_PROGRESS || next == CANCELLED;
			case IN_PROGRESS -> next == COMPLETED || next == CANCELLED;
			case COMPLETED, CANCELLED -> false;
		};
	}

}
