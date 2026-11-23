package com.uel.homehelp.booking;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.uel.homehelp.address.AddressLookup;
import com.uel.homehelp.address.AddressPoint;
import com.uel.homehelp.booking.SlotHoldDtos.HoldRequest;
import com.uel.homehelp.catalog.HomeService;
import com.uel.homehelp.catalog.HomeServiceRepository;
import com.uel.homehelp.common.TimeZones;
import com.uel.homehelp.common.web.ResourceNotFoundException;
import com.uel.homehelp.provider.ProviderSchedules;
import com.uel.homehelp.provider.ProviderWeek;

/**
 * Holds a provider for a customer's chosen slot while they finish booking.
 *
 * <p>Each customer has at most one hold: holding a new slot replaces the old one. A hold
 * expires on its own after {@code homehelp.booking.hold-ttl} (10 minutes by default).
 */
@Service
class SlotHoldService {

	private final HomeServiceRepository services;

	private final AddressLookup addresses;

	private final ProviderSchedules schedules;

	private final SlotHoldStore store;

	private final BusyTimes busyTimes;

	private final BookingProperties properties;

	private final Clock clock;

	SlotHoldService(HomeServiceRepository services, AddressLookup addresses, ProviderSchedules schedules,
			SlotHoldStore store, BusyTimes busyTimes, BookingProperties properties, Clock clock) {
		this.services = services;
		this.addresses = addresses;
		this.schedules = schedules;
		this.store = store;
		this.busyTimes = busyTimes;
		this.properties = properties;
		this.clock = clock;
	}

	/**
	 * Holds one free provider for the slot.
	 * @throws SlotUnavailableException if no provider is free for that time
	 */
	@Transactional(readOnly = true)
	SlotHold hold(UUID userId, HoldRequest request) {
		zone(request.timeZone());
		HomeService service = this.services
			.findBySlugAndActiveTrueAndCategoryActiveTrue(request.service().strip().toLowerCase(Locale.ROOT))
			.orElseThrow(() -> new ResourceNotFoundException("Service"));
		AddressPoint address = this.addresses.findOwnedBy(userId, request.addressId())
			.orElseThrow(() -> new ResourceNotFoundException("Address"));

		Instant now = this.clock.instant();
		Instant start = request.start().toInstant();
		if (start.isAfter(now.plus(Duration.ofDays(SlotService.BOOKING_HORIZON_DAYS + 1)))) {
			throw InvalidSlotRequestException
				.date("Slots can be booked up to %d days ahead.".formatted(SlotService.BOOKING_HORIZON_DAYS));
		}
		Duration duration = Duration.ofMinutes(service.getDurationMinutes());
		Instant end = start.plus(duration);
		LocalDate utcDate = LocalDate.ofInstant(start, ZoneOffset.UTC);
		Instant earliestStart = now.plus(SlotService.MIN_NOTICE);

		SlotHold current = this.store.findByUser(userId).orElse(null);
		UUID currentId = (current != null) ? current.holdId() : null;
		List<ProviderWeek> providers = this.schedules
			.findEligible(service.getId(), address.latitude(), address.longitude())
			.stream()
			.filter((p) -> !p.providerId().equals(userId))
			.toList();
		Map<UUID, Set<Instant>> held = this.busyTimes.cells(providers.stream().map(ProviderWeek::providerId).toList(),
				start, end, currentId, null);

		Duration ttl = this.properties.holdTtl();
		for (ProviderWeek provider : providers) {
			boolean working = SlotCalculator.startTimes(provider, utcDate, ZoneOffset.UTC, duration, earliestStart)
				.contains(start);
			if (!working || !SlotHoldStore.isFree(held.get(provider.providerId()), start, end)) {
				continue;
			}
			SlotHold hold = new SlotHold(UUID.randomUUID(), userId, provider.providerId(), service.getId(),
					service.getSlug(), service.getName(), service.getDurationMinutes(), address.id(), start, end,
					now.plus(ttl));
			// Another customer may claim this provider between the check and here; then try the next one.
			if (this.store.claim(hold, ttl, currentId)) {
				if (current != null) {
					this.store.release(current);
				}
				return hold;
			}
		}
		throw new SlotUnavailableException();
	}

	/** @throws ResourceNotFoundException if the customer has no active hold */
	SlotHold current(UUID userId) {
		return this.store.findByUser(userId).orElseThrow(() -> new ResourceNotFoundException("Slot hold"));
	}

	/** Releases the customer's own hold. Other customers' holds look like they don't exist. */
	void release(UUID userId, UUID holdId) {
		SlotHold hold = this.store.find(holdId)
			.filter((h) -> h.userId().equals(userId))
			.orElseThrow(() -> new ResourceNotFoundException("Slot hold"));
		this.store.release(hold);
	}

	static ZoneId zone(String timeZone) {
		String id = (timeZone != null) ? timeZone : SlotService.DEFAULT_TIME_ZONE;
		return TimeZones.parseRegion(id).orElseThrow(() -> InvalidSlotRequestException.timeZone(id));
	}

}
