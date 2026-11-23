package com.uel.homehelp.booking;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.uel.homehelp.address.AddressDetails;
import com.uel.homehelp.address.AddressLookup;
import com.uel.homehelp.booking.BookingDtos.CreateBookingRequest;
import com.uel.homehelp.catalog.HomeService;
import com.uel.homehelp.catalog.HomeServiceRepository;
import com.uel.homehelp.common.Strings;
import com.uel.homehelp.common.web.ResourceNotFoundException;
import com.uel.homehelp.provider.ProviderSchedules;
import com.uel.homehelp.provider.ProviderWeek;
import com.uel.homehelp.user.Role;

/**
 * Creating bookings and moving them through their life (see {@link BookingStatus}).
 * Every change locks the booking row and is written to its history.
 *
 * <p>People who aren't on a booking get 404, so booking ids can't be probed.
 */
@Service
class BookingService {

	private static final Sort BY_START = Sort.by(Sort.Order.asc("startAt"), Sort.Order.asc("id"));

	private final BookingRepository bookings;

	private final BookingEventRepository events;

	private final SlotHoldStore holds;

	private final HomeServiceRepository services;

	private final AddressLookup addresses;

	private final ProviderSchedules schedules;

	private final BusyTimes busyTimes;

	private final Clock clock;

	BookingService(BookingRepository bookings, BookingEventRepository events, SlotHoldStore holds,
			HomeServiceRepository services, AddressLookup addresses, ProviderSchedules schedules, BusyTimes busyTimes,
			Clock clock) {
		this.bookings = bookings;
		this.events = events;
		this.holds = holds;
		this.services = services;
		this.addresses = addresses;
		this.schedules = schedules;
		this.busyTimes = busyTimes;
		this.clock = clock;
	}

	// --- creating and reading ---

	/**
	 * Turns the customer's hold into a confirmed booking. The hold is released once the
	 * booking is committed.
	 * @throws SlotUnavailableException if the provider is no longer eligible or got booked
	 */
	@Transactional
	Booking create(UUID customerId, CreateBookingRequest request) {
		SlotHold hold = this.holds.find(request.holdId())
			.filter((h) -> h.userId().equals(customerId))
			.orElseThrow(() -> new ResourceNotFoundException("Slot hold"));
		HomeService service = this.services.findBySlugAndActiveTrueAndCategoryActiveTrue(hold.serviceSlug())
			.filter((s) -> s.getId().equals(hold.serviceId()))
			.orElseThrow(SlotUnavailableException::new);
		AddressDetails address = this.addresses.findDetailsOwnedBy(customerId, hold.addressId())
			.orElseThrow(() -> new ResourceNotFoundException("Address"));
		boolean stillEligible = this.schedules.findEligible(service.getId(), address.latitude(), address.longitude())
			.stream()
			.anyMatch((p) -> p.providerId().equals(hold.providerId()));
		if (!stillEligible) {
			throw new SlotUnavailableException();
		}

		Booking booking = save(new Booking(customerId, hold.providerId(), service, address, hold.start(),
				Strings.trimToNull(request.notes())));
		record(booking, BookingEventType.CREATED, null, customerId, Role.CUSTOMER, null);
		afterCommit(() -> this.holds.release(hold));
		return booking;
	}

	/** A booking the user is on (as customer or provider), or any booking for admins. */
	@Transactional(readOnly = true)
	Booking get(UUID userId, boolean admin, UUID bookingId) {
		return this.bookings.findById(bookingId)
			.filter((b) -> admin || b.getCustomerId().equals(userId) || b.getProviderId().equals(userId))
			.orElseThrow(() -> new ResourceNotFoundException("Booking"));
	}

	@Transactional(readOnly = true)
	Page<Booking> listForCustomer(UUID customerId, BookingStatus status, int page, int size) {
		PageRequest request = PageRequest.of(page, size, BY_START);
		return (status != null) ? this.bookings.findByCustomerIdAndStatus(customerId, status, request)
				: this.bookings.findByCustomerId(customerId, request);
	}

	@Transactional(readOnly = true)
	Page<Booking> listForProvider(UUID providerId, BookingStatus status, int page, int size) {
		PageRequest request = PageRequest.of(page, size, BY_START);
		return (status != null) ? this.bookings.findByProviderIdAndStatus(providerId, status, request)
				: this.bookings.findByProviderId(providerId, request);
	}

	// --- provider actions ---

	@Transactional
	Booking start(UUID providerId, UUID bookingId) {
		Booking booking = locked(bookingId, (b) -> b.getProviderId().equals(providerId));
		BookingStatus from = booking.getStatus();
		booking.start(this.clock.instant());
		record(booking, BookingEventType.STARTED, from, providerId, Role.PROVIDER, null);
		return booking;
	}

	@Transactional
	Booking complete(UUID providerId, UUID bookingId) {
		Booking booking = locked(bookingId, (b) -> b.getProviderId().equals(providerId));
		BookingStatus from = booking.getStatus();
		booking.complete(this.clock.instant());
		record(booking, BookingEventType.COMPLETED, from, providerId, Role.PROVIDER, null);
		return booking;
	}

	// --- cancelling (Step 19) ---

	/** Customers cancel CONFIRMED bookings before they start; within 24 hours it's marked late. */
	@Transactional
	Booking cancelByCustomer(UUID customerId, UUID bookingId, String reason) {
		Booking booking = locked(bookingId, (b) -> b.getCustomerId().equals(customerId));
		requireConfirmedAndNotStarted(booking);
		return cancel(booking, customerId, Role.CUSTOMER, Strings.trimToNull(reason));
	}

	/** Providers cancel CONFIRMED bookings before they start, with a reason. */
	@Transactional
	Booking cancelByProvider(UUID providerId, UUID bookingId, String reason) {
		Booking booking = locked(bookingId, (b) -> b.getProviderId().equals(providerId));
		requireConfirmedAndNotStarted(booking);
		return cancel(booking, providerId, Role.PROVIDER, reason.strip());
	}

	/** Admins can cancel any CONFIRMED or IN_PROGRESS booking, with a reason. */
	@Transactional
	Booking cancelByAdmin(UUID adminId, UUID bookingId, String reason) {
		Booking booking = locked(bookingId, (b) -> true);
		return cancel(booking, adminId, Role.ADMIN, reason.strip());
	}

	// --- rescheduling (Step 19) ---

	/**
	 * Moves a CONFIRMED booking to a new start time, keeping the same provider when they
	 * are free and otherwise picking another eligible one.
	 * @throws BookingRuleException if within 24 hours of the start or rescheduled twice already
	 * @throws SlotUnavailableException if nobody is free at the new time
	 */
	@Transactional
	Booking reschedule(UUID customerId, UUID bookingId, Instant newStart) {
		Booking booking = locked(bookingId, (b) -> b.getCustomerId().equals(customerId));
		if (booking.getStatus() != BookingStatus.CONFIRMED) {
			throw BookingRuleException.invalidTransition(booking.getStatus(), "reschedule");
		}
		Instant now = this.clock.instant();
		if (now.isAfter(booking.getStartAt().minus(BookingPolicy.FREE_CHANGE_CUTOFF))) {
			throw BookingRuleException.tooLateToReschedule(BookingPolicy.FREE_CHANGE_CUTOFF.toHours());
		}
		if (booking.getRescheduleCount() >= BookingPolicy.MAX_RESCHEDULES) {
			throw BookingRuleException.rescheduleLimitReached(BookingPolicy.MAX_RESCHEDULES);
		}
		if (newStart.equals(booking.getStartAt())) {
			throw BookingRuleException.sameTime();
		}
		if (newStart.isAfter(now.plus(Duration.ofDays(SlotService.BOOKING_HORIZON_DAYS + 1)))) {
			throw InvalidSlotRequestException
				.date("Slots can be booked up to %d days ahead.".formatted(SlotService.BOOKING_HORIZON_DAYS));
		}

		Duration duration = Duration.ofMinutes(booking.getDurationMinutes());
		Instant newEnd = newStart.plus(duration);
		UUID currentProvider = booking.getProviderId();
		List<ProviderWeek> providers = this.schedules
			.findEligible(booking.getServiceId(), booking.getLatitude(), booking.getLongitude())
			.stream()
			.filter((p) -> !p.providerId().equals(customerId))
			.sorted(Comparator.comparing((ProviderWeek p) -> !p.providerId().equals(currentProvider)))
			.toList();
		UUID ownHoldId = this.holds.findByUser(customerId).map(SlotHold::holdId).orElse(null);
		Map<UUID, Set<Instant>> busy = this.busyTimes.cells(providers.stream().map(ProviderWeek::providerId).toList(),
				newStart, newEnd, ownHoldId, booking.getId());
		LocalDate utcDate = LocalDate.ofInstant(newStart, ZoneOffset.UTC);
		Instant earliestStart = now.plus(SlotService.MIN_NOTICE);

		for (ProviderWeek provider : providers) {
			boolean working = SlotCalculator.startTimes(provider, utcDate, ZoneOffset.UTC, duration, earliestStart)
				.contains(newStart);
			if (working && SlotHoldStore.isFree(busy.get(provider.providerId()), newStart, newEnd)) {
				Instant oldStart = booking.getStartAt();
				booking.reschedule(provider.providerId(), newStart);
				save(booking);
				String details = "Moved from %s to %s%s".formatted(oldStart, newStart,
						provider.providerId().equals(currentProvider) ? "" : " with another provider");
				record(booking, BookingEventType.RESCHEDULED, BookingStatus.CONFIRMED, customerId, Role.CUSTOMER,
						details);
				return booking;
			}
		}
		throw new SlotUnavailableException();
	}

	// --- helpers ---

	private void requireConfirmedAndNotStarted(Booking booking) {
		if (booking.getStatus() != BookingStatus.CONFIRMED) {
			throw BookingRuleException.invalidTransition(booking.getStatus(), "cancel");
		}
		if (!this.clock.instant().isBefore(booking.getStartAt())) {
			throw BookingRuleException.alreadyStarted();
		}
	}

	private Booking cancel(Booking booking, UUID actorId, Role role, String reason) {
		BookingStatus from = booking.getStatus();
		booking.cancel(actorId, role, reason, this.clock.instant());
		String details = Boolean.TRUE.equals(booking.getLateCancellation()) ? "Late cancellation" : null;
		record(booking, BookingEventType.CANCELLED, from, actorId, role, details);
		return booking;
	}

	/** Locks the booking; anyone the predicate rejects gets 404. */
	private Booking locked(UUID bookingId, Predicate<Booking> visibleTo) {
		return this.bookings.findByIdForUpdate(bookingId)
			.filter(visibleTo)
			.orElseThrow(() -> new ResourceNotFoundException("Booking"));
	}

	/** Saves and flushes so the database's overlap rule is checked right here. */
	private Booking save(Booking booking) {
		try {
			return this.bookings.saveAndFlush(booking);
		}
		catch (DataIntegrityViolationException ex) {
			throw new SlotUnavailableException();
		}
	}

	private void record(Booking booking, BookingEventType type, BookingStatus from, UUID actorId, Role role,
			String details) {
		this.bookings.flush();
		this.events.save(new BookingEvent(booking.getId(), type, from, booking.getStatus(), actorId, role, details,
				this.clock.instant()));
	}

	private static void afterCommit(Runnable action) {
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCommit() {
					action.run();
				}
			});
		}
		else {
			action.run();
		}
	}

}
