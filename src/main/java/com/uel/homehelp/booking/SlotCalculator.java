package com.uel.homehelp.booking;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

import com.uel.homehelp.provider.ProviderWeek;
import com.uel.homehelp.provider.WeeklyWindow;

/**
 * Works out when one provider could start a job of a given length.
 *
 * <ul>
 * <li>Start times are every {@value #STEP_MINUTES} minutes on the provider's clock
 * (:00 and :30), and the whole job must fit inside a single window.</li>
 * <li>All arithmetic is on real instants, so jobs across a daylight-saving change get
 * their true length.</li>
 * <li>The customer's date is in their own time zone, which may differ from the
 * provider's; windows from the provider's previous and next day are considered too.</li>
 * </ul>
 */
final class SlotCalculator {

	static final int STEP_MINUTES = 30;

	private SlotCalculator() {
	}

	/**
	 * @param provider the provider's time zone and weekly windows
	 * @param date the day the customer asked for, in {@code customerZone}
	 * @param customerZone the customer's time zone
	 * @param duration length of the job
	 * @param earliestStart nothing before this instant (e.g. now plus minimum notice)
	 * @return start instants, sorted and without duplicates
	 */
	static SortedSet<Instant> startTimes(ProviderWeek provider, LocalDate date, ZoneId customerZone, Duration duration,
			Instant earliestStart) {
		Instant dayStart = date.atStartOfDay(customerZone).toInstant();
		Instant dayEnd = date.plusDays(1).atStartOfDay(customerZone).toInstant();
		ZoneId zone = provider.timeZone();
		SortedSet<Instant> starts = new TreeSet<>();
		for (LocalDate providerDate : List.of(date.minusDays(1), date, date.plusDays(1))) {
			for (WeeklyWindow window : provider.windows()) {
				if (window.dayOfWeek() != providerDate.getDayOfWeek()) {
					continue;
				}
				Instant windowEnd = ZonedDateTime.of(providerDate, window.end(), zone).toInstant();
				int endMinute = window.end().toSecondOfDay() / 60;
				// Counting minutes of the day (not LocalTime) so the loop can't wrap past midnight.
				for (int minute = roundUpToStep(window.start().toSecondOfDay() / 60); minute < endMinute;
						minute += STEP_MINUTES) {
					Instant start = ZonedDateTime.of(providerDate, LocalTime.ofSecondOfDay(minute * 60L), zone)
						.toInstant();
					if (start.plus(duration).isAfter(windowEnd)) {
						break;
					}
					if (!start.isBefore(dayStart) && start.isBefore(dayEnd) && !start.isBefore(earliestStart)) {
						starts.add(start);
					}
				}
			}
		}
		return starts;
	}

	private static int roundUpToStep(int minuteOfDay) {
		return ((minuteOfDay + STEP_MINUTES - 1) / STEP_MINUTES) * STEP_MINUTES;
	}

}
