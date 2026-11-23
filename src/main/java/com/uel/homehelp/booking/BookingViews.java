package com.uel.homehelp.booking;

import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import com.uel.homehelp.booking.BookingDtos.AddressSummary;
import com.uel.homehelp.booking.BookingDtos.BookingResponse;
import com.uel.homehelp.booking.BookingDtos.Cancellation;
import com.uel.homehelp.booking.BookingDtos.HistoryEntry;
import com.uel.homehelp.booking.BookingDtos.Money;
import com.uel.homehelp.booking.BookingDtos.Party;
import com.uel.homehelp.booking.SlotDtos.ServiceSummary;
import com.uel.homehelp.common.web.PageResponse;
import com.uel.homehelp.user.User;
import com.uel.homehelp.user.UserRepository;

/** Builds booking responses, loading names in one query per page. */
@Component
class BookingViews {

	private final UserRepository users;

	private final BookingEventRepository events;

	BookingViews(UserRepository users, BookingEventRepository events) {
		this.users = users;
		this.events = events;
	}

	BookingResponse detail(Booking booking, ZoneId zone) {
		List<HistoryEntry> history = this.events.findByBookingIdOrderByOccurredAtAscIdAsc(booking.getId())
			.stream()
			.map((e) -> new HistoryEntry(e.getType(), e.getFromStatus(), e.getToStatus(), e.getActorRole(),
					e.getDetails(), e.getOccurredAt()))
			.toList();
		return response(booking, zone, usersFor(List.of(booking)), history);
	}

	PageResponse<BookingResponse> page(Page<Booking> page, ZoneId zone) {
		Map<UUID, User> people = usersFor(page.getContent());
		return PageResponse.from(page, (b) -> response(b, zone, people, null));
	}

	private Map<UUID, User> usersFor(List<Booking> bookings) {
		Set<UUID> ids = new HashSet<>();
		bookings.forEach((b) -> {
			ids.add(b.getCustomerId());
			ids.add(b.getProviderId());
		});
		return this.users.findAllById(ids).stream().collect(Collectors.toMap(User::getId, Function.identity()));
	}

	private static BookingResponse response(Booking b, ZoneId zone, Map<UUID, User> people,
			List<HistoryEntry> history) {
		User customer = people.get(b.getCustomerId());
		User provider = people.get(b.getProviderId());
		Cancellation cancellation = (b.getCancelledAt() != null) ? new Cancellation(b.getCancelledAt(),
				b.getCancelledByRole(), b.getCancellationReason(), Boolean.TRUE.equals(b.getLateCancellation()))
				: null;
		return new BookingResponse(b.getId(), b.getStatus(),
				new ServiceSummary(b.getServiceSlug(), b.getServiceName(), b.getDurationMinutes()),
				new Money(b.getPriceAmount(), b.getCurrency()), b.getStartAt().atZone(zone).toOffsetDateTime(),
				b.getEndAt().atZone(zone).toOffsetDateTime(),
				new AddressSummary(b.getAddressLine1(), b.getAddressLine2(), b.getAddressCity(), b.getAddressPostcode(),
						b.getAddressCountryCode()),
				new Party(b.getCustomerId(), (customer != null) ? customer.getFullName() : null,
						(customer != null) ? customer.getPhone() : null),
				new Party(b.getProviderId(), (provider != null) ? provider.getFullName() : null, null), b.getNotes(),
				b.getRescheduleCount(), cancellation, b.getCreatedAt(), history);
	}

}
