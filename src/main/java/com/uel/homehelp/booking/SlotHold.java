package com.uel.homehelp.booking;

import java.time.Instant;
import java.util.UUID;

/**
 * One provider reserved for one customer's chosen slot, for a short time.
 * Stored in Redis as JSON; the provider id is never shown to the customer.
 */
record SlotHold(UUID holdId, UUID userId, UUID providerId, UUID serviceId, String serviceSlug, String serviceName,
		int durationMinutes, UUID addressId, Instant start, Instant end, Instant expiresAt) {
}
