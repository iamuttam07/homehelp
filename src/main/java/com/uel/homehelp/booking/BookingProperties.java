package com.uel.homehelp.booking;

import java.time.Duration;

import jakarta.validation.constraints.NotNull;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * {@code homehelp.booking.*} settings.
 *
 * @param holdTtl how long a slot hold lasts before it expires on its own
 */
@Validated
@ConfigurationProperties("homehelp.booking")
record BookingProperties(@NotNull Duration holdTtl) {
}
