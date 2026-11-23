package com.uel.homehelp.common;

import java.time.ZoneId;
import java.util.Optional;

/** Time zone helpers shared across features. */
public final class TimeZones {

	private TimeZones() {
	}

	/**
	 * Parses an IANA region id such as {@code Europe/London}. Fixed offsets such as
	 * {@code +01:00} or {@code GMT+1} are rejected, because they ignore daylight saving.
	 */
	public static Optional<ZoneId> parseRegion(String id) {
		String trimmed = Strings.trimToNull(id);
		if (trimmed == null || !ZoneId.getAvailableZoneIds().contains(trimmed)) {
			return Optional.empty();
		}
		return Optional.of(ZoneId.of(trimmed));
	}

}
