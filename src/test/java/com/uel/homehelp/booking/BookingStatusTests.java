package com.uel.homehelp.booking;

import static com.uel.homehelp.booking.BookingStatus.CANCELLED;
import static com.uel.homehelp.booking.BookingStatus.COMPLETED;
import static com.uel.homehelp.booking.BookingStatus.CONFIRMED;
import static com.uel.homehelp.booking.BookingStatus.IN_PROGRESS;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

/** The whole state machine, checked pair by pair. */
class BookingStatusTests {

	private static final Map<BookingStatus, Set<BookingStatus>> ALLOWED = Map.of(
			CONFIRMED, EnumSet.of(IN_PROGRESS, CANCELLED),
			IN_PROGRESS, EnumSet.of(COMPLETED, CANCELLED),
			COMPLETED, EnumSet.noneOf(BookingStatus.class),
			CANCELLED, EnumSet.noneOf(BookingStatus.class));

	@Test
	void allowsExactlyTheDocumentedTransitions() {
		for (BookingStatus from : BookingStatus.values()) {
			for (BookingStatus to : BookingStatus.values()) {
				assertThat(from.canMoveTo(to)).as("%s → %s", from, to).isEqualTo(ALLOWED.get(from).contains(to));
			}
		}
	}

	@Test
	void onlyConfirmedAndInProgressOccupyTheProvider() {
		assertThat(BookingStatus.ACTIVE).containsExactlyInAnyOrder(CONFIRMED, IN_PROGRESS);
	}

}
