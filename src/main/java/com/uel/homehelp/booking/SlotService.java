package com.uel.homehelp.booking;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.uel.homehelp.address.AddressLookup;
import com.uel.homehelp.address.AddressPoint;
import com.uel.homehelp.booking.SlotDtos.ServiceSummary;
import com.uel.homehelp.booking.SlotDtos.Slot;
import com.uel.homehelp.booking.SlotDtos.SlotsResponse;
import com.uel.homehelp.catalog.HomeService;
import com.uel.homehelp.catalog.HomeServiceRepository;
import com.uel.homehelp.common.TimeZones;
import com.uel.homehelp.common.web.ResourceNotFoundException;
import com.uel.homehelp.provider.ProviderSchedules;
import com.uel.homehelp.provider.ProviderWeek;

/**
 * Finds when a service can be booked at one of the customer's addresses.
 *
 * <p>A slot is offered when at least one provider is approved, offers the service,
 * covers the address, is working for the whole job, and isn't held by another customer
 * (the caller's own hold still counts as free) or booked. Customers never see
 * themselves as a provider.
 */
@Service
class SlotService {

	/** Slots must start at least this far in the future. */
	static final Duration MIN_NOTICE = Duration.ofHours(2);

	/** How many days ahead customers can book. */
	static final int BOOKING_HORIZON_DAYS = 30;

	static final String DEFAULT_TIME_ZONE = "Europe/London";

	private final HomeServiceRepository services;

	private final AddressLookup addresses;

	private final ProviderSchedules schedules;

	private final SlotHoldStore holds;

	private final BusyTimes busyTimes;

	private final Clock clock;

	SlotService(HomeServiceRepository services, AddressLookup addresses, ProviderSchedules schedules,
			SlotHoldStore holds, BusyTimes busyTimes, Clock clock) {
		this.services = services;
		this.addresses = addresses;
		this.schedules = schedules;
		this.holds = holds;
		this.busyTimes = busyTimes;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	SlotsResponse find(UUID userId, String serviceSlug, LocalDate date, UUID addressId, String timeZone) {
		ZoneId zone = TimeZones.parseRegion(timeZone).orElseThrow(() -> InvalidSlotRequestException.timeZone(timeZone));
		LocalDate today = LocalDate.now(this.clock.withZone(zone));
		if (date.isBefore(today)) {
			throw InvalidSlotRequestException.date("The date is in the past.");
		}
		if (date.isAfter(today.plusDays(BOOKING_HORIZON_DAYS))) {
			throw InvalidSlotRequestException
				.date("Slots can be booked up to %d days ahead.".formatted(BOOKING_HORIZON_DAYS));
		}
		HomeService service = this.services
			.findBySlugAndActiveTrueAndCategoryActiveTrue(serviceSlug.strip().toLowerCase(Locale.ROOT))
			.orElseThrow(() -> new ResourceNotFoundException("Service"));
		AddressPoint address = this.addresses.findOwnedBy(userId, addressId)
			.orElseThrow(() -> new ResourceNotFoundException("Address"));

		Duration duration = Duration.ofMinutes(service.getDurationMinutes());
		Instant earliestStart = this.clock.instant().plus(MIN_NOTICE);
		List<ProviderWeek> providers = this.schedules
			.findEligible(service.getId(), address.latitude(), address.longitude())
			.stream()
			.filter((p) -> !p.providerId().equals(userId))
			.toList();
		UUID ownHoldId = this.holds.findByUser(userId).map(SlotHold::holdId).orElse(null);
		Instant dayStart = date.atStartOfDay(zone).toInstant();
		Instant lastPossibleEnd = date.plusDays(1).atStartOfDay(zone).toInstant().plus(duration);
		Map<UUID, Set<Instant>> held = this.busyTimes.cells(providers.stream().map(ProviderWeek::providerId).toList(),
				dayStart, lastPossibleEnd, ownHoldId, null);

		Map<Instant, Integer> providersPerStart = new TreeMap<>();
		for (ProviderWeek provider : providers) {
			SlotCalculator.startTimes(provider, date, zone, duration, earliestStart)
				.stream()
				.filter((start) -> SlotHoldStore.isFree(held.get(provider.providerId()), start, start.plus(duration)))
				.forEach((start) -> providersPerStart.merge(start, 1, Integer::sum));
		}

		List<Slot> slots = providersPerStart.entrySet()
			.stream()
			.map((e) -> new Slot(e.getKey().atZone(zone).toOffsetDateTime(),
					e.getKey().plus(duration).atZone(zone).toOffsetDateTime(), e.getValue()))
			.toList();
		return new SlotsResponse(
				new ServiceSummary(service.getSlug(), service.getName(), service.getDurationMinutes()), date,
				zone.getId(), address.id(), slots);
	}

}
