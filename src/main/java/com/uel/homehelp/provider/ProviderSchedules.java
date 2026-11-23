package com.uel.homehelp.provider;

import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Finds providers who can do a job at a location, with their working weeks. */
@Component
public class ProviderSchedules {

	private final ProviderProfileRepository profiles;

	private final AvailabilityWindowRepository windows;

	ProviderSchedules(ProviderProfileRepository profiles, AvailabilityWindowRepository windows) {
		this.profiles = profiles;
		this.windows = windows;
	}

	/**
	 * Providers who are APPROVED, offer the service, cover the location with their
	 * service area, and have at least one availability window.
	 */
	@Transactional(readOnly = true)
	public List<ProviderWeek> findEligible(UUID serviceId, double latitude, double longitude) {
		List<UUID> ids = this.profiles.findEligibleProviderIds(serviceId, latitude, longitude,
				ProviderServiceAreaService.MAX_RADIUS_KM * 1000.0);
		if (ids.isEmpty()) {
			return List.of();
		}
		Map<UUID, List<WeeklyWindow>> windowsByProvider = this.windows.findByProviderIdIn(ids)
			.stream()
			.collect(Collectors.groupingBy(AvailabilityWindow::getProviderId, Collectors.mapping(
					(w) -> new WeeklyWindow(w.getDayOfWeek(), w.getStartTime(), w.getEndTime()), Collectors.toList())));
		return this.profiles.findAllById(ids)
			.stream()
			.filter((p) -> windowsByProvider.containsKey(p.getUserId()))
			.map((p) -> new ProviderWeek(p.getUserId(), ZoneId.of(p.getTimeZone()), windowsByProvider.get(p.getUserId())))
			.sorted(Comparator.comparing(ProviderWeek::providerId))
			.toList();
	}

}
