package com.uel.homehelp.booking;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.uel.homehelp.provider.ProviderWeek;
import com.uel.homehelp.provider.WeeklyWindow;

/** Pure unit tests: no Spring, no database. */
class SlotCalculatorTests {

	private static final ZoneId LONDON = ZoneId.of("Europe/London");

	/** A Monday in winter (GMT, offset zero). */
	private static final LocalDate MONDAY = LocalDate.of(2027, 1, 11);

	private static final Duration ONE_HOUR = Duration.ofMinutes(60);

	private static final Instant LONG_AGO = Instant.EPOCH;

	@Test
	void startsEveryHalfHourWhileTheJobFits() {
		assertThat(londonTimes(week(LONDON, window(DayOfWeek.MONDAY, "09:00", "12:00")), MONDAY, ONE_HOUR))
			.containsExactly("09:00", "09:30", "10:00", "10:30", "11:00");
	}

	@Test
	void jobsDoNotSpanTwoWindowsEvenWhenTheyTouch() {
		assertThat(londonTimes(week(LONDON, window(DayOfWeek.MONDAY, "09:00", "10:00"),
				window(DayOfWeek.MONDAY, "10:00", "11:00")), MONDAY, Duration.ofMinutes(90)))
			.isEmpty();
	}

	@Test
	void startsAreOnTheHalfHourEvenIfTheWindowIsNot() {
		assertThat(londonTimes(week(LONDON, window(DayOfWeek.MONDAY, "09:15", "11:15")), MONDAY, ONE_HOUR))
			.containsExactly("09:30", "10:00");
	}

	@Test
	void serviceLongerThanTheWindowHasNoSlots() {
		assertThat(londonTimes(week(LONDON, window(DayOfWeek.MONDAY, "09:00", "10:00")), MONDAY,
				Duration.ofMinutes(120)))
			.isEmpty();
	}

	@Test
	void ignoresOtherDays() {
		assertThat(londonTimes(week(LONDON, window(DayOfWeek.TUESDAY, "09:00", "17:00")), MONDAY, ONE_HOUR)).isEmpty();
	}

	@Test
	void respectsTheEarliestStart() {
		Instant tenAm = MONDAY.atTime(10, 0).atZone(LONDON).toInstant();

		assertThat(SlotCalculator.startTimes(week(LONDON, window(DayOfWeek.MONDAY, "09:00", "12:00")), MONDAY, LONDON,
				ONE_HOUR, tenAm))
			.extracting((i) -> i.atZone(LONDON).toLocalTime().toString())
			.containsExactly("10:00", "10:30", "11:00");
	}

	@Test
	void lateEveningWindowsDoNotWrapPastMidnight() {
		assertThat(londonTimes(week(LONDON, window(DayOfWeek.MONDAY, "22:00", "23:45")), MONDAY, Duration.ofMinutes(30)))
			.containsExactly("22:00", "22:30", "23:00");
	}

	@Test
	void usesRealDurationsAcrossTheSpringClockChange() {
		// London skips 01:00-02:00 on Sunday 28 March 2027, so 00:00-04:00 local is only three real hours.
		LocalDate clockChange = LocalDate.of(2027, 3, 28);

		List<Instant> starts = List.copyOf(SlotCalculator.startTimes(
				week(LONDON, window(DayOfWeek.SUNDAY, "00:00", "04:00")), clockChange, LONDON, ONE_HOUR, LONG_AGO));

		assertThat(starts).containsExactly(Instant.parse("2027-03-28T00:00:00Z"), Instant.parse("2027-03-28T00:30:00Z"),
				Instant.parse("2027-03-28T01:00:00Z"), Instant.parse("2027-03-28T01:30:00Z"),
				Instant.parse("2027-03-28T02:00:00Z"));
		assertThat(starts.get(starts.size() - 1).plus(ONE_HOUR).atZone(LONDON).toLocalTime())
			.isEqualTo(LocalTime.of(4, 0));
	}

	@Test
	void convertsFromTheProvidersTimeZone() {
		// 09:00-11:00 in Paris is 08:00-10:00 in London in winter.
		ProviderWeek paris = week(ZoneId.of("Europe/Paris"), window(DayOfWeek.MONDAY, "09:00", "11:00"));

		assertThat(londonTimes(paris, MONDAY, ONE_HOUR)).containsExactly("08:00", "08:30", "09:00");
	}

	@Test
	void includesWindowsFromTheProvidersNextDay() {
		// Tuesday 07:00-09:00 in Tokyo (UTC+9) is Monday 22:00-24:00 in London in winter.
		ProviderWeek tokyo = week(ZoneId.of("Asia/Tokyo"), window(DayOfWeek.TUESDAY, "07:00", "09:00"));

		assertThat(londonTimes(tokyo, MONDAY, ONE_HOUR)).containsExactly("22:00", "22:30", "23:00");
	}

	// --- helpers ---

	private static List<String> londonTimes(ProviderWeek provider, LocalDate date, Duration duration) {
		return SlotCalculator.startTimes(provider, date, LONDON, duration, LONG_AGO)
			.stream()
			.map((i) -> i.atZone(LONDON).toLocalTime().toString())
			.toList();
	}

	private static ProviderWeek week(ZoneId zone, WeeklyWindow... windows) {
		return new ProviderWeek(UUID.randomUUID(), zone, Arrays.asList(windows));
	}

	private static WeeklyWindow window(DayOfWeek day, String start, String end) {
		return new WeeklyWindow(day, LocalTime.parse(start), LocalTime.parse(end));
	}

}
