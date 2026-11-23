package com.uel.homehelp.provider;

import java.time.DayOfWeek;
import java.time.LocalTime;

/** A weekly working window, in the provider's time zone. */
public record WeeklyWindow(DayOfWeek dayOfWeek, LocalTime start, LocalTime end) {
}
