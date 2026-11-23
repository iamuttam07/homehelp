package com.uel.homehelp.provider;

import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/** An eligible provider's working week, for features outside this package. */
public record ProviderWeek(UUID providerId, ZoneId timeZone, List<WeeklyWindow> windows) {
}
