package com.uel.homehelp.provider;

import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.uel.homehelp.common.web.ResourceNotFoundException;
import com.uel.homehelp.provider.AvailabilityDtos.AvailabilityRequest;
import com.uel.homehelp.provider.AvailabilityDtos.AvailabilityResponse;
import com.uel.homehelp.provider.AvailabilityDtos.WindowDto;

/**
 * A provider's weekly working hours.
 *
 * <p>Rules: times are on quarter hours, each window ends after it starts on the same day,
 * windows on the same day don't overlap (touching is fine), at most
 * {@link #MAX_WINDOWS_PER_DAY} per day, and the time zone is a valid IANA id.
 */
@Service
class ProviderAvailabilityService {

	static final int MAX_WINDOWS_PER_DAY = 4;

	static final int MAX_WINDOWS_PER_WEEK = MAX_WINDOWS_PER_DAY * 7;

	private final ProviderProfileRepository profiles;

	private final AvailabilityWindowRepository windows;

	ProviderAvailabilityService(ProviderProfileRepository profiles, AvailabilityWindowRepository windows) {
		this.profiles = profiles;
		this.windows = windows;
	}

	@Transactional(readOnly = true)
	AvailabilityResponse get(UUID providerId) {
		ProviderProfile profile = this.profiles.findById(providerId)
			.orElseThrow(() -> new ResourceNotFoundException("Provider profile"));
		return response(profile.getTimeZone(),
				this.windows.findByProviderId(providerId).stream().map(WindowDto::from).toList());
	}

	/**
	 * Replaces the whole week.
	 * @throws InvalidAvailabilityException if any rule is broken; nothing is changed
	 */
	@Transactional
	AvailabilityResponse replace(UUID providerId, AvailabilityRequest request) {
		String zone = validate(request);
		// Lock the profile so two concurrent replacements can't interleave.
		ProviderProfile profile = this.profiles.findByIdForUpdate(providerId)
			.orElseThrow(() -> new ResourceNotFoundException("Provider profile"));
		profile.setTimeZone(zone);
		this.profiles.save(profile);

		this.windows.deleteByProviderId(providerId);
		this.windows.saveAll(request.windows()
			.stream()
			.map((w) -> new AvailabilityWindow(providerId, w.dayOfWeek(), w.start(), w.end()))
			.toList());
		return response(zone, request.windows());
	}

	/** Checks every rule and reports all problems at once. Returns the normalized zone id. */
	private static String validate(AvailabilityRequest request) {
		List<String> problems = new ArrayList<>();
		String zone = null;
		try {
			zone = ZoneId.of(request.timeZone().strip()).getId();
		}
		catch (DateTimeException ex) {
			problems.add("'%s' is not a known time zone (use an IANA id such as Europe/London).".formatted(request.timeZone()));
		}

		Map<DayOfWeek, List<WindowDto>> byDay = new TreeMap<>();
		for (WindowDto w : request.windows()) {
			if (!isQuarterHour(w.start().toSecondOfDay()) || !isQuarterHour(w.end().toSecondOfDay())) {
				problems.add("%s %s: times must be on a quarter hour (:00, :15, :30, :45).".formatted(w.dayOfWeek(), label(w)));
			}
			if (!w.start().isBefore(w.end())) {
				problems.add("%s %s: the end must be after the start, on the same day.".formatted(w.dayOfWeek(), label(w)));
			}
			byDay.computeIfAbsent(w.dayOfWeek(), (d) -> new ArrayList<>()).add(w);
		}

		byDay.forEach((day, list) -> {
			if (list.size() > MAX_WINDOWS_PER_DAY) {
				problems.add("%s: at most %d windows per day.".formatted(day, MAX_WINDOWS_PER_DAY));
			}
			List<WindowDto> sorted = list.stream().sorted(WindowDto.WEEK_ORDER).toList();
			for (int i = 1; i < sorted.size(); i++) {
				WindowDto previous = sorted.get(i - 1);
				WindowDto current = sorted.get(i);
				if (current.start().isBefore(previous.end())) {
					problems.add("%s: %s overlaps %s.".formatted(day, label(current), label(previous)));
				}
			}
		});

		if (!problems.isEmpty()) {
			throw new InvalidAvailabilityException(problems);
		}
		return zone;
	}

	private static boolean isQuarterHour(int secondOfDay) {
		return secondOfDay % (15 * 60) == 0;
	}

	private static String label(WindowDto w) {
		return w.start() + "-" + w.end();
	}

	private static AvailabilityResponse response(String zone, List<WindowDto> windows) {
		return new AvailabilityResponse(zone, windows.stream().sorted(WindowDto.WEEK_ORDER).toList());
	}

}
